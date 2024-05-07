package com.custom.proxy.handler.client;

import com.custom.proxy.handler.RelayHandler;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

@Slf4j
public class FillProxyHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private final String remoteHost;
    private final int remotePort;

    public FillProxyHandler(String remoteHost, int remotePort) {
        this.remoteHost = remoteHost;
        this.remotePort = remotePort;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) {
        if (request.method() == HttpMethod.CONNECT) {
            log.info("Received CONNECT request: {}", request.uri());
            // 构建新的请求转发到服务端
            FullHttpRequest forwardRequest = new DefaultFullHttpRequest(
                    request.protocolVersion(), HttpMethod.POST, request.uri());
            forwardRequest.headers().set(request.headers());

            String host = request.uri().substring(0, request.uri().indexOf(":"));
            Integer port = Integer.parseInt(request.uri().substring(request.uri().indexOf(":") + 1));
            forwardRequest.headers().add("X-Target-Host", host);
            forwardRequest.headers().add("X-Target-Port", port);

            // 连接到服务端
            Bootstrap b = new Bootstrap();
            b.group(ctx.channel().eventLoop())
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            // 仅添加用于转发的handler，不处理SSL，因为它是由客户端和目标服务器协商的
                            ch.pipeline().addLast(new RelayHandler(ctx.channel()));
                        }
                    });

            ChannelFuture connectFuture = b.connect(remoteHost, remotePort);
            connectFuture.addListener((ChannelFutureListener) future -> {
                if (future.isSuccess()) {
                    //临时添加转发的http解析器，用于转发请求
                    future.channel().pipeline().addLast(new HttpClientCodec());
                    future.channel().pipeline().addLast(new HttpObjectAggregator(65536));
                    //发送修改的请求
                    future.channel().writeAndFlush(forwardRequest);
                    //释放临时添加转发的http解析器
                    future.channel().pipeline().remove(HttpClientCodec.class);
                    future.channel().pipeline().remove(HttpObjectAggregator.class);
                    future.channel().pipeline().addLast(new RelayHandler(future.channel()));
                    //释放该请求的全局监听的http解析器,到此转换为SSL隧道的TCP通信模式
                    ctx.pipeline().remove(HttpServerCodec.class);
                    ctx.pipeline().remove(HttpObjectAggregator.class);
                    ctx.pipeline().remove(this.getClass());  // 移除当前处理器
                    ctx.pipeline().addLast(new RelayHandler(future.channel()));  // 添加用于转发的handler
                    log.info("send connect request to post");
                } else {
                    ctx.writeAndFlush(new DefaultFullHttpResponse(
                            HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR));
                }
            });
        } else {
            // 直接转发其他请求
            ctx.fireChannelRead(request.retain());
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (cause instanceof IOException && cause.getMessage().contains("Connection reset")) {
            log.info("Connection was reset by the peer");
        } else {
            log.error("Error occurred in FillProxyHandler", cause);
        }
        ctx.close();
    }

}
