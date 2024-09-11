package com.netty.client_proxy.handler;

import com.alibaba.fastjson.JSON;
import com.netty.client_proxy.entity.HttpRequestDTO;
import com.netty.client_proxy.entity.ProxyConfigDTO;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.timeout.IdleStateHandler;
import timber.log.Timber;
import com.netty.client_proxy.enums.*;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class FillWebSocketProxyHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private final String remoteHost;
    private final int remotePort;
    private final SslContext sslContext;
    private Channel websocketChannel; // WebSocket连接的通道
    private final ProxyConfigDTO proxyConfigDTO;

    public FillWebSocketProxyHandler(ProxyConfigDTO proxyConfigDTO) throws Exception {
        this.remoteHost = proxyConfigDTO.getRemoteHost();
        this.remotePort = proxyConfigDTO.getRemotePort();
        this.proxyConfigDTO = proxyConfigDTO;
        this.sslContext = SslContextBuilder.forClient()
                .protocols("TLSv1.1", "TLSv1.2")
                .ciphers(null)  // 默认使用所有可用的加密套件
                .build();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
        Timber.i("Received http request: %s", request.uri());
        if (websocketChannel == null || !websocketChannel.isActive()) {
            Timber.d("首次进行websocket握手，加载websocket通道");
            handleConnect(ctx, request);
        } else {
            Timber.d("发送数据到服务器");
            sendRequestOverWebSocket(ctx, request);
        }

    }

    WebSocketClientHandshaker handshaker;
    private void handleConnect(ChannelHandlerContext ctx, FullHttpRequest request) throws URISyntaxException {
        String wsUri;
        // 根据设置，websocket握手是否启用SSL访问
        if (proxyConfigDTO.getSslRequestEnabled()) {
            wsUri = "wss://";
        }else {
            wsUri = "ws://";
        }
        URI uri = new URI(wsUri + remoteHost + ":" + remotePort + "/websocket");
        handshaker = WebSocketClientHandshakerFactory
                .newHandshaker(uri, WebSocketVersion.V13, null, false, new DefaultHttpHeaders(),Integer.MAX_VALUE);
        WebSocketRelayHandler webSocketRelayHandler = new WebSocketRelayHandler(handshaker, ctx.channel(), ChannelFlowEnum.LOCAL_CHANNEL_FLOW);

        Bootstrap b = new Bootstrap();
        b.group(ctx.channel().eventLoop())
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        // 根据设置,是否启用SSL访问
                        if (proxyConfigDTO.getSslRequestEnabled()) {
                            ch.pipeline().addLast(sslContext.newHandler(ch.alloc(), remoteHost, remotePort));
                        }
                        ch.pipeline().addLast(new HttpClientCodec());
                        ch.pipeline().addLast(new HttpObjectAggregator(Integer.MAX_VALUE));

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
                        Timber.e("WebSocket握手失败,%s:%s",remoteHost,remotePort);
                        ctx.writeAndFlush(new DefaultFullHttpResponse(
                                HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR));
                        ctx.close();
                    }
                });
            } else {
                Timber.e("WebSocket连接失败,%s:%s",remoteHost,remotePort);
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
            String reqStr = JSON.toJSONString(httpRequestDTO);
            WebSocketFrame frame = new TextWebSocketFrame(reqStr);
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
            Timber.i("Connection was reset by the peer");
        } else {
            Timber.e(cause,"Error occurred in FillProxyHandler");
        }
        ctx.close();
    }

    private void removeCheckHttpHandler(ChannelPipeline pipeline, Class<? extends ChannelHandler> clazz) {
        if (pipeline.get(clazz) != null){
            pipeline.remove(clazz);
        }
    }
}