package com.custom.proxy.handler;

import com.custom.proxy.util.WebSocketUtil;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

@Slf4j
@ChannelHandler.Sharable
public class WebSocketRelayHandler extends ChannelDuplexHandler  {

    private final WebSocketClientHandshaker handshaker;

    private ChannelPromise handshakeFuture;

    @Setter
    private Channel inboundChannel;

    public WebSocketRelayHandler(WebSocketClientHandshaker handshaker, Channel inboundChannel, Integer addCnt) {
        this.handshaker = handshaker;
        this.inboundChannel = inboundChannel;
        this.addCnt = addCnt;
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
        log.info("channelActive, 进行handshake");
        handshaker.handshake(ctx.channel());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        log.info("当前通道不再活跃!");
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        Channel ch = ctx.channel();
        if (!handshaker.isHandshakeComplete()) {
            try {
                handshaker.finishHandshake(ch, (FullHttpResponse) msg);
                log.info("websocket Handshake 完成!");
                handshakeFuture.setSuccess();
            } catch (WebSocketHandshakeException e) {
                log.info("websocket连接失败!");
                handshakeFuture.setFailure(e);
            }
            return;
        }
        if (inboundChannel.isActive()) {
            inboundChannel.writeAndFlush(msg).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
        } else {
            ReferenceCountUtil.release(msg);
            ctx.channel().close();
        }
    }

    private final Integer addCnt;
    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        switch (msg) {
            case BinaryWebSocketFrame frame -> {
                log.info("Websocket二进制帧转TCP流,frame:{}", frame.content().toString(CharsetUtil.UTF_8));
                ByteBuf buf = frame.content();
                ctx.writeAndFlush(buf);
            }
            case TextWebSocketFrame frame -> {
                String text = frame.text();
                if (text.contains(WebSocketUtil.frameHead)) {
//                    log.info("Websocket文本帧转TCP流,frame:{}", frame.text());
                    text = text.substring(WebSocketUtil.frameHead.length());
                    ByteBuf content = Unpooled.buffer();
                    content.writeBytes(text.getBytes(StandardCharsets.UTF_8));
                    ctx.writeAndFlush(content);
                } else {
//                    super.write(ctx, msg, promise);
                    ctx.writeAndFlush(frame);
                }
            }
            case ByteBuf data -> {
                log.info("TCP流转Websocket二进制帧,data:{}", data.toString(CharsetUtil.UTF_8));
                WebSocketFrame frame = new BinaryWebSocketFrame(data);
                ctx.writeAndFlush(frame, promise);
            }
            case null, default -> super.write(ctx, msg, promise);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (cause instanceof IOException && cause.getMessage().contains("Connection reset")) {
            log.info("Connection was reset by the peer");
        } else {
            log.error("Error occurred in WebSocketRelayHandler", cause);
        }
        // 异常处理
        if (!handshakeFuture.isDone()) {
            handshakeFuture.setFailure(cause);
        }
        ctx.close();
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b)).append(", ");
        }
        return sb.toString();
    }
}