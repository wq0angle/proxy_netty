package com.custom.proxy.handler.client;

import com.custom.proxy.handler.WebSocketRelayHandler;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketClientCompressionHandler;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.codec.http.websocketx.WebSocketClientProtocolHandler;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URI;

@Slf4j
public class FillWebSocketProxyHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private final String remoteHost;
    private final int remotePort;
    private final SslContext sslContext;

    public FillWebSocketProxyHandler(String remoteHost, int remotePort) throws Exception {
        this.remoteHost = remoteHost;
        this.remotePort = remotePort;
        this.sslContext = SslContextBuilder.forClient()
                .protocols("TLSv1.1", "TLSv1.2", "TLSv1.3")
                .ciphers(null)  // 默认使用所有可用的加密套件
                .build();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
        if (request.method() == HttpMethod.CONNECT) {
            log.info("Received CONNECT request: {}", request.uri());
            handleConnect(ctx, request);
        } else {
            // 直接转发其他请求
            ctx.fireChannelRead(request.retain());
        }
    }

    private void handleConnect(ChannelHandlerContext ctx, FullHttpRequest request) {
        try {
            URI uri = new URI("ws://" + remoteHost + ":" + remotePort);
            WebSocketClientHandshaker handshake = WebSocketClientHandshakerFactory.newHandshaker(
                    uri, WebSocketVersion.V13, null, true, new DefaultHttpHeaders());

            FullHttpRequest forwardRequest = new DefaultFullHttpRequest(
                    request.protocolVersion(), request.method(), request.uri());

            //复制头部
            forwardRequest.headers().set(request.headers());
            forwardRequest.content().writeBytes(request.content()); // 添加请求体

            Integer maxContentLength = 1024 * 1024 * 10;
            Bootstrap b = new Bootstrap();
            b.group(ctx.channel().eventLoop())
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
//                            ch.pipeline().addLast(sslContext.newHandler(ch.alloc(), remoteHost, remotePort));
                            ch.pipeline().addLast(new HttpClientCodec());
                            ch.pipeline().addLast(new HttpObjectAggregator(65536));
//                            ch.pipeline().addLast(new WebSocketClientProtocolHandler(handshake));
                            ch.pipeline().addLast(new WebSocketRelayHandler(ctx.channel(), handshake));
                        }
                    });

            ChannelFuture connectFuture = b.connect(remoteHost, remotePort);
            connectFuture.addListener((ChannelFutureListener) future -> {
                if (future.isSuccess()) {
                    //发送修改的请求
                    future.channel().writeAndFlush(forwardRequest);
                    //释放临时添加转发的http解析器
                    future.channel().pipeline().remove(WebSocketRelayHandler.class);
//                    future.channel().pipeline().remove(HttpClientCodec.class);
//                    future.channel().pipeline().remove(HttpObjectAggregator.class);
//                    future.channel().pipeline().addLast(new WebSocketClientProtocolHandler(handshake));
                    future.channel().pipeline().addLast(new WebSocketRelayHandler(future.channel(), handshake));
                    //释放该请求的全局监听的http解析器,透明转发请求,在connect后面的请求彻底转换为SSL隧道的TCP通信模式
//                    ctx.pipeline().remove(HttpServerCodec.class);
//                    ctx.pipeline().remove(HttpObjectAggregator.class);
//                    ctx.pipeline().remove(this.getClass());  // 移除当前处理器
//                    ctx.pipeline().addLast(new WebSocketClientProtocolHandler(handshake));
                    ctx.pipeline().addLast(new WebSocketRelayHandler(future.channel(), handshake));
                    log.info("send connect request to post");
                } else {
                    log.error("WebSocket connection failed", future.cause());
                    ctx.writeAndFlush(new DefaultFullHttpResponse(
                            HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR));
                    ctx.close();
                }
            });
        } catch (Exception e) {
            log.error("WebSocket connection error", e);
            ctx.writeAndFlush(new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR));
            ctx.close();
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
}