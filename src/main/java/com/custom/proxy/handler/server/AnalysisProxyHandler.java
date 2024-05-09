package com.custom.proxy.handler.server;

import com.custom.proxy.handler.RelayHandler;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import lombok.extern.slf4j.Slf4j;

import javax.net.ssl.SSLException;

@Slf4j
public class AnalysisProxyHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) {

        log.info("Request Method: {}, URI: {}, Headers: {}", request.method(), request.uri(), request.headers());
        String host;
        int port;
        //携带特定的头部信息表示客户端代理的请求
        if (request.headers().contains("X-Target-Host")){
            host = request.headers().get("X-Target-Host");
            port = request.headers().getInt("X-Target-Port");
            // 将请求方法改回为CONNECT | 目前暂时没用到, 后续若涉及请求处理时可以添加
            request.setMethod(HttpMethod.CONNECT);
            request.setUri((port == 443 ? "https" : "http") + "://" + host + ":" + port);
        }
        else {
            String[] hostPort = request.uri().split(":");
            host = hostPort[0];
            port = hostPort.length == 2 ? Integer.parseInt(hostPort[1]) : 80;
        }
        handleConnectRequest(ctx, request, host, port);
    }

    private void handleConnectRequest(ChannelHandlerContext ctx, FullHttpRequest request,String host,Integer port) {
        // 建立与目标服务器的连接
        Bootstrap b = new Bootstrap();
        b.group(ctx.channel().eventLoop())
                .channel(ctx.channel().getClass())
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        // 仅添加用于转发的handler,代理服务端无需SSL处理，因为握手过程处理交由代理客户端处理
                        ch.pipeline().addLast(new RelayHandler(ctx.channel()));
                    }
                });

        ChannelFuture connectFuture = b.connect(host, port);
        connectFuture.addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                log.info("Connected to target server");
                FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
                ctx.writeAndFlush(response);
                // 移除HTTP处理器并设置透明转发
                ctx.pipeline().remove(HttpServerCodec.class);
                ctx.pipeline().remove(HttpObjectAggregator.class);
                ctx.pipeline().remove(this.getClass());  // 移除当前处理器
                ctx.pipeline().addLast(new RelayHandler(future.channel()));  // 添加用于转发的handler
            } else {
                // 连接失败，向客户端发送 500 错误
                ctx.writeAndFlush(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR));
//                ctx.close();
            }
        });
    }
}
