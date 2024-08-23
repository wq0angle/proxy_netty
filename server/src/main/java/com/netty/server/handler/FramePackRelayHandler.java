package com.netty.server.handler;

import com.netty.common.enums.ChannelFlowEnum;
import com.netty.common.util.WebSocketUtil;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrameEncoder;
import io.netty.util.ReferenceCountUtil;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

@Slf4j
public class FramePackRelayHandler extends ChannelDuplexHandler {
    private final Channel inboundChannel;

    private final ChannelFlowEnum channelFlowEnum;

    public FramePackRelayHandler(Channel inboundChannel, ChannelFlowEnum channelFlowEnum) {
        this.inboundChannel = inboundChannel;
        this.channelFlowEnum = channelFlowEnum;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (inboundChannel.isActive()) {
            /*
              调试状态下需要理清channel的角色关系，在转为透明代理模式下, 存在两个流处理器进行数据交互,主要点在于两个不同的channel(ctx.channel和inboundChannel)
              ctx.channel和inboundChannel分别对应local和future的channel,但随着数据通道流向不同,其对应的角色会转变
              比如 local{ ctx.channel() } -> future{ inboundChannel }, future{ ctx.channel() } -> local{ inboundChannel }
              inboundChannel.writeAndFlush 会触发 local 或 future 的channel.writeAndFlush,也就是调用当前类重写的write方法
              而重写的write方法里的 ctx.channel() 也就是inboundChannel
            */
            inboundChannel.writeAndFlush(msg).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
        } else {
            ReferenceCountUtil.release(msg);
            ctx.channel().close();
        }
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        switch (msg) {
            case BinaryWebSocketFrame frame -> {
//                log.debug("Websocket二进制帧转TCP流", frame.content().toString(CharsetUtil.UTF_8));
                ByteBuf buf = frame.content();
                ctx.writeAndFlush(buf);
            }
            case TextWebSocketFrame frame -> {
//                log.debug("Websocket文本帧,frame:{}", frame.text());
                super.write(ctx, msg, promise);
            }
            case ByteBuf data -> {
//                log.debug("TCP流转Websocket二进制帧,data:{}", data.toString(CharsetUtil.UTF_8));
                WebSocketFrame frame = new BinaryWebSocketFrame(data);
                ctx.writeAndFlush(frame, promise);
            }
            case FullHttpRequest request -> {
//                log.debug("TCP流转Websocket文本帧,request:{}", request.uri());
                super.write(ctx, request, promise);
            }
            case FullHttpResponse response -> {
//                log.debug("TCP流转Websocket文本帧,response:{}", response);
                WebSocketFrame frame = WebSocketUtil.convertToTextWebSocketFrame(response);

                ctx.writeAndFlush(frame, promise);
            }
            case null, default -> super.write(ctx, msg, promise);
        }
    }
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (cause instanceof IOException && cause.getMessage().contains("Connection reset")) {
            log.debug("Connection was reset by the peer");
        } else {
            log.error("Error occurred in FramePackRelayHandler,channelFlow:{}", channelFlowEnum.getMsg(), cause);
        }
        ctx.close();
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        log.debug("Websocket帧互转TCP流的通道建立");
    }
}
