package com.custom.proxy.handler;

import com.custom.proxy.util.WebSocketUtil;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.Map;

@Slf4j
public class RelayWebSocketHandler extends ChannelDuplexHandler {
    private final Channel relayChannel;

    public RelayWebSocketHandler(Channel relayChannel) {
        this.relayChannel = relayChannel;
    }

   @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
       if (relayChannel.isActive()) {
           if (msg instanceof WebSocketFrame frame) {
               // 处理从客户端接收的WebSocket帧
               if (frame instanceof BinaryWebSocketFrame binaryFrame) {
                   log.info("reader message frame: {}", frame.content().toString(CharsetUtil.UTF_8));
                   // 提取二进制数据
//                   ByteBuf data = binaryFrame.content();
                   ByteBuf data = Unpooled.buffer().writeBytes("serverTest".getBytes());
                   // 直接将二进制数据写入目标服务器
                   relayChannel.writeAndFlush(data.retain());
               } else if (frame instanceof TextWebSocketFrame textFrame) {
                   // 提取文本数据
                   String text = textFrame.text();
                   ByteBuf data = ctx.alloc().buffer();
                   data.writeCharSequence(text, CharsetUtil.UTF_8);
                   // 将文本数据转换为ByteBuf后写入目标服务器
                   relayChannel.writeAndFlush(data.retain());
               } else {
                   // 处理其他类型的WebSocket帧
                   log.info("reader message type: {}", frame.getClass().getSimpleName());
               }
           } else {
               // 传递非WebSocket帧消息
               log.info("reader message type: {}", msg.getClass().getSimpleName());
           }
       }
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (ctx.channel().isActive()) {
            switch (msg) {
                case FullHttpResponse response -> {
                    log.info("reader message type: FullHttpResponse,context:{}", response);
                    // 将HTTP响应转换为WebSocket帧
//                    WebSocketFrame frame = WebSocketUtil.convertToWebSocketFrame(response);
//                    ctx.writeAndFlush(frame).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
                }
                case ByteBuf buf -> {
                    log.info("reader message ByteBuf:{}",buf.toString(CharsetUtil.UTF_8));
                    // 将TCP流数据封装成WebSocket帧
                    WebSocketFrame frame = new BinaryWebSocketFrame(buf);
                    ctx.writeAndFlush(frame).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
                }
                case WebSocketFrame webSocketFrame -> {
                    log.info("reader message type: WebSocketFrame");
                    ctx.writeAndFlush(webSocketFrame).addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
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
