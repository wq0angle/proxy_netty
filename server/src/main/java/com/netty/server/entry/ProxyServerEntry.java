package com.netty.server.entry;

import com.beust.jcommander.internal.Maps;
import com.netty.server.config.AppConfig;
import com.netty.server.handler.AnalysisProxyHandler;
import com.netty.server.handler.AnalysisWebSocketProxyHandler;
import com.netty.server.handler.ProxyLoaderHandler;
import com.netty.server.provider.SslContextProvider;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@EnableAsync
@Component
public class ProxyServerEntry {

    @Autowired
    private AppConfig appConfig;

//    @Async
    public void start(int port) throws Exception {
        EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        Integer maxContentLength = 1024 * 1024 * 10; //设置最大响应体大小 10M
        // 目录文件中获取sslContextMap
        Map<String,SslContext> sslContextMap;
        if (appConfig.getSslListenerEnabled()) {
            sslContextMap = SslContextProvider.mapSslContext(appConfig.getSslJksPath(), appConfig.getSslJksFilePassword());
        }else {
            sslContextMap = Maps.newHashMap();
        }
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<>() {
                        @Override
                        protected void initChannel(Channel ch) throws Exception {
                            ChannelPipeline p = ch.pipeline();
                            //添加sniHandler，根据域名添加对应证书到SSL解析器
                            if (appConfig.getSslListenerEnabled()) {
                                String sinDefaultFile = appConfig.getSinDefaultFile();
                                p.addLast(SslContextProvider.getSniHandler(sslContextMap,sinDefaultFile.trim()));
                            }
                            p.addLast(new LoggingHandler(LogLevel.DEBUG)); // 添加日志处理器，输出 SSL 握手过程中的详细信息
                            p.addLast(new HttpServerCodec());
                            p.addLast(new HttpObjectAggregator(maxContentLength));
                            p.addLast(new ProxyLoaderHandler(appConfig));
                        }
                    });

            Channel ch = b.bind(port).sync().channel();
            log.info("代理主服务端启动，监听端口: {}", port);
            ch.closeFuture().sync();
            log.info("代理主服务端关闭");
        } finally {
            // 主要用于调试程序时强制终止程序
            bossGroup.shutdownGracefully(0, 0, TimeUnit.SECONDS);
            workerGroup.shutdownGracefully(0, 0, TimeUnit.SECONDS);

        }
    }

}
