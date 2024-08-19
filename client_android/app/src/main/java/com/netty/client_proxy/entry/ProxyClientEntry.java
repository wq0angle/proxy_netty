package com.netty.client_proxy.entry;

import com.netty.client_proxy.config.ProxyLoadConfig;
import com.netty.client_proxy.entity.ProxyConfigDTO;
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
import org.jetbrains.annotations.NotNull;
import timber.log.Timber;

public class ProxyClientEntry {
    EventLoopGroup bossGroup;
    EventLoopGroup workerGroup ;
    /**
     * 启动客户端，初始化连接并设置默认处理器链。
     */
    public void start() {
        ProxyConfigDTO proxyConfigDTO = ProxyLoadConfig.getProxyConfigDTO();

        // 设置本地代理的端口，连接远程的地址、端口
        int localPort = proxyConfigDTO.getLocalPort();
        String remoteHost = proxyConfigDTO.getRemoteHost();
        int remotePort = proxyConfigDTO.getRemotePort();

        if (bossGroup != null && !bossGroup.isShutdown()){
            Timber.tag("ProxyClient").e("客户端已开启,不能重复开启");
        }
        if (workerGroup != null && !workerGroup.isShutdown()){
            Timber.tag("ProxyClient").e("客户端已开启,不能重复开启");
        }

        bossGroup = new NioEventLoopGroup();
        workerGroup = new NioEventLoopGroup();
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
                            // 根据设置,选择代理请求类型
                            if (ProxyReqEnum.parse(proxyConfigDTO.getProxyType()).equals(ProxyReqEnum.HTTP)) {
                                // HTTP编码处理器
                                p.addLast(new HttpServerCodec());
                                // HTTP消息聚合处理器，避免半包问题
                                p.addLast(new HttpObjectAggregator(maxContentLength));
                                p.addLast(new FillProxyHandler(remoteHost, remotePort, proxyConfigDTO));
                            }else if (ProxyReqEnum.parse(proxyConfigDTO.getProxyType()).equals(ProxyReqEnum.WEBSOCKET)) {
                                p.addLast(new HttpServerCodec());
                                p.addLast(new HttpObjectAggregator(maxContentLength));
                                p.addLast(new FillWebSocketProxyHandler(proxyConfigDTO));
                            }else {
                                Timber.i("请检查配置, 不支持的代理类型: %s ", proxyConfigDTO.getProxyType());
                            }
                        }
                    });

            Channel ch = b.bind("127.0.0.1",localPort).sync().channel();
            Timber.i("代理客户端启动，监听端口: %s ", localPort);
            ch.closeFuture().sync();
        } catch (Exception e){
            Timber.e(e, "代理客户端启动失败");
        }
    }

    public void stop(){
        if (bossGroup == null || bossGroup.isShutdown()) {
            Timber.tag("ProxyClient").e("客户端已关闭,不能重复关闭");
            return;
        }
        if (workerGroup == null || workerGroup.isShutdown()) {
            Timber.tag("ProxyClient").e("客户端已关闭,不能重复关闭");
            return;
        }

        bossGroup.shutdownGracefully();
        workerGroup.shutdownGracefully();
        Timber.tag("ProxyClient").i("客户端关闭成功");
    }

}