package com.netty.custom.handler.client;

import com.netty.custom.handler.WebSocketRelayHandler;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.TimeUnit;

@Slf4j
public class FillWebSocketProxyHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private String remoteHost;
    private int remotePort;
    private SslContext sslContext;
    private Channel websocketChannel; // WebSocket连接的通道

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
        if (websocketChannel == null || !websocketChannel.isActive()) {
            log.info("首次进行websocket握手，加载websocket通道");
            handleConnect(ctx, request);
        } else {
            log.info("发送数据到服务器");
            sendRequestOverWebSocket(ctx, request);
        }

    }

    WebSocketClientHandshaker handshaker;
    private void handleConnect(ChannelHandlerContext ctx, FullHttpRequest request) throws URISyntaxException {
        URI uri = new URI("wss://" + remoteHost + ":" + remotePort + "/websocket");
        handshaker = WebSocketClientHandshakerFactory
                .newHandshaker(uri, WebSocketVersion.V13, null, false, new DefaultHttpHeaders());
        WebSocketRelayHandler webSocketRelayHandler = new WebSocketRelayHandler(handshaker, ctx.channel(), 1);

        Bootstrap b = new Bootstrap();
        b.group(ctx.channel().eventLoop())
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ch.pipeline().addLast(sslContext.newHandler(ch.alloc(), remoteHost, remotePort));
                        ch.pipeline().addLast(new HttpClientCodec());
                        ch.pipeline().addLast(new HttpObjectAggregator(1024 * 1024 * 10));

                        // 读超时60秒，写超时30秒
                        ch.pipeline().addLast(new IdleStateHandler(60, 30, 0, TimeUnit.SECONDS));

                        ch.pipeline().addLast(webSocketRelayHandler);
                    }
                });

        b.connect(remoteHost, remotePort).addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                websocketChannel = future.channel();
                webSocketRelayHandler.handshakeFuture().addListener((ChannelFutureListener) handshakeFuture -> {
                    if (handshakeFuture.isSuccess()) {
                        sendRequestOverWebSocket(ctx, request);
                    } else {
                        log.error("WebSocket握手失败");
                        ctx.writeAndFlush(new DefaultFullHttpResponse(
                                HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR));
                        ctx.close();
                    }
                });
            } else {
                log.error("WebSocket连接失败");
                ctx.writeAndFlush(new DefaultFullHttpResponse(
                        HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR));
                ctx.close();
            }
        });
    }

    private void sendRequestOverWebSocket(ChannelHandlerContext ctx, FullHttpRequest request) {
        if (websocketChannel != null && websocketChannel.isActive()) {
            // 发送CONNECT请求到代理服务端
            WebSocketFrame frame = new TextWebSocketFrame(request.uri());
            websocketChannel.writeAndFlush(frame);

            // 立即移除客户都和服务端的channel通道HTTP处理器
            removeCheckHttpHandler(websocketChannel.pipeline(), HttpServerCodec.class);
            removeCheckHttpHandler(websocketChannel.pipeline(), HttpObjectAggregator.class);
            removeCheckHttpHandler(ctx.pipeline(), HttpServerCodec.class);
            removeCheckHttpHandler(ctx.pipeline(), HttpObjectAggregator.class);

            //流处理器替换
            removeCheckHttpHandler(ctx.pipeline(), this.getClass());
            ctx.channel().pipeline().addLast(new WebSocketRelayHandler(handshaker, websocketChannel,2));
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