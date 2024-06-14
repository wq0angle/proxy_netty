package com.custom.proxy.handler;

import com.custom.proxy.util.WebSocketUtil;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Slf4j
public class FramePackRelayHandler extends ChannelDuplexHandler {
    private final Channel relayChannel;

    public FramePackRelayHandler(Channel relayChannel, Integer addCnt) {
        this.relayChannel = relayChannel;
        this.addCnt = addCnt;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (relayChannel.isActive()) {
            relayChannel.writeAndFlush(msg).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
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
                log.info("Websocket文本帧,frame:{}", frame.text());
                super.write(ctx, msg, promise);
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
            log.error("Error occurred in FramePackRelayHandler", cause);
        }
        ctx.close();
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        log.info("Websocket帧转TCP流的通道建立");
    }
}
