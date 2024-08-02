package com.netty.client_proxy.entry;

import com.netty.client_proxy.config.AppConfig;
import com.netty.client_proxy.enums.ProxyReqEnum;
import com.netty.client_proxy.handler.FillProxyHandler;
import com.netty.client_proxy.handler.FillWebSocketProxyHandler;
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
import android.util.Log;
import org.jetbrains.annotations.NotNull;
import timber.log.Timber;

public class ProxyClientEntry {

    /**
     * 启动客户端，初始化连接并设置默认处理器链。
     * @throws Exception 网络连接或初始化时的异常
     */
    public void start() throws Exception {
        AppConfig appConfig = new AppConfig();

        // 设置本地代理的端口，连接远程的地址、端口
        int localPort = appConfig.getLocalPort();
        String remoteHost = appConfig.getRemoteHost();
        int remotePort = appConfig.getRemotePort();

        EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        int maxContentLength = 1024 * 1024 * 10;

        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    // 添加日志处理器
//                    .handler(new LoggingHandler(LogLevel.DEBUG))
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(@NotNull SocketChannel ch) throws Exception {
                            ChannelPipeline p = ch.pipeline();
                            // HTTP编码处理器
                            p.addLast(new HttpServerCodec());
                            // HTTP消息聚合处理器，避免半包问题
                            p.addLast(new HttpObjectAggregator(maxContentLength));
                            // 根据设置,选择代理请求类型
                            if (ProxyReqEnum.parse(appConfig.getProxyType()).equals(ProxyReqEnum.HTTP)) {
                                p.addLast(new FillProxyHandler(remoteHost, remotePort, appConfig));
                            }else if (ProxyReqEnum.parse(appConfig.getProxyType()).equals(ProxyReqEnum.WEBSOCKET)) {
                                p.addLast(new FillWebSocketProxyHandler(appConfig));
//                                p.addLast(new FillWebSocketProxyHandler1(appConfig));
                            }else {
                                Timber.i("请检查配置, 不支持的代理类型: %s ", appConfig.getProxyType());
                            }
                        }
                    });

            Channel ch = b.bind("127.0.0.1",localPort).sync().channel();
            Timber.i("HTTP代理客户端启动，监听端口: %s ", localPort);
            ch.closeFuture().sync();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }

}