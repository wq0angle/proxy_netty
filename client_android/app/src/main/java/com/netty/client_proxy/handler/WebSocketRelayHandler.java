package com.netty.client_proxy.handler;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;
import lombok.extern.slf4j.Slf4j;
import timber.log.Timber;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import com.netty.client_proxy.config.*;
import com.netty.client_proxy.enums.*;
import com.netty.client_proxy.util.*;

@ChannelHandler.Sharable
public class WebSocketRelayHandler extends ChannelDuplexHandler {

    private final WebSocketClientHandshaker handshaker;

    private ChannelPromise handshakeFuture;

    private final Channel inboundChannel;

    private final ChannelFlowEnum channelFlow;

    public WebSocketRelayHandler(WebSocketClientHandshaker handshaker, Channel inboundChannel, ChannelFlowEnum channelFlow) {
        this.handshaker = handshaker;
        this.inboundChannel = inboundChannel;
        this.channelFlow = channelFlow;
    }

    public ChannelFuture handshakeFuture() {
        return handshakeFuture;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        handshakeFuture = ctx.newPromise();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        Timber.e("channelActive, 进行handshake");
        handshaker.handshake(ctx.channel());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
//        log.info("当前通道不再活跃!");
//        if (inboundChannel != null && inboundChannel.isActive()) {
//            inboundChannel.close();
//        }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        Channel ch = ctx.channel();
        if (!handshaker.isHandshakeComplete()) {
            try {
                handshaker.finishHandshake(ch, (FullHttpResponse) msg);
                Timber.d("websocket Handshake 完成!");
                handshakeFuture.setSuccess();
            } catch (WebSocketHandshakeException e) {
                Timber.i("websocket连接失败!");
                handshakeFuture.setFailure(e);
            }
            return;
        }
        /*
          调试状态下需要理清channel的角色关系，在转为透明代理模式下, 存在两个流处理器进行数据交互,主要点在于两个不同的channel(ctx.channel和inboundChannel)
          ctx.channel和inboundChannel分别对应local和future的channel,但随着数据通道流向不同,其对应的角色会转变
          比如 local{ ctx.channel() } -> future{ inboundChannel }, future{ ctx.channel() } -> local{ inboundChannel }
          inboundChannel.writeAndFlush 会触发 local 或 future 的channel.writeAndFlush,也就是调用当前类重写的write方法
          而重写的write方法里的 ctx.channel() 也就是inboundChannel
         */
        if (inboundChannel.isActive()) {
            inboundChannel.writeAndFlush(msg).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
        } else {
            ReferenceCountUtil.release(msg);
            ctx.channel().close();
        }
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (msg instanceof BinaryWebSocketFrame){
            BinaryWebSocketFrame frame = (BinaryWebSocketFrame) msg;
//                Timber.e("Websocket二进制帧转TCP流,frame:%s", frame.content().toString(CharsetUtil.UTF_8));
            ByteBuf buf = frame.content();
            ctx.writeAndFlush(buf);
        }else if (msg instanceof TextWebSocketFrame){
            TextWebSocketFrame frame = (TextWebSocketFrame) msg;
            String text = frame.text();
            if (text.contains(WebSocketUtil.frameHead)) {
//                    Timber.d("Websocket文本帧转TCP流,frame:%s", frame.text());
                text = text.substring(WebSocketUtil.frameHead.length());
                ByteBuf content = Unpooled.buffer();
                content.writeBytes(text.getBytes(StandardCharsets.UTF_8));
                ctx.writeAndFlush(content);
            } else {
                super.write(ctx, msg, promise);
            }
        }else if (msg instanceof ByteBuf){
            ByteBuf buf = (ByteBuf) msg;
//                Timber.d("TCP流转Websocket二进制帧,data:%Timber", buf.toString(CharsetUtil.UTF_8));
            WebSocketFrame frame = new BinaryWebSocketFrame(buf);
            ctx.writeAndFlush(frame, promise);
        }else {
            super.write(ctx, msg, promise);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (cause instanceof IOException && cause.getMessage().contains("Connection reset")) {
            Timber.d("Connection was reset by the peer");
        } else {
            Timber.e(cause,"Error occurred in WebSocketRelayHandler,channelFlow:%s", channelFlow.getMsg());
        }
        // 异常处理
        if (!handshakeFuture.isDone()) {
            handshakeFuture.setFailure(cause);
        }
        ctx.close();
    }

//    private static String bytesToHex(byte[] bytes) {
//        StringBuilder sb = new StringBuilder();
//        for (byte b : bytes) {
//            sb.append(String.format("%02x", b)).append(", ");
//        }
//        return sb.toString();
//    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent e = (IdleStateEvent) evt;
            if (e.state() == IdleState.READER_IDLE) {
                Timber.d("读超时，关闭连接");
                ctx.close();
            } else if (e.state() == IdleState.WRITER_IDLE) {
                Timber.d("写超时，发送心跳");
                ctx.writeAndFlush(new PingWebSocketFrame());
            }
        }
    }
}