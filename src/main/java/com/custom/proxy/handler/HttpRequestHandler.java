package com.custom.proxy.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HttpRequestHandler extends SimpleChannelInboundHandler<HttpObject> {

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, HttpObject msg) throws Exception {
        if (msg instanceof HttpRequest request) {
            // 检查 HTTP 请求类型
            if (HttpMethod.GET.equals(request.method())) {
                // 处理 GET 请求
                handleGetRequest(ctx, request);
            }
        }
    }

    private void handleGetRequest(ChannelHandlerContext ctx, HttpRequest request) {
        // 创建响应
        FullHttpResponse response = new DefaultFullHttpResponse(
                request.protocolVersion(), HttpResponseStatus.OK);

        // 设置响应内容
        String content = "Hello, World!";
        response.content().writeBytes(content.getBytes());
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain");
        response.headers().set(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());

        // 发送响应
        ctx.writeAndFlush(response);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("An exception occurred: {}", cause.getMessage());
        ctx.close();
    }
}
