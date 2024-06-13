package com.custom.proxy.handler.client;

import com.custom.proxy.handler.FramePackRelayHandler;
import com.custom.proxy.handler.WebSocketRelayHandler;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
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
        log.info("Received http request: {}", request.uri());
        handleConnect(ctx, request);
    }

    private void handleConnect(ChannelHandlerContext ctx, FullHttpRequest request) {
        try {
            URI uri = new URI("ws://" + remoteHost + ":" + remotePort + "/websocket");

            //注意这里的false，因为我们不希望WebSocketRelayHandler处理HTTP响应
            WebSocketClientHandshaker handshaker = WebSocketClientHandshakerFactory
                    .newHandshaker(uri, WebSocketVersion.V13, null, false, new DefaultHttpHeaders());

            WebSocketRelayHandler webSocketRelayHandler = new WebSocketRelayHandler(handshaker, ctx.channel());

            Integer maxContentLength = 1024 * 1024 * 10;
            Bootstrap b = new Bootstrap();
            b.group(ctx.channel().eventLoop())
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            ch.pipeline().addLast(new LoggingHandler(LogLevel.INFO));
                            ch.pipeline().addLast(new HttpClientCodec());
                            ch.pipeline().addLast(new HttpObjectAggregator(maxContentLength));

                            ch.pipeline().addLast(webSocketRelayHandler);
                        }
                    });

            ChannelFuture connectFuture = b.connect(remoteHost, remotePort);
            connectFuture.addListener((ChannelFutureListener) future -> {
                if (future.isSuccess()) {
                    log.info("WebSocket connection established");
                    // 发送WebSocket握手请求
                    webSocketRelayHandler.handshakeFuture().addListener(handshakeFuture -> {
                        if (!handshakeFuture.isSuccess()) {
                            log.error("WebSocket Handshake initiation failed", handshakeFuture.cause());
                            ctx.writeAndFlush(new DefaultFullHttpResponse(
                                    HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR));
                            ctx.close();
                        }else {
                            log.info("send connect to server");

                            // 发送CONNECT请求到代理服务端
                            WebSocketFrame frame = new TextWebSocketFrame(request.uri());
                            future.channel().writeAndFlush(frame);

                            // 立即移除HTTP处理器
//                            removeCheckHttpHandler(future.channel().pipeline(), HttpServerCodec.class);
//                            removeCheckHttpHandler(future.channel().pipeline(), HttpObjectAggregator.class);

//                            removeCheckHttpHandler(ctx.pipeline(), HttpClientCodec.class);
                            removeCheckHttpHandler(ctx.pipeline(), this.getClass());

//                            removeCheckHttpHandler(ctx.pipeline(), HttpObjectAggregator.class);
                            webSocketRelayHandler.setInboundChannel(future.channel());
                            ctx.channel().pipeline().addLast(webSocketRelayHandler);
//                            ctx.channel().pipeline().addLast(new WebSocketRelayHandler(handshaker, future.channel()));
                        }
                    });

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

    private void removeCheckHttpHandler(ChannelPipeline pipeline, Class<? extends ChannelHandler> clazz) {
        if (pipeline.get(clazz) != null){
            log.info("remove check http handler");
            pipeline.remove(clazz);
        }
    }
}