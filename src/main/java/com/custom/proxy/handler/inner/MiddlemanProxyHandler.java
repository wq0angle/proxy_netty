package com.custom.proxy.handler.inner;

import com.custom.proxy.handler.HttpRequestHandler;
import com.custom.proxy.handler.RelayHandler;
import com.custom.proxy.provider.CertificateProvider;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import lombok.extern.slf4j.Slf4j;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

@Slf4j
public class MiddlemanProxyHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
    private final String remoteHost;
    private final int remotePort;
    private final SslContext sslContext;

    public MiddlemanProxyHandler(String remoteHost, int remotePort) throws Exception {
        this.remoteHost = remoteHost;
        this.remotePort = remotePort;
        this.sslContext = SslContextBuilder.forClient()
                .protocols("TLSv1.1", "TLSv1.2", "TLSv1.3")
                .ciphers(null)  // 默认使用所有可用的加密套件
                .build();
    }
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
        if (request.method() == HttpMethod.CONNECT) {
            log.info("Received CONNECT request: {}", request.uri());
            showHandleConnect(ctx, request);
        } else {
            // 直接转发其他请求
            ctx.fireChannelRead(request.retain());
        }
    }
    private void showHandleConnect(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
        String host = request.uri().substring(0, request.uri().indexOf(":"));
        int port = Integer.parseInt(request.uri().substring(request.uri().indexOf(":") + 1));

        // 发送 200 OK 响应，表示隧道已建立
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
        ctx.writeAndFlush(response);

        // 移除 HTTP 相关的处理器
        ctx.pipeline().remove(HttpServerCodec.class);
        ctx.pipeline().remove(HttpObjectAggregator.class);

        // 添加 SSL 处理器，用于解密来自客户端的流量
        SslContext sslCtx = CertificateProvider.createTargetSslContext(host);

        ctx.pipeline().addLast(sslCtx.newHandler(ctx.alloc(), host, port));

        // 添加 HTTP 编解码器，用于解析解密后的 HTTP 消息
        int maxContentLength = 1024 * 1024 * 10;
        ctx.pipeline().addLast(new HttpServerCodec());
        ctx.pipeline().addLast(new HttpObjectAggregator(maxContentLength));

        // 添加自定义处理器，用于修改解密后的 HTTP 请求
        ctx.pipeline().addLast(new HttpRequestHandler());

        // 添加用于转发修改后的请求的处理器
//        ctx.pipeline().addLast(new RelayHandler(ctx.channel()));
    }
}
