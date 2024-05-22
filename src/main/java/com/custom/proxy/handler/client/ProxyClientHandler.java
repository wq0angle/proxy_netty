package com.custom.proxy.handler.client;

import com.custom.proxy.provider.CertificateProvider;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslHandler;
import lombok.extern.slf4j.Slf4j;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;

@Slf4j
public class ProxyClientHandler{

    public static void start(int localPort, String remoteHost, int remotePort) throws Exception {
        CertificateProvider.buildSslFile();

        EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        Integer maxContentLength = 1024 * 1024 * 10;

        // 添加 SSL 处理器，用于解密来自客户端的流量
        SSLContext sslCtx = CertificateProvider.createTargetSslContext("localhost");
        SSLEngine sslEngine = sslCtx.createSSLEngine();
        sslEngine.setUseClientMode(false); // 设置为服务器模式

        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .handler(new LoggingHandler(LogLevel.INFO))
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            ChannelPipeline p = ch.pipeline();
                            ch.pipeline().addFirst(new SslHandler(sslEngine));
                            p.addLast(new HttpServerCodec());
                            p.addLast(new HttpObjectAggregator(maxContentLength));
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