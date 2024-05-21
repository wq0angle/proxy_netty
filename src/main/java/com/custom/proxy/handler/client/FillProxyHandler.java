package com.custom.proxy.handler.client;

import com.custom.proxy.handler.HttpRequestHandler;
import com.custom.proxy.handler.RelayHandler;
import com.custom.proxy.provider.CertificateProvider;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import lombok.extern.slf4j.Slf4j;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import java.io.IOException;

@Slf4j
public class FillProxyHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private final String remoteHost;
    private final int remotePort;
    private final SslContext sslContext;

    public FillProxyHandler(String remoteHost, int remotePort) throws Exception {
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
//            handleConnect(ctx, request);
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
        SSLContext sslCtx = CertificateProvider.getInstance().createTargetSslContext(host);
        SslContext nettySslContext = CertificateProvider.getInstance().convertToNettySslContext(sslCtx);

        ctx.pipeline().addLast(nettySslContext.newHandler(ctx.alloc()));

        // 添加 HTTP 编解码器，用于解析解密后的 HTTP 消息
        int maxContentLength = 1024 * 1024 * 10;
        ctx.pipeline().addLast(new HttpServerCodec());
        ctx.pipeline().addLast(new HttpObjectAggregator(maxContentLength));

        // 添加自定义处理器，用于修改解密后的 HTTP 请求
        ctx.pipeline().addLast(new HttpRequestHandler());

        // 添加用于转发修改后的请求的处理器
//        ctx.pipeline().addLast(new RelayHandler(ctx.channel()));
    }

    private void handleConnect(ChannelHandlerContext ctx, FullHttpRequest request) {
        // 解析目标主机和端口
        String host = request.uri().substring(0, request.uri().indexOf(":"));
        int port = Integer.parseInt(request.uri().substring(request.uri().indexOf(":") + 1));

        // 构建新请求转发到服务端 | 隐藏url
        FullHttpRequest forwardRequest = new DefaultFullHttpRequest(
                request.protocolVersion(), HttpMethod.POST, "/proxy");

        //connect请求临时改为POST请求,携带host信息到请求头
        forwardRequest.headers().add("X-Target-Url", request.uri());
        forwardRequest.headers().set("Host", remoteHost);
        forwardRequest.headers().set("X-Target-Method", request.method().name());
        forwardRequest.content().writeBytes(request.content()); // 添加请求体

        int maxContentLength = 1024 * 1024 * 10;
        // 连接到目标服务器
        Bootstrap b = new Bootstrap();
        b.group(ctx.channel().eventLoop())
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        // 仅添加用于转发的handler
                        ch.pipeline().addLast(sslContext.newHandler(ch.alloc(), host, remotePort)); // 添加 SSL 处理器
                        ch.pipeline().addLast();
                        ch.pipeline().addLast(new LoggingHandler(LogLevel.INFO)); // 添加日志处理器，输出 SSL 握手过程中的详细信息
                        ch.pipeline().addLast(new RelayHandler(ctx.channel()));
                    }
                });

        ChannelFuture connectFuture = b.connect(remoteHost, remotePort);
        connectFuture.addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
                ctx.writeAndFlush(response);
                ctx.pipeline().addLast(new RelayHandler(future.channel()));  // 添加用于转发的handler
                //临时添加转发的http解析器，用于转发请求
                future.channel().pipeline().addLast(new HttpClientCodec());
                future.channel().pipeline().addLast(new HttpObjectAggregator(maxContentLength));
                //发送修改的请求
                future.channel().writeAndFlush(forwardRequest);
                //释放临时添加转发的http解析器
                future.channel().pipeline().remove(HttpClientCodec.class);
                future.channel().pipeline().remove(HttpObjectAggregator.class);
                future.channel().pipeline().addLast(new RelayHandler(future.channel()));
                //释放该请求的全局监听的http解析器,透明转发请求,在connect后面的请求彻底转换为SSL隧道的TCP通信模式
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