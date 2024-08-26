package com.netty.server.handler;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class StaticFileServerHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private final File websiteDirectory;

    public StaticFileServerHandler(File websiteDirectory) {
        this.websiteDirectory = websiteDirectory;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
        if (request.method() != HttpMethod.GET) {
            sendError(ctx, HttpResponseStatus.METHOD_NOT_ALLOWED);
            return;
        }

        String uri = request.uri();
        File file = new File(websiteDirectory, uri);

        // 安全性检查：防止目录遍历攻击
        if (!isSafeFile(file)) {
            sendError(ctx, HttpResponseStatus.FORBIDDEN);
            return;
        }

        if (!file.exists() || !file.isFile()) {
            sendError(ctx, HttpResponseStatus.NOT_FOUND);
            return;
        }

        try {
            byte[] content = Files.readAllBytes(file.toPath());
            String contentType = getContentType(file);
            FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, Unpooled.wrappedBuffer(content));
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, contentType);
            response.headers().set(HttpHeaderNames.CONTENT_LENGTH, content.length);
            ctx.writeAndFlush(response);
        } catch (IOException e) {
            sendError(ctx, HttpResponseStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private boolean isSafeFile(File file) {
        try {
            // 获取文件的绝对路径
            Path filePath = file.getCanonicalFile().toPath();
            Path basePath = websiteDirectory.getCanonicalFile().toPath();

            // 检查文件是否在指定的目录内
            return filePath.startsWith(basePath);
        } catch (IOException e) {
            return false;
        }
    }

    private String getContentType(File file) {
        String fileName = file.getName();
        if (fileName.endsWith(".html")) {
            return "text/html; charset=UTF-8";
        } else if (fileName.endsWith(".css")) {
            return "text/css; charset=UTF-8";
        } else if (fileName.endsWith(".js")) {
            return "application/javascript; charset=UTF-8";
        } else if (fileName.endsWith(".png")) {
            return "image/png";
        } else if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
            return "image/jpeg";
        } else if (fileName.endsWith(".gif")) {
            return "image/gif";
        } else {
            return "application/octet-stream"; // 默认类型
        }
    }

    private void sendError(ChannelHandlerContext ctx, HttpResponseStatus status) {
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status);
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }
}