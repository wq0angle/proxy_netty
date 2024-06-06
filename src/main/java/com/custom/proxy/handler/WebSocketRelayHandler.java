package com.custom.proxy.handler;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

@Slf4j
public class WebSocketRelayHandler extends ChannelInboundHandlerAdapter {

    private final WebSocketClientHandshaker handshaker;

    private ChannelPromise handshakeFuture;

    private final Channel inboundChannel;

    public WebSocketRelayHandler(WebSocketClientHandshaker handshaker, Channel inboundChannel) {
        this.handshaker = handshaker;
        this.inboundChannel = inboundChannel;
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
        log.info("channelInactive!");
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

        if (msg instanceof FullHttpResponse response) {
            // 处理HTTP响应
            log.info("Received HTTP response: {}", response.status());
            inboundChannel.writeAndFlush(response.retain());

        } else if (msg instanceof WebSocketFrame frame) {

            if (frame instanceof TextWebSocketFrame textFrame) {
                log.info("Received WebSocket message: {}", textFrame.text());
            }else if (frame instanceof BinaryWebSocketFrame binaryFrame){
                log.info("Received WebSocket message: {}", binaryFrame.content().toString(CharsetUtil.UTF_8));
            }
            else if (frame instanceof PingWebSocketFrame pingFrame) {
                log.info("Received WebSocket ping frame");
                ctx.writeAndFlush(new PongWebSocketFrame(pingFrame.content()));
            }
            else if (frame instanceof PongWebSocketFrame pongFrame) {
                log.info("Received WebSocket pong frame");
            }

        }else {
            if (inboundChannel.isActive()) {
                inboundChannel.writeAndFlush(msg).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
            } else {
                ReferenceCountUtil.release(msg);
                ctx.channel().close();
            }
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (cause instanceof IOException && cause.getMessage().contains("Connection reset")) {
            log.info("Connection was reset by the peer");
        } else {
            log.error("Error occurred in AnalysisWebSocketProxyHandler", cause);
        }
        // 异常处理
        if (!handshakeFuture.isDone()) {
            handshakeFuture.setFailure(cause);
        }
        ctx.close();
    }
}