package com.custom.proxy.handler.server;

import com.custom.proxy.provider.SslContextProvider;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.ssl.SniHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.util.concurrent.ImmediateEventExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;

import javax.net.ssl.SSLException;
import java.io.File;

@Slf4j
public class ProxyServerHandler{

    public void start(int port) throws Exception {
        EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        Integer maxContentLength = 1024 * 1024 * 10; //设置最大响应体大小 10M
        SslContext sslContext1 = SslContextProvider.getSslContext1();
        SslContext sslContext2 = SslContextProvider.getSslContext2();
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<>() {
                        @Override
                        protected void initChannel(Channel ch) throws Exception {
                            ChannelPipeline p = ch.pipeline();
                            //添加sniHandler，根据域名添加对应证书到SSL解析器
                            p.addLast(SslContextProvider.getSniHandler(sslContext1, sslContext2));
                            p.addLast(new HttpServerCodec());
                            p.addLast(new HttpObjectAggregator(maxContentLength));
                            p.addLast(new AnalysisProxyHandler());
                        }
                    });

            Channel ch = b.bind(port).sync().channel();
            log.info("代理服务端启动，监听端口: {}", port);
            ch.closeFuture().sync();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
}
