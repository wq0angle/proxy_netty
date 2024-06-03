package com.custom.proxy.handler;

import io.netty.channel.*;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.util.ReferenceCountUtil;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

@Slf4j
public class WebSocketRelayHandler extends SimpleChannelInboundHandler<WebSocketFrame> {

    private final Channel inboundChannel;

    public WebSocketRelayHandler(Channel inboundChannel) {
        this.inboundChannel = inboundChannel;
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, WebSocketFrame frame) throws Exception {
        if (frame instanceof TextWebSocketFrame || frame instanceof BinaryWebSocketFrame) {
            inboundChannel.writeAndFlush(frame.retain());
        } else if (frame instanceof PongWebSocketFrame) {
            log.debug("WebSocket Client received pong");
        } else if (frame instanceof CloseWebSocketFrame) {
            log.info("WebSocket Client received closing");
            ctx.channel().close();
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (cause instanceof IOException && cause.getMessage().contains("Connection reset")) {
            log.info("Connection was reset by the peer");
        } else {
            log.error("Error occurred in FillProxyHandler", cause);
        }
        ctx.close();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        log.info("WebSocket Client disconnected!");
        inboundChannel.close();
    }

}