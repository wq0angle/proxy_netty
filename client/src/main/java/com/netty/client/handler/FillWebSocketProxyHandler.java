package com.netty.client.handler;

import com.alibaba.fastjson.JSON;
import com.netty.client.config.AppConfig;
import com.netty.common.entity.HttpRequestDTO;
import com.netty.common.enums.ChannelFlowEnum;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
public class FillWebSocketProxyHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private final String remoteHost;
    private final int remotePort;
    private final SslContext sslContext;
    private Channel websocketChannel; // WebSocket连接的通道
    private final AppConfig appConfig;

    public FillWebSocketProxyHandler(AppConfig appConfig) throws Exception {
        this.remoteHost = appConfig.getRemoteHost();
        this.remotePort = appConfig.getRemotePort();
        this.appConfig = appConfig;
        this.sslContext = SslContextBuilder.forClient()
                .protocols("TLSv1.1", "TLSv1.2", "TLSv1.3")
                .ciphers(null)  // 默认使用所有可用的加密套件
                .build();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
        log.info("Received http request: {}", request.uri());
        if (websocketChannel == null || !websocketChannel.isActive()) {
            log.debug("首次进行websocket握手，加载websocket通道");
            handleConnect(ctx, request);
        } else {
            log.debug("发送数据到服务器");
            sendRequestOverWebSocket(ctx, request);
        }

    }

    WebSocketClientHandshaker handshaker;
    private void handleConnect(ChannelHandlerContext ctx, FullHttpRequest request) throws URISyntaxException {
        String wsUri;
        // 根据设置，websocket握手是否启用SSL访问
        if (appConfig.getSslRequestEnabled()) {
            wsUri = "wss://";
        }else {
            wsUri = "ws://";
        }
        URI uri = new URI(wsUri + remoteHost + ":" + remotePort + "/websocket");
        handshaker = WebSocketClientHandshakerFactory
                .newHandshaker(uri, WebSocketVersion.V13, null, false, new DefaultHttpHeaders());
        WebSocketRelayHandler webSocketRelayHandler = new WebSocketRelayHandler(handshaker, ctx.channel(), ChannelFlowEnum.LOCAL_CHANNEL_FLOW);

        Bootstrap b = new Bootstrap();
        b.group(ctx.channel().eventLoop())
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        // 根据设置,是否启用SSL访问
                        if (appConfig.getSslRequestEnabled()) {
                            ch.pipeline().addLast(sslContext.newHandler(ch.alloc(), remoteHost, remotePort));
                        }
                        ch.pipeline().addLast(new HttpClientCodec());
                        ch.pipeline().addLast(new HttpObjectAggregator(1024 * 1024 * 10));

                        // 读超时60秒，写超时30秒
                        ch.pipeline().addLast(new IdleStateHandler(60, 30, 0, TimeUnit.SECONDS));

                        ch.pipeline().addLast(webSocketRelayHandler);
                    }
                });

        FullHttpRequest requestNew = new DefaultFullHttpRequest(request.protocolVersion(), request.method(), request.uri());
        requestNew.headers().add(request.headers());

        b.connect(remoteHost, remotePort).addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                websocketChannel = future.channel();
                webSocketRelayHandler.handshakeFuture().addListener((ChannelFutureListener) handshakeFuture -> {
                    if (handshakeFuture.isSuccess()) {
                        sendRequestOverWebSocket(ctx, requestNew);
                    } else {
                        log.error("WebSocket握手失败,{}:{}",remoteHost,remotePort);
                        ctx.writeAndFlush(new DefaultFullHttpResponse(
                                HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR));
                        ctx.close();
                    }
                });
            } else {
                log.error("WebSocket连接失败,{}:{}",remoteHost,remotePort);
                ctx.writeAndFlush(new DefaultFullHttpResponse(
                        HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR));
                ctx.close();
            }
        });
    }

    private void sendRequestOverWebSocket(ChannelHandlerContext ctx, FullHttpRequest request) {
        if (websocketChannel != null && websocketChannel.isActive()) {
            // 发送请求到代理服务端
            Map<String,String> headers = request.headers().entries().stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            byte[] byteArray = new byte[request.content().readableBytes()];
            request.content().getBytes(0, byteArray); // 从索引 0 开始读取
            HttpRequestDTO httpRequestDTO = new HttpRequestDTO(request.uri(),request.method().name(),
                    request.protocolVersion().text(), headers, byteArray);
            WebSocketFrame frame = new TextWebSocketFrame(JSON.toJSONString(httpRequestDTO));
            websocketChannel.writeAndFlush(frame);

            // 立即移除客户端和服务端的channel通道HTTP处理器
            removeCheckHttpHandler(websocketChannel.pipeline(), HttpServerCodec.class);
            removeCheckHttpHandler(websocketChannel.pipeline(), HttpObjectAggregator.class);
            removeCheckHttpHandler(ctx.pipeline(), HttpServerCodec.class);
            removeCheckHttpHandler(ctx.pipeline(), HttpObjectAggregator.class);

            //流处理器替换
            removeCheckHttpHandler(ctx.pipeline(), this.getClass()); //移除当前处理器
            ctx.channel().pipeline().addLast(new WebSocketRelayHandler(handshaker, websocketChannel,ChannelFlowEnum.FUTURE_CHANNEL_FLOW));
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (cause instanceof IOException && cause.getMessage().contains("Connection reset")) {
            log.debug("Connection was reset by the peer");
        } else {
            log.error("Error occurred in FillProxyHandler", cause);
        }
        ctx.close();
    }

    private void removeCheckHttpHandler(ChannelPipeline pipeline, Class<? extends ChannelHandler> clazz) {
        if (pipeline.get(clazz) != null){
            pipeline.remove(clazz);
        }
    }
}