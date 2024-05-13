package com.custom.proxy.handler.server;

import com.custom.proxy.handler.RelayHandler;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.util.CharsetUtil;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;

@Slf4j
public class AnalysisProxyHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) {

        log.info("Request Method: {}, URI: {}, Headers: {}", request.method(), request.uri(), request.headers());
        String host;
        int port;

        if (request.headers().contains("X-Target-Host")){
            //携带特定的头部信息表示客户端代理过来connect请求
            host = request.headers().get("X-Target-Host");
            port = request.headers().getInt("X-Target-Port");
            // 将请求方法改回为CONNECT
            request.setMethod(HttpMethod.CONNECT);
            //connect请求的url一般为 host + port,不过在代理转发的过程中不重要了
            request.setUri((port == 443 ? "https" : "http") + "://" + host + ":" + port);
            request.headers().set("Host", host);
        }
        else {
            // 不带X-Target-Host头部的请求，返回指定目录的HTML内容 | 一般为get或者post请求,url为地址后面路径
            host = "127.0.0.1";
            port = 5088;
            if(request.uri().equals("/")){
                request.setUri("/index.html");
            }
            // 修改请求的目标地址为本地HTTP代理的地址和端口
            request.setUri("http://" + host + ":" + port + request.uri());
        }
        handleConnectRequest(ctx, request, host, port);
    }

    private void handleConnectRequest(ChannelHandlerContext ctx, FullHttpRequest request,String host,Integer port) {
        Integer maxContentLength = 1024 * 1024 * 10;
        // 建立与目标服务器的连接
        Bootstrap b = new Bootstrap();
        b.group(ctx.channel().eventLoop())
                .channel(ctx.channel().getClass())
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        if (request.method() != HttpMethod.CONNECT) {
                            // 添加HTTP处理器
                            ch.pipeline().addLast(new HttpClientCodec());
                            ch.pipeline().addLast(new HttpObjectAggregator(maxContentLength));
                        }
                        // 仅添加用于转发的handler,代理服务端无需SSL处理，因为握手过程处理交由代理客户端处理
                        ch.pipeline().addLast(new RelayHandler(ctx.channel()));
                    }
                });

        ChannelFuture connectFuture = b.connect(host, port);
        connectFuture.addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                if (request.method() == HttpMethod.CONNECT) {
                    log.info("Connected to target server");
                    FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
                    ctx.writeAndFlush(response);
                    // 移除HTTP处理器并设置透明转发
                    ctx.pipeline().remove(HttpServerCodec.class);
                    ctx.pipeline().remove(HttpObjectAggregator.class);
                    ctx.pipeline().remove(this.getClass());  // 移除当前处理器
                    ctx.pipeline().addLast(new RelayHandler(future.channel()));  // 添加用于转发的handler
                }else {
                    log.info("request body to target server");
                    // 构建新请求转发到服务端
                    FullHttpRequest forwardRequest = new DefaultFullHttpRequest(
                            request.protocolVersion(), request.method(), request.uri());
                    forwardRequest.headers().set(request.headers());
                    future.channel().writeAndFlush(forwardRequest);
                }

            } else {
                // 连接失败，向客户端发送 500 错误
                ctx.writeAndFlush(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR));
                ctx.close();
            }
        });
    }

}
