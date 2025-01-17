package com.netty.client.gui.entry;

import com.netty.client.gui.entity.AppConfig;
import com.netty.client.gui.handler.ProxyLoaderHandler;
import com.netty.common.enums.ProxyReqEnum;
import com.netty.client.gui.controller.MainController;
import com.netty.client.gui.handler.FillProxyHandler;
import com.netty.client.gui.handler.FillWebSocketProxyHandler;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ProxyClientEntry {

    private static EventLoopGroup bossGroup;
    private static EventLoopGroup workerGroup;

    /**
     * 启动客户端，初始化连接并设置默认处理器链。
     * @throws Exception 网络连接或初始化时的异常
     */
    public void start(AppConfig mainAppConfig){

        try {
            int localPort = mainAppConfig.getLocalPort();
            String remoteHost = mainAppConfig.getRemoteHost();
            int remotePort = mainAppConfig.getRemotePort();

            bossGroup = new NioEventLoopGroup();
            workerGroup = new NioEventLoopGroup();

            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    // 添加日志处理器
//                .handler(new LoggingHandler(LogLevel.DEBUG))
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            ChannelPipeline p = ch.pipeline();
                            // HTTP编码处理器
                            p.addLast(new HttpServerCodec());
                            // HTTP消息聚合处理器，避免半包问题
                            p.addLast(new HttpObjectAggregator(Integer.MAX_VALUE));
                            p.addLast(new ProxyLoaderHandler(mainAppConfig));
                        }
                    });

            Channel ch = b.bind("127.0.0.1", localPort).sync().channel();
            MainController.appendToConsole("HTTP代理客户端启动，监听端口: " + localPort + "\n");
            log.info("HTTP代理客户端启动，监听端口: {}", localPort);
            ch.closeFuture().sync();
        }catch (Exception e){
            log.error("启动代理客户端时出错", e);
            throw new RuntimeException(e);
        }
    }

    public void stop(){
        if (!isRunning()) {
            log.error("客户端已关闭,不能重复关闭");
            return;
        }

        bossGroup.shutdownGracefully();
        workerGroup.shutdownGracefully();
        log.info("客户端关闭成功");
    }

    public Boolean isRunning(){
        return bossGroup != null && !bossGroup.isShutdown() && workerGroup != null && !workerGroup.isShutdown();
    }

}