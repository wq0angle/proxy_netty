package com.custom.proxy.handler.server;

import com.custom.proxy.provider.SslContextProvider;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Slf4j
@EnableAsync
@Component
public class ProxyServerHandler{

    @Async
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
                            p.addLast(new LoggingHandler(LogLevel.DEBUG)); // 添加日志处理器，输出 SSL 握手过程中的详细信息
                            p.addLast(new HttpServerCodec());
                            p.addLast(new HttpObjectAggregator(maxContentLength));
                            p.addLast(new WebSocketServerProtocolHandler("/websocket"));
                            p.addLast(new AnalysisWebSocketProxyHandler());
                            p.addLast(new AnalysisProxyHandler());
                        }
                    });

            Channel ch = b.bind(port).sync().channel();
            log.info("代理主服务端启动，监听端口: {}", port);
            ch.closeFuture().sync();
        } finally {
            // 设置优雅关闭的等待时间为1秒，超时时间为2秒，根据需要调整这些值, 主要用于调试程序时强制终止程序
            bossGroup.shutdownGracefully(1, 2, TimeUnit.SECONDS);
            workerGroup.shutdownGracefully(1, 2, TimeUnit.SECONDS);

        }
    }
}
