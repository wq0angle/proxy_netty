package com.netty.client.entry;

import com.netty.client.handler.FillWebSocketProxyHandler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ProxyClientEntry {

    /**
     * 启动客户端，初始化连接并设置默认处理器链。
     * @throws Exception 网络连接或初始化时的异常
     */
    public void start(int localPort, String remoteHost, int remotePort) throws Exception {
//        CertificateProvider.buildRootSslFile();

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
//                            p.addLast(new FillProxyHandler(remoteHost, remotePort));
                            p.addLast(new FillWebSocketProxyHandler(remoteHost, remotePort));
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