package com.netty.server.handler;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AnalysisVpnHandler extends SimpleChannelInboundHandler<ByteBuf> {

    private final String targetHost;
    private final int targetPort;
    private ChannelFuture targetChannelFuture;

    public AnalysisVpnHandler(String targetHost, int targetPort) {
        this.targetHost = targetHost;
        this.targetPort = targetPort;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf byteBuf) throws Exception {
        // 假设数据包的前8个字节是目标主机和端口信息
        // 这里需要根据实际数据包格式进行解析
        String targetHost = extractDestinationIP(byteBuf.array());
        int targetPort = extractDestinationPort(byteBuf.array());

        // 如果目标通道未连接，则建立连接
        if (targetChannelFuture == null || !targetChannelFuture.channel().isActive()) {
            connectToTargetServer(ctx, targetHost, targetPort);
        } else {
            // 转发数据到目标服务器
            targetChannelFuture.channel().writeAndFlush(byteBuf.retain());
        }
    }

    private void connectToTargetServer(ChannelHandlerContext ctx, String targetHost, int targetPort) {
        EventLoopGroup group = new NioEventLoopGroup();
        try {
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(group)
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.SO_KEEPALIVE, true)
                    .handler(new TargetServerHandler(ctx)); // 处理目标服务器的响应

            // 连接到目标服务器
            targetChannelFuture = bootstrap.connect(targetHost, targetPort);
            targetChannelFuture.addListener(future -> {
                if (future.isSuccess()) {
                    log.info("成功连接到目标服务器: {}:{}", targetHost, targetPort);
                } else {
                    log.error("连接到目标服务器失败: {}:{}", targetHost, targetPort);
                    ctx.close(); // 关闭VPN连接
                }
            });
        } catch (Exception e) {
            log.error("连接到目标服务器时发生错误", e);
            ctx.close(); // 关闭VPN连接
        }
    }

    private String extractDestinationIP(byte[] data) {
        // IP 地址位于 IP 头部的第16到第19字节
        return (data[16] & 0xFF) + "." +
                (data[17] & 0xFF) + "." +
                (data[18] & 0xFF) + "." +
                (data[19] & 0xFF);
    }

    private int extractDestinationPort(byte[] data) {
        // 假设 IP 头部长度为20字节（无选项字段）
        return ((data[20 + 2] & 0xFF) << 8) | (data[20 + 3] & 0xFF);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("发生异常: {}", cause.getMessage());
        ctx.close(); // 关闭连接
    }

    // 处理目标服务器响应的处理器
    private class TargetServerHandler extends SimpleChannelInboundHandler<ByteBuf> {
        private final ChannelHandlerContext vpnCtx;

        public TargetServerHandler(ChannelHandlerContext vpnCtx) {
            this.vpnCtx = vpnCtx; // 保存VPN的上下文
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, ByteBuf byteBuf) throws Exception {
            // 将目标服务器的响应回传给VPN客户端
            vpnCtx.writeAndFlush(byteBuf.retain());
            log.info("从目标服务器接收到响应并回传给VPN客户端");
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            log.error("目标服务器处理异常: {}", cause.getMessage());
            ctx.close(); // 关闭连接
        }
    }
}