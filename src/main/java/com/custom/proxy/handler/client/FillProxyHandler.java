package com.custom.proxy.handler.client;

import com.custom.proxy.handler.RelayHandler;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.util.CharsetUtil;
import lombok.extern.slf4j.Slf4j;

import javax.net.ssl.SSLException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class FillProxyHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private final String remoteHost;
    private final int remotePort;
    private final SslContext sslContext;


    public FillProxyHandler(String remoteHost, int remotePort) throws SSLException {
        this.remoteHost = remoteHost;
        this.remotePort = remotePort;
        this.sslContext = SslContextBuilder.forClient()
                .protocols("TLSv1.1","TLSv1.2","TLSv1.3")
                .build();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) {
        if (request.method() == HttpMethod.CONNECT) {
            log.info("Received CONNECT request: {}", request.uri());
            int maxContentLength = 1024 * 1024 * 10;
            // 构建新请求转发到服务端 | 隐藏url
            FullHttpRequest forwardRequest = new DefaultFullHttpRequest(
                    request.protocolVersion(), HttpMethod.POST, "/proxy");
            forwardRequest.headers().set(request.headers());

            //connect请求临时改为POST请求,携带host信息到请求头
            String host = request.uri().substring(0, request.uri().indexOf(":"));
            Integer port = Integer.parseInt(request.uri().substring(request.uri().indexOf(":") + 1));
            forwardRequest.headers().add("X-Target-Host", host);
            forwardRequest.headers().add("X-Target-Port", port);
            forwardRequest.headers().set("Host", remoteHost);
            forwardRequest.content().writeBytes(Unpooled.EMPTY_BUFFER); // 添加空请求体

            // 连接到服务端
            Bootstrap b = new Bootstrap();
            b.group(ctx.channel().eventLoop())
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            // 仅添加用于转发的handler
                            ch.pipeline().addLast(sslContext.newHandler(ch.alloc(), remoteHost, remotePort)); // 添加 SSL 处理器
                            ch.pipeline().addLast(new LoggingHandler(LogLevel.INFO)); // 添加日志处理器，输出 SSL 握手过程中的详细信息
                            ch.pipeline().addLast(new RelayHandler(ctx.channel()));
                        }
                    });

            ChannelFuture connectFuture = b.connect(remoteHost, remotePort);
            connectFuture.addListener((ChannelFutureListener) future -> {
                if (future.isSuccess()) {
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
        } else {
            // 直接转发其他请求
            ctx.fireChannelRead(request.retain());
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (cause instanceof IOException && cause.getMessage().contains("Connection reset")) {
            //客户端关闭连接
            log.info("Connection was reset by the peer");
        } else {
            //其他过程异常输出到日志
            log.error("Error occurred in FillProxyHandler", cause);
        }
        ctx.close();
    }

}
