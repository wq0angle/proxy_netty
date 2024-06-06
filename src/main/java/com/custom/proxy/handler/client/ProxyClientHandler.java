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
import io.netty.handler.timeout.IdleStateHandler;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;

@Slf4j
public class ProxyClientHandler {

    /**
     * 启动客户端，初始化连接并设置默认处理器链。
     * @throws Exception 网络连接或初始化时的异常
     */
    public static void start(int localPort, String remoteHost, int remotePort) throws Exception {
        CertificateProvider.buildRootSslFile();

        EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        int maxContentLength = 1024 * 1024 * 10;

        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    // 添加日志处理器
                    .handler(new LoggingHandler(LogLevel.DEBUG))
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            ChannelPipeline p = ch.pipeline();
                            // HTTP编码处理器
                            p.addLast(new HttpServerCodec());
                            // HTTP消息聚合处理器，避免半包问题
                            p.addLast(new HttpObjectAggregator(maxContentLength));
                            // 心跳检测，避免因长时间无数据传输导致的连接断开
                            ch.pipeline().addLast(new IdleStateHandler(0, 30, 0, TimeUnit.SECONDS));
                            p.addLast(new FillProxyHandler(remoteHost, remotePort));
//                            p.addLast(new FillWebSocketProxyHandler(remoteHost, remotePort));
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