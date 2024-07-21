package com.netty.server.handler;

import com.netty.server.config.AppConfig;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ProxyLoaderHandler extends SimpleChannelInboundHandler<Object> {
    private AppConfig appConfig;

    public ProxyLoaderHandler(AppConfig appConfig) {
        this.appConfig = appConfig;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        switch (msg) {
            case FullHttpRequest request:
                // 检查是否是WebSocket握手请求
                if (isWebSocketUpgradeRequest(request)) {
                    // 如果是WebSocket请求，则添加WebSocket相关的处理器
                    ctx.pipeline().addLast(new WebSocketServerProtocolHandler("/websocket")); // websocket握手路径
                    ctx.pipeline().addLast(new AnalysisWebSocketProxyHandler()); // WebSocket帧处理器
                } else {
                    // 如果不是WebSocket请求，按照HTTP请求处理
                    ctx.pipeline().addLast(new AnalysisProxyHandler(appConfig));
                }
                ctx.fireChannelRead(request.retain()); // 增加引用计数并继续传递
                break;
            /**
             * 目前只有http监听请求,后面尝试增加vpn入口监听及vpn的tcp代理
             */
            default:
                log.error("ProxyLoaderHandler error");
        }

        removeCheckHttpHandler(ctx, this.getClass());
    }

    // 方法用于检查是否是WebSocket升级请求
    private boolean isWebSocketUpgradeRequest(FullHttpRequest request) {
        HttpHeaders headers = request.headers();
        return "websocket".equalsIgnoreCase(headers.get(HttpHeaderNames.UPGRADE)) &&
                "Upgrade".equalsIgnoreCase(headers.get(HttpHeaderNames.CONNECTION)) &&
                headers.contains(HttpHeaderNames.SEC_WEBSOCKET_KEY) &&
                "13".equals(headers.get(HttpHeaderNames.SEC_WEBSOCKET_VERSION));
    }

    private void removeCheckHttpHandler(ChannelHandlerContext ctx, Class<? extends ChannelHandler> clazz) {
        if (ctx.pipeline().get(clazz) != null){
            log.debug("remove check http handler");
            ctx.pipeline().remove(clazz);
        }
    }

}
