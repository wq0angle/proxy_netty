package com.custom.proxy.handler;

import io.netty.channel.*;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.websocketx.*;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class WebSocketRelayHandler extends SimpleChannelInboundHandler<WebSocketFrame> {

    private final Channel inboundChannel;
    private final WebSocketClientHandshaker handshake;
    private ChannelPromise handshakeFuture;

    public WebSocketRelayHandler(Channel inboundChannel, WebSocketClientHandshaker handshake) {
        this.inboundChannel = inboundChannel;
        this.handshake = handshake;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        handshakeFuture = ctx.newPromise();
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        handshake.handshake(ctx.channel());
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        log.info("WebSocket Client disconnected!");
        inboundChannel.close();
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, WebSocketFrame frame) throws Exception {
        if (!handshake.isHandshakeComplete()) {
            handshake.finishHandshake(ctx.channel(), (FullHttpResponse) frame);
            handshakeFuture.setSuccess();
            return;
        }

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
        log.error("WebSocket Client error", cause);
        if (!handshakeFuture.isDone()) {
            handshakeFuture.setFailure(cause);
        }
        ctx.close();
    }
}