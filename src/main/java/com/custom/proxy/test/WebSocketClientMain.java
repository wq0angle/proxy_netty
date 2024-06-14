package com.custom.proxy.test;

import com.alibaba.fastjson.JSON;
import com.custom.proxy.handler.WebSocketRelayHandler;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketClientCompressionHandler;
import io.netty.util.CharsetUtil;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.Objects;

public class WebSocketClientMain {
    public static void main(String[] args) throws Exception {
        URI uri = new URI("ws://127.0.0.1:6088/websocket"); // 修改为您的WebSocket服务端地址
        EventLoopGroup group = new NioEventLoopGroup();
        try {
            //注意这里的false，因为我们不希望WebSocketRelayHandler处理HTTP响应
            WebSocketClientHandshaker handshaker = WebSocketClientHandshakerFactory
                    .newHandshaker(uri, WebSocketVersion.V13, null, false, new DefaultHttpHeaders());

            WebSocketRelayHandler webSocketRelayHandler = new WebSocketRelayHandler(handshaker, null,1);

            Bootstrap b = new Bootstrap();
            b.group(group)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline p = ch.pipeline();

                            // 添加必要的HTTP编解码器，因为WebSocket握手是基于HTTP的
                            p.addLast(new HttpClientCodec());
                            p.addLast(new HttpObjectAggregator(Integer.MAX_VALUE)); // 必须要有，因为WebSocket握手响应可能被分片

                            // 添加WebSocketClientProtocolHandler，它会处理握手逻辑
//                            p.addLast(new WebSocketClientProtocolHandler(handshaker));

                            // 添加自定义的WebSocket消息处理器
//                            handler.setInboundChannel(ch);
                            p.addLast(webSocketRelayHandler); // 接收并处理来自服务器的WebSocket消息
                        }
                    });

            ChannelFuture connectFuture = b.connect(uri.getHost(), uri.getPort()).sync();
            Channel ch = connectFuture.channel();

            webSocketRelayHandler.handshakeFuture().sync();

            // 现在WebSocketClientProtocolHandler会自动处理握手，无需手动调用handshake方法
            // WebSocket连接建立后，逻辑已在userEventTriggered和CustomWebSocketFrameHandler中定义

            BufferedReader console = new BufferedReader(new InputStreamReader(System.in));
            while (true) {
                String msg = console.readLine();
                if (msg == null) {
                    break;
                } else if ("再见".equalsIgnoreCase(msg)) {
                    ch.writeAndFlush(new CloseWebSocketFrame());
                    ch.closeFuture().sync();
                    break;
                } else if ("ping".equalsIgnoreCase(msg)) {
                    WebSocketFrame frame = new PingWebSocketFrame(Unpooled.wrappedBuffer(new byte[]{8, 1, 8, 1}));
                    ch.writeAndFlush(frame);
                } else {
                    WebSocketFrame frame = new TextWebSocketFrame(msg);
                    ch.writeAndFlush(frame);
                }
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            group.shutdownGracefully();
        }
    }

}

