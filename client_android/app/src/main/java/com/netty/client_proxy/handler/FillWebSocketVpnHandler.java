package com.netty.client_proxy.handler;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import com.netty.client_proxy.config.AppConfig;
import timber.log.Timber;

public class FillWebSocketVpnHandler extends SimpleChannelInboundHandler<ByteBuf> {

    private final AppConfig appConfig;
    private final String remoteHost;
    private final int remotePort;
    private Channel relayChannel;

    public FillWebSocketVpnHandler(AppConfig appConfig) {
        this.appConfig = appConfig;
        this.remoteHost = appConfig.getRemoteHost();
        this.remotePort = appConfig.getRemotePort();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf byteBuf) throws Exception {
        // 如果relayChannel尚未连接，则建立连接

        if (relayChannel == null || !relayChannel.isActive()) {
            connectToRemoteServer(ctx,byteBuf);
        } else {
            // 转发数据
            relayChannel.writeAndFlush(byteBuf.retain()); // 使用retain()以防止ByteBuf被释放
        }
    }

    private void connectToRemoteServer(ChannelHandlerContext ctx,ByteBuf byteBuf) {
        EventLoopGroup group = new NioEventLoopGroup();
        try {
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(group)
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.SO_KEEPALIVE, true)
                    .handler(new RelayHandler(ctx.channel())); // 使用RelayHandler进行转发

            // 连接到远程Netty服务器
            ChannelFuture future = bootstrap.connect(remoteHost, remotePort).sync();
            relayChannel = future.channel(); // 保存远程连接的Channel
            future.addListener(f -> {
                if (f.isSuccess()) {
                    relayChannel.writeAndFlush(byteBuf.retain());
                    Timber.i("成功连接到远程服务器: %s:%s", remoteHost, remotePort);
                } else {
                    Timber.e("连接到远程服务器失败: %s:%s", remoteHost, remotePort);
                }
            });
        } catch (InterruptedException e) {
            Timber.e(e,"连接到远程服务器时发生错误");
        } finally {
            group.shutdownGracefully();
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        Timber.e(cause,"连接通道发生异常");
        ctx.close(); // 关闭连接
    }
}