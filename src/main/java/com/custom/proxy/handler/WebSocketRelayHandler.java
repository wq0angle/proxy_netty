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
public class WebSocketRelayHandler extends ChannelDuplexHandler  {

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

            switch (frame) {
                case TextWebSocketFrame textFrame -> {
                    log.info("Received WebSocket message: {}", textFrame.text());
                    String text = textFrame.text();
                    handleTextFrame(text, ctx);
                }
                case BinaryWebSocketFrame binaryFrame -> {
                    log.info("Received WebSocket message: {}", binaryFrame.content().toString(CharsetUtil.UTF_8));
                    // 处理二进制帧
                    ByteBuf content = binaryFrame.content();

                    byte[] contentArray = new byte[content.readableBytes()];
                    content.readBytes(contentArray);
                    log.info("content: {}", bytesToHex(contentArray));

                    handleBinaryFrame(content, ctx);
                }
                case PingWebSocketFrame pingFrame -> {
                    log.info("Received WebSocket ping frame");
                    ctx.writeAndFlush(new PongWebSocketFrame(pingFrame.content()));
                }
                case PongWebSocketFrame pongFrame -> log.info("Received WebSocket pong frame");
                case CloseWebSocketFrame closeFrame -> {
                    log.info("Received WebSocket close frame");
                    ctx.close();
                }
                default -> {
                }
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
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (msg instanceof ByteBuf data) {
            // 将TCP流数据封装成WebSocket帧
            WebSocketFrame frame = new BinaryWebSocketFrame(data);
            ctx.write(frame, promise);
        } else {
            // 传递非ByteBuf消息
            super.write(ctx, msg, promise); // 调用父类的write方法来处理非ByteBuf消息
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

    private void handleBinaryFrame(ByteBuf content, ChannelHandlerContext ctx) {
        // 这里将二进制数据转换为TCP流格式
        // 示例：直接将二进制数据写入到应用层的SocketChannel
        if (inboundChannel.isActive()) {
            inboundChannel.writeAndFlush(content.retain());
        }
    }

    private void handleTextFrame(String text, ChannelHandlerContext ctx) {
        // 处理文本数据，转换为TCP流格式
        // 示例：将文本数据转换为ByteBuf后写入到应用层的SocketChannel
        ByteBuf buffer = Unpooled.wrappedBuffer(text.getBytes(StandardCharsets.UTF_8));
        if (inboundChannel.isActive()) {
            inboundChannel.writeAndFlush(buffer);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b)).append(", ");
        }
        return sb.toString();
    }
}