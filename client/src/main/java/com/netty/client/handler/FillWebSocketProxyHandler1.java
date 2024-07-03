package com.netty.client.handler;

import com.netty.client.config.AppConfig;
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
import java.util.concurrent.TimeUnit;

@Slf4j
public class FillWebSocketProxyHandler1 extends SimpleChannelInboundHandler<FullHttpRequest> {

    private Channel websocketChannel; // WebSocket连接的通道
    private final AppConfig appConfig;
    WebSocketConnectHandler webSocketConnect;

    public FillWebSocketProxyHandler1(AppConfig appConfig) throws Exception {
        this.appConfig = appConfig;
        webSocketConnect = WebSocketConnectHandler.getInstance();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
        log.info("Received http request: {}", request.uri());

        websocketChannel = webSocketConnect.getWebsocketChannel();
        if (websocketChannel == null) {
            log.debug("首次进行websocket握手，加载websocket通道");
            webSocketConnect.startConnect(appConfig);
            websocketChannel = webSocketConnect.getWebsocketChannel();
        }

        sendRequestOverWebSocket(ctx, request);

    }

    private void sendRequestOverWebSocket(ChannelHandlerContext ctx, FullHttpRequest request) {
        if (websocketChannel != null) {
            // 发送CONNECT请求到代理服务端
            WebSocketFrame frame = new TextWebSocketFrame(request.uri());
            websocketChannel.writeAndFlush(frame);

            // 立即移除客户端channel通道HTTP处理器
            removeCheckHttpHandler(ctx.pipeline(), HttpServerCodec.class);
            removeCheckHttpHandler(ctx.pipeline(), HttpObjectAggregator.class);

            //流处理器替换
            removeCheckHttpHandler(ctx.pipeline(), this.getClass()); //移除当前处理器
            websocketChannel.pipeline().addLast(new WebSocketRelayHandler1(ctx.channel(),ChannelFlowEnum.LOCAL_CHANNEL_FLOW));
            ctx.channel().pipeline().addLast(new WebSocketRelayHandler1(websocketChannel,ChannelFlowEnum.FUTURE_CHANNEL_FLOW));
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