package com.custom.proxy.test;

import com.alibaba.fastjson.JSON;
import com.custom.proxy.handler.WebSocketRelayHandler;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketClientCompressionHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;

import java.net.URI;

public class WebSocketClientMain {
    public static void main(String[] args) throws Exception {
        URI uri = new URI("ws://127.0.0.1:6088"); // 修改为您的WebSocket服务端地址
        String protocol = uri.getScheme();
        final String host = uri.getHost();
        final int port = uri.getPort();

        EventLoopGroup group = new NioEventLoopGroup();
        try {
            WebSocketClientHandshaker handshaker = WebSocketClientHandshakerFactory.newHandshaker(
                    uri, WebSocketVersion.V13, null, false, new DefaultHttpHeaders());

            Bootstrap b = new Bootstrap();
            b.group(group)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline p = ch.pipeline();
                            if (protocol.equals("wss")) {
//                                SslContext sslCtx = SslContextBuilder.forClient().build();
//                                p.addLast(sslCtx.newHandler(ch.alloc(), host, port));
                            }
                            p.addLast(WebSocketClientCompressionHandler.INSTANCE);
                            p.addLast(new HttpClientCodec());
                            p.addLast(new HttpObjectAggregator(8192));
                            p.addLast(new WebSocketRelayHandler(ch.pipeline().channel()));
                        }
                    });


            ChannelFuture connectFuture = b.connect(uri.getHost(), port);

            Channel ch = connectFuture.addListener((ChannelFutureListener) future -> {
                if (future.isSuccess()) {
                    // 发送WebSocket握手请求
                    handshaker.handshake(future.channel()).addListener(handshakeFuture -> {
                        if (!handshakeFuture.isSuccess()) {
                            System.out.println("WebSocket Handshake initiation failed: " + handshakeFuture.cause());
                            future.channel().close();
                        }else {
                            // 发送文本消息
                            removeCheckHttpHandler(future.channel().pipeline(), HttpServerCodec.class);
                            removeCheckHttpHandler(future.channel().pipeline(), HttpObjectAggregator.class);
                            WebSocketFrame frame = new TextWebSocketFrame("Hello, WebSocket!");
                            future.channel().writeAndFlush(frame);
                            future.channel().pipeline().addLast(new WebSocketRelayHandler(future.channel()));
                        }
                    });

                } else {
                    System.out.println("WebSocket connection failed: " + future.cause());
                    future.channel().writeAndFlush(new DefaultFullHttpResponse(
                            HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR));
                    future.channel().close();
                }
            }).sync().channel();



            // 等待服务器响应
            Thread.sleep(5000);

            // 关闭连接
            ch.writeAndFlush(new CloseWebSocketFrame());
            ch.closeFuture().sync();
        } finally {
            group.shutdownGracefully();
        }
    }
    private static void removeCheckHttpHandler(ChannelPipeline pipeline, Class<? extends ChannelHandler> clazz) {
        if (pipeline.get(clazz) != null){
            System.out.println("remove check http handler");
            pipeline.remove(clazz);
        }
    }

}
