package com.netty.server.handler;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AnalysisVpnHandler extends SimpleChannelInboundHandler<ByteBuf> {

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf buffer) {
        // 解析PPTP控制消息
        int messageType = buffer.getByte(1); // 假设消息类型在第二个字节
        switch (messageType) {
            case 1: // 连接请求
                handleConnectionRequest(ctx, buffer);
                break;
            case 2: // 断开请求
                handleDisconnectRequest(ctx, buffer);
                break;
            // 处理其他PPTP消息类型
            default:
                log.info("未知消息类型: {}", messageType);
        }
    }

    private void handleConnectionRequest(ChannelHandlerContext ctx, ByteBuf buffer) {
        // 处理连接请求逻辑
        log.info("处理连接请求");

        // 解析连接请求的其他字段（如会话ID、控制消息类型等）
        int sessionId = buffer.getShort(4); // 假设会话ID在第5和第6个字节

        // 构建响应
        ByteBuf response = ctx.alloc().buffer();
        response.writeByte(0); // 响应类型
        response.writeShort(sessionId); // 回写会话ID
        // 添加其他响应数据...

        ctx.writeAndFlush(response);
    }

    private void handleDisconnectRequest(ChannelHandlerContext ctx, ByteBuf buffer) {
        // 处理断开请求逻辑
        log.info("处理断开请求");

        // 解析断开请求的其他字段（如会话ID等）
        int sessionId = buffer.getShort(4); // 假设会话ID在第5和第6个字节

        // 构建响应
        ByteBuf response = ctx.alloc().buffer();
        response.writeByte(1); // 响应类型
        response.writeShort(sessionId); // 回写会话ID
        // 添加其他响应数据...

        ctx.writeAndFlush(response);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("异常", cause);
        ctx.close();
    }
}