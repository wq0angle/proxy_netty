package com.netty.server.handler;

import com.netty.common.entity.*;
import com.netty.server.config.AppConfig;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.util.internal.StringUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;

@Slf4j
public class AnalysisProxyHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private final AppConfig appConfig;

    public AnalysisProxyHandler(AppConfig appConfig) {
        this.appConfig = appConfig;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) throws URISyntaxException {
        log.info("Request Method: {}, URI: {}, Headers: {}", request.method(), request.uri(), request.headers());
        TargetConnectDTO targetConnect = new TargetConnectDTO();
        String[] urlArr = request.uri().split(":");
        targetConnect.setProxyType(1);
        // 如果是connect方法,表示进行非代理链式请求，作为远程代理直接转发
        if (request.method() == HttpMethod.CONNECT){
            targetConnect.setHost(urlArr[0]);
            targetConnect.setPort(urlArr.length < 2 ? 80 :Integer.parseInt(urlArr[1]));
        }else {
            forwardRequest(request,targetConnect);
        }
        handleConnectRequest(ctx, request, targetConnect);
    }

    private void handleConnectRequest(ChannelHandlerContext ctx, FullHttpRequest request,TargetConnectDTO targetConnect) {
        Integer maxContentLength = 1024 * 1024 * 10;
        Bootstrap b = new Bootstrap();
        b.group(ctx.channel().eventLoop())
                .channel(NioSocketChannel.class)
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

        ChannelFuture connectFuture = b.connect(targetConnect.getHost(), targetConnect.getPort());
        connectFuture.addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                if (targetConnect.getProxyType() != 0) {
                    if (request.method() == HttpMethod.CONNECT) {
                        log.info("proxy Connected to target server");
                        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
                        response.headers().set("proxy", "text/plain; charset=UTF-8");
                        ctx.writeAndFlush(response);

                        // 移除HTTP处理器并设置透明转发
                        removeCheckHttpHandler(ctx, HttpServerCodec.class);
                        removeCheckHttpHandler(ctx, HttpObjectAggregator.class);
                    }else{
                        log.info("proxy request to target server");
                        FullHttpRequest forwardRequest = new DefaultFullHttpRequest(
                                request.protocolVersion(), request.method(), request.uri());
                        forwardRequest.headers().set(request.headers());
                        forwardRequest.content().writeBytes(request.content()); // 添加请求体
                        future.channel().writeAndFlush(forwardRequest);
                    }

                    // 流处理器替换
                    removeCheckHttpHandler(ctx, this.getClass()); // 移除当前处理器
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

    private void forwardRequest(FullHttpRequest request,TargetConnectDTO targetConnect) throws URISyntaxException {
        String proxyEnable = request.headers().get("Proxy-Target-Enable");
        if (StringUtil.isNullOrEmpty(proxyEnable)){
            targetConnect.setProxyType(0);
            targetConnect.setHost("127.0.0.1");
            targetConnect.setPort(appConfig.getWebsitePort());
            if(request.uri().equals("/")){
                request.setUri("/index.html");
            }
        }else {
            URI uri = new URI(request.uri());
            targetConnect.setHost(uri.getHost());
            targetConnect.setPort(uri.getPort());
            // 如果端口未指定，则返回 -1
            if (targetConnect.getPort() == -1) {
                targetConnect.setPort(uri.getScheme().equals("https") ? 443 : 80); // 默认端口
            }
        }
    }

    private void removeCheckHttpHandler(ChannelHandlerContext ctx, Class<? extends ChannelHandler> clazz) {
        if (ctx.pipeline().get(clazz) != null){
            log.debug("remove check http handler");
            ctx.pipeline().remove(clazz);
        }
    }
}