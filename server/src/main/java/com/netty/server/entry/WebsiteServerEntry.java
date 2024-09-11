package com.netty.server.entry;

import com.netty.server.handler.StaticFileServerHandler;
import com.sun.net.httpserver.HttpServer;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
@EnableAsync
@Component
public class WebsiteServerEntry {

    /**
     * 启动静态网站服务(部署伪装网站, 应对流量嗅探并提高隐蔽性)
     */
    @Async
    public void start(String ipAddress,Integer port,String websiteDirectory) throws Exception {

        EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        public void initChannel(SocketChannel ch) {
                            ch.pipeline().addLast(new HttpServerCodec());
                            ch.pipeline().addLast(new HttpObjectAggregator(Integer.MAX_VALUE));
                            ch.pipeline().addLast(new StaticFileServerHandler(new File(websiteDirectory)));
                        }
                    });

            Channel ch = b.bind(ipAddress,port).sync().channel();
            log.info("静态网站部署启动 url-> {}:{} | 资源目录:{}", ipAddress, port, websiteDirectory);
            ch.closeFuture().sync();
        } finally {
            workerGroup.shutdownGracefully();
            bossGroup.shutdownGracefully();
        }
    }
}