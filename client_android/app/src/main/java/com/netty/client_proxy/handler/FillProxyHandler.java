package com.netty.client_proxy.handler;

import com.netty.client_proxy.entity.ProxyConfigDTO;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import timber.log.Timber;

import java.io.IOException;
import java.util.Objects;

public class FillProxyHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private final String remoteHost;
    private final int remotePort;
    private final SslContext sslContext;
    private ProxyConfigDTO proxyConfigDTO;

    public FillProxyHandler(String remoteHost, int remotePort, ProxyConfigDTO proxyConfigDTO) throws Exception {
        this.remoteHost = remoteHost;
        this.remotePort = remotePort;
        this.proxyConfigDTO = proxyConfigDTO;
        this.sslContext = SslContextBuilder.forClient()
                .protocols("TLSv1.1", "TLSv1.2", "TLSv1.3")
                .ciphers(null)  // 默认使用所有可用的加密套件
                .build();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
        Timber.i("Received request: %s", request);
        handleConnectAndRequest(ctx, request);
    }

    private void handleConnectAndRequest(ChannelHandlerContext ctx, FullHttpRequest request) {

        FullHttpRequest forwardRequest = new DefaultFullHttpRequest(
                request.protocolVersion(), request.method(), request.uri());

        forwardRequest.headers().add(request.headers());
        forwardRequest.headers().add("Proxy-Target-Enable", true);
        forwardRequest.content().writeBytes(request.content()); // 添加请求体
        // 连接到目标服务器
        Bootstrap b = new Bootstrap();
        b.group(ctx.channel().eventLoop())
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) throws Exception {
                        // 仅添加用于转发的handler
                        if (proxyConfigDTO.getSslRequestEnabled()) {
                            ch.pipeline().addLast(sslContext.newHandler(ch.alloc(), remoteHost, remotePort)); // 添加 SSL 处理器
                        }
                        ch.pipeline().addLast(new LoggingHandler(LogLevel.INFO)); // 添加日志处理器，输出 SSL 握手过程中的详细信息
                        ch.pipeline().addLast(new HttpClientCodec());
                        ch.pipeline().addLast(new HttpObjectAggregator(Integer.MAX_VALUE));
                        ch.pipeline().addLast(new RelayHandler(ctx.channel()));
                    }
                });

        ChannelFuture connectFuture = b.connect(remoteHost, remotePort);
        connectFuture.addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                //发送修改的请求
                future.channel().writeAndFlush(forwardRequest);
                //释放临时添加转发的http解析器
                future.channel().pipeline().remove(HttpClientCodec.class);
                future.channel().pipeline().remove(HttpObjectAggregator.class);

                //释放该请求的全局监听的http解析器,不再解析TCP流并透明转发,在connect后面的请求后转换为SSL隧道模式
                ctx.pipeline().remove(HttpServerCodec.class);
                ctx.pipeline().remove(HttpObjectAggregator.class);

                //流处理器替换
                ctx.pipeline().remove(this.getClass());  // 移除当前处理器
                ctx.pipeline().addLast(new RelayHandler(future.channel()));  // 添加用于转发的handler

                Timber.d("send request to post , %s", request.uri());
            } else {
                ctx.writeAndFlush(new DefaultFullHttpResponse(
                        HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR));
            }
        });
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (cause instanceof IOException && Objects.requireNonNull(cause.getMessage()).contains("Connection reset")) {
            Timber.d("Connection was reset by the peer");
        } else {
            Timber.e(cause,"Error occurred in FillProxyHandler");
        }
        ctx.close();
    }
}