package com.custom.proxy.handler;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
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
            if (msg instanceof FullHttpResponse response) {
                // 将HTTP响应内容转发为WebSocket文本帧
                TextWebSocketFrame frame = new TextWebSocketFrame(response.content().retain());
                relayChannel.writeAndFlush(frame).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
            } else if (msg instanceof ByteBuf buf) {
                // 如果msg是ByteBuf类型，直接写入到relayChannel中
                relayChannel.writeAndFlush((buf).retain()).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
            } else {
                // 处理其他可能的消息类型
                relayChannel.writeAndFlush(msg).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
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
