package com.netty.client_proxy.handler;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import com.netty.client_proxy.config.AppConfig;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
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
            bootstrap.group(ctx.channel().eventLoop())
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.SO_KEEPALIVE, true)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
//                            ch.pipeline().addLast(new HttpClientCodec());
//                            ch.pipeline().addLast(new HttpObjectAggregator(Integer.MAX_VALUE));
                            ch.pipeline().addLast(new RelayHandler(ctx.channel())); // 使用RelayHandler进行转发
                        }
                    });
            byteBuf.retain();
            // 连接到远程Netty服务器
            ChannelFuture connectFuture = bootstrap.connect(remoteHost, remotePort);
            connectFuture.addListener((ChannelFutureListener) future -> {
                if (future.isSuccess()) {
                    future.channel().writeAndFlush(byteBuf.retain());
                    Timber.i("成功连接到远程服务器: %s:%s", remoteHost, remotePort);
                } else {
                    Timber.e("连接到远程服务器失败: %s:%s", remoteHost, remotePort);
                }
            });
        } catch (Exception e) {
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