package com.netty.client.gui.handler;

import com.netty.client.gui.controller.MainController;
import com.netty.client.gui.entity.AppConfig;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

@Slf4j
public class LocalProxyHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private AppConfig appConfig;

    public LocalProxyHandler(AppConfig appConfig) throws Exception {
        this.appConfig = appConfig;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
        log.info("passing proxy {} request: {}", request.method(), request.uri());
        MainController.appendToConsole("passing proxy request : " + request.uri() + "\n");
        handleLocal(ctx, request);
    }

    private void handleLocal(ChannelHandlerContext ctx, FullHttpRequest request) throws URISyntaxException {
        String host;
        int port;
        String[] urlArr = request.uri().split(":");
        if (request.method() == HttpMethod.CONNECT){
            host = urlArr[0];
            port = urlArr.length < 2 ? 80 :Integer.parseInt(urlArr[1]);
        }else {
            URI uri = new URI(request.uri());
            host = uri.getHost();
            port = uri.getPort();
            if (port == -1) {
                port = uri.getScheme().equals("https") ? 443 : 80;
            }
        }
        Bootstrap b = new Bootstrap();
        b.group(ctx.channel().eventLoop())
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        if (request.method() != HttpMethod.CONNECT) {
                            // 添加HTTP处理器
                            ch.pipeline().addLast(new HttpClientCodec());
                            ch.pipeline().addLast(new HttpObjectAggregator(Integer.MAX_VALUE));
                        }
                        // 仅添加用于转发的handler,代理服务端无需SSL处理，因为握手过程处理交由代理客户端处理
                        ch.pipeline().addLast(new RelayHandler(ctx.channel()));
                    }
                });
        ChannelFuture connectFuture = b.connect(host, port);
        int finalPort = port;
        connectFuture.addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                if (request.method() == HttpMethod.CONNECT) {
                    log.debug("proxy Connected to target server");
                    FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
                    response.headers().set("proxy", "text/plain; charset=UTF-8");
                    ctx.writeAndFlush(response);

                       /*
                        释放该请求的全局监听的http解析器,不再解析TCP流并透明转发后续生命周期内的所有请求
                        如果为加密https请求，在本地回写 200 ok 后直接本地转发请求，相当于本地客户端作为代理服务端进行请求代理转发
                        只作为代理桥接器使用，不会涉及到请求的操作，如解密请求或过滤请求
                         */
                    removeCheckHttpHandler(ctx, HttpServerCodec.class);
                    removeCheckHttpHandler(ctx, HttpObjectAggregator.class);
                }else{
                    log.debug("proxy Request to target server");
                    FullHttpRequest forwardRequest = new DefaultFullHttpRequest(
                            request.protocolVersion(), request.method(), request.uri());
                    forwardRequest.headers().set(request.headers());
                    forwardRequest.content().writeBytes(request.content()); // 添加请求体
                    future.channel().writeAndFlush(forwardRequest);
                }

                // 流处理器替换,不再涉及请求的操作,只透明转发请求
                removeCheckHttpHandler(ctx, this.getClass()); // 移除当前处理器
                ctx.pipeline().addLast(new RelayHandler(future.channel()));  // 添加用于转发的handler

            } else {
                log.error("Http连接至目标服务器失败,{}:{}", host, finalPort);
                MainController.appendToConsole("Http连接至目标服务器失败," + host + ":" + finalPort + "\n");
                // 连接失败，向客户端发送 500 错误
                ctx.writeAndFlush(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR));
                ctx.close();
            }
        });
    }

    private void removeCheckHttpHandler(ChannelHandlerContext ctx, Class<? extends ChannelHandler> clazz) {
        if (ctx.pipeline().get(clazz) != null){
            log.debug("remove check http handler");
            ctx.pipeline().remove(clazz);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (cause instanceof IOException && cause.getMessage().contains("Connection reset")) {
            log.debug("Connection was reset by the peer");
        } else {
            log.error("Error occurred in FillProxyHandler", cause);
            MainController.appendToConsole("Error occurred in FillProxyHandler \n");
        }
        ctx.close();
    }
}