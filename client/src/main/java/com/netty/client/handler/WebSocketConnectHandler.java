package com.netty.client.handler;

import com.netty.client.config.AppConfig;
import com.netty.common.enums.ChannelFlowEnum;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.net.ssl.SSLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.TimeUnit;

@Slf4j
@Getter
public class WebSocketConnectHandler {

    WebSocketClientHandshaker handshaker;
    private Channel websocketChannel; // WebSocket连接的通道

    public static WebSocketConnectHandler instance;

    public static WebSocketConnectHandler getInstance() {
        if (instance == null) {
            instance = new WebSocketConnectHandler();
        }
        return instance;
    }

    public void startConnect(AppConfig appConfig) throws URISyntaxException, InterruptedException, SSLException {
        SslContext sslContext = SslContextBuilder.forClient()
                .protocols("TLSv1.1", "TLSv1.2", "TLSv1.3")
                .ciphers(null)  // 默认使用所有可用的加密套件
                .build();

        String wsUri;
        // 根据设置，websocket握手是否启用SSL访问
        if (appConfig.getSslRequestEnabled()) {
            wsUri = "wss://";
        }else {
            wsUri = "ws://";
        }
        URI uri = new URI(wsUri + appConfig.getRemoteHost() + ":" + appConfig.getRemotePort() + "/websocket");
        handshaker = WebSocketClientHandshakerFactory
                .newHandshaker(uri, WebSocketVersion.V13, null, false, new DefaultHttpHeaders());

        Bootstrap b = new Bootstrap();
        EventLoopGroup group = new NioEventLoopGroup();
        b.group(group)
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        // 根据设置,是否启用SSL访问
                        if (appConfig.getSslRequestEnabled()) {
                            ch.pipeline().addLast(sslContext.newHandler(ch.alloc(), appConfig.getRemoteHost(), appConfig.getRemotePort()));
                        }
                        ch.pipeline().addLast(new HttpClientCodec());
                        ch.pipeline().addLast(new HttpObjectAggregator(Integer.MAX_VALUE));

                        // 读超时60秒，写超时30秒
                        ch.pipeline().addLast(new IdleStateHandler(60, 30, 0, TimeUnit.SECONDS));
                        ch.pipeline().addLast(new SimpleChannelInboundHandler<FullHttpResponse>() {
                            @Override
                            protected void channelRead0(ChannelHandlerContext ctx, FullHttpResponse msg) throws Exception {
                                if (msg.status().code() != 101) {
                                    log.error("WebSocket握手失败,{}:{}->{}",appConfig.getRemoteHost(), appConfig.getRemotePort(), msg);
                                    return;
                                }
                                handshaker.finishHandshake(ctx.channel(), msg);

                                removeCheckHttpHandler(ctx.pipeline(), HttpServerCodec.class);
                                removeCheckHttpHandler(ctx.pipeline(), HttpObjectAggregator.class);
                                ctx.pipeline().remove(this);
                            }
                        });
                    }
                });

        ChannelFuture connectFuture = b.connect(uri.getHost(), uri.getPort()).sync();
        if (!connectFuture.isSuccess()) {
            log.error("WebSocket连接失败,{}:{}",appConfig.getRemoteHost(), appConfig.getRemotePort());
            return;
        }
        websocketChannel = connectFuture.channel();
        ChannelPromise handshakeFuture = websocketChannel.newPromise();
        handshaker.handshake(websocketChannel, handshakeFuture).addListener(future -> {
            if (!future.isSuccess()) {
                log.error("WebSocket握手失败,{}:{}",appConfig.getRemoteHost(), appConfig.getRemotePort());
            }
        });

    }

    private void removeCheckHttpHandler(ChannelPipeline pipeline, Class<? extends ChannelHandler> clazz) {
        if (pipeline.get(clazz) != null){
            pipeline.remove(clazz);
        }
    }
}
