package com.netty.server.handler;

import com.netty.common.enums.ChannelFlowEnum;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.http.*;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import lombok.extern.slf4j.Slf4j;

import javax.net.ssl.SSLException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

@Slf4j
public class AnalysisVpnHandler extends SimpleChannelInboundHandler<ByteBuf> {

    private final SslContext sslContext;

    public AnalysisVpnHandler() throws SSLException {
        this.sslContext = SslContextBuilder.forClient()
                .protocols("TLSv1.1", "TLSv1.2", "TLSv1.3")
                .ciphers(null)  // 默认使用所有可用的加密套件
                .build();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) throws Exception {
        byte[] array = new byte[msg.readableBytes()];
        msg.getBytes(msg.readerIndex(), array);

//        String targetIp = extractDestinationIP(array);
//        int targetPort = extractDestinationPort(array);
        String targetIp = "fanyi.baidu.com";
        int targetPort = 443;

        EventLoopGroup group = new NioEventLoopGroup();
        try {
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(ctx.channel().eventLoop())
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.SO_KEEPALIVE, true)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ch.pipeline().addLast(new HttpClientCodec());
                            ch.pipeline().addLast(new HttpObjectAggregator(Integer.MAX_VALUE));
                            ch.pipeline().addLast(sslContext.newHandler(ch.alloc(), targetIp, targetPort)); // 添加 SSL 处理器
                            //添加日志处理器
                            ch.pipeline().addLast(new LoggingHandler(LogLevel.INFO));
                            ch.pipeline().addLast(new RelayHandler(ctx.channel()));
                        }
                    });

//            msg.retain(); // 增加引用计数
            // 连接到远程Netty服务器
            ChannelFuture connectFuture = bootstrap.connect(targetIp, targetPort);
            connectFuture.addListener((ChannelFutureListener) future -> {
                if (future.isSuccess()) {

                    ctx.pipeline().addLast(new HttpClientCodec());
                    ctx.pipeline().addLast(new HttpObjectAggregator(Integer.MAX_VALUE));

                    FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
                    response.headers().set("proxy", "text/plain; charset=UTF-8");
                    ctx.writeAndFlush(response);

//                    relayChannel.writeAndFlush(msg.retain());
//                    future.channel().writeAndFlush(createFullHttpRequest(targetIp,targetPort,"/"));
                    // 流处理器替换
//                    ctx.pipeline().addLast(new RelayHandler(future.channel()));  // 添加用于转发的handler
//                    ctx.fireChannelRead(msg.retain()); // 增加引用计数并继续传递
//                    removeCheckHttpHandler(ctx, this.getClass()); // 移除当前处理器

                    log.info("成功连接到远程服务器: {}:{}", targetIp, targetPort);
                } else {
                    log.info("连接到远程服务器失败: {}:{}", targetIp, targetPort);
                }
            });
        } catch (Exception e) {
            log.error("连接到远程服务器时发生错误",e);
        } finally {
            group.shutdownGracefully();
        }
    }

    private static FullHttpRequest createFullHttpRequest(String host,int port,String path) {
        FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "https://" + host + ":" + port + path);
        request.headers().set("Host", host);
        request.content().writeBytes(FullHttpRequest.EMPTY_LAST_CONTENT.content());
        return request;
    }

    private void removeCheckHttpHandler(ChannelHandlerContext ctx, Class<? extends ChannelHandler> clazz) {
        if (ctx.pipeline().get(clazz) != null){
            log.debug("remove check http handler");
            ctx.pipeline().remove(clazz);
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
        log.error("异常", cause);
        ctx.close();
    }
}