package com.custom.proxy.handler;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.util.ReferenceCountUtil;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

@Slf4j
public class RelayWebSocketHandler extends ChannelInboundHandlerAdapter {
    private final Channel relayChannel;

    public RelayWebSocketHandler(Channel relayChannel) {
        this.relayChannel = relayChannel;
    }

   @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (relayChannel.isActive()) {
            switch (msg) {
                case FullHttpResponse response -> {
                    log.info("reader message type: FullHttpResponse,context:{}", response);
                    TextWebSocketFrame frame = new TextWebSocketFrame(response.content().retain());
                    relayChannel.writeAndFlush(frame).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
                }


                case ByteBuf buf -> {
                    log.info("reader message type: ByteBuf");
                    relayChannel.writeAndFlush(buf.retain()).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
                }
                case WebSocketFrame webSocketFrame -> {
                    log.info("reader message type: WebSocketFrame");
                    relayChannel.writeAndFlush(webSocketFrame).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
                }
                case null, default -> {
                    log.warn("Unexpected message type: {}", msg.getClass().getName());
                    // 释放资源，避免内存泄漏
                    ReferenceCountUtil.release(msg);
                }
            }
        } else {
            ReferenceCountUtil.release(msg);
        }
    }


    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        if (relayChannel.isActive()) {
            relayChannel.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (cause instanceof IOException && cause.getMessage().contains("Connection reset")) {
            log.info("Connection was reset by the peer");
        } else {
            log.error("Error occurred in RelayWebSocketHandler", cause);
        }
        ctx.close();
    }
}
