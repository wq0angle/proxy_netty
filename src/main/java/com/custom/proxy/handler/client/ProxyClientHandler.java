package com.custom.proxy.handler.client;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.*;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ProxyClientHandler{

    public static void start(int localPort, String remoteHost, int remotePort) throws Exception {
        EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
//                    .handler(new LoggingHandler(LogLevel.INFO))
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline p = ch.pipeline();
                            p.addLast(new HttpServerCodec());
                            p.addLast(new HttpObjectAggregator(65536));
                            p.addLast(new FillProxyHandler(remoteHost, remotePort));
                        }
                    });

            Channel ch = b.bind(localPort).sync().channel();
            log.info("HTTP代理客户端启动，监听端口: {}", localPort);
            ch.closeFuture().sync();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }


}