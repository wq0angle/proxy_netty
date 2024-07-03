package com.netty.server.handler;

import com.netty.common.enums.ChannelFlowEnum;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.util.CharsetUtil;
import lombok.extern.slf4j.Slf4j;
import com.netty.common.util.WebSocketUtil;
import java.io.IOException;
import java.time.LocalDate;

@Slf4j
public class AnalysisWebSocketProxyHandler extends SimpleChannelInboundHandler<WebSocketFrame> {

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame msg) {
        if (msg != null) {
            handleWebSocketFrame(ctx, msg);
        } else {
            log.info("检测到WebSocket帧请求为空");
        }
    }

    private void handleHttpRequest(ChannelHandlerContext ctx, String host, Integer port) {
        Integer maxContentLength = 1024 * 1024 * 10;
        Bootstrap b = new Bootstrap();
        b.group(ctx.channel().eventLoop())
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        // 仅添加用于转发的handler,代理服务端无需SSL处理，因为握手过程处理交由代理客户端处理
//                        ch.pipeline().addLast(new LoggingHandler(LogLevel.INFO));
                        ch.pipeline().addLast(new FramePackRelayHandler(ctx.channel(), ChannelFlowEnum.LOCAL_CHANNEL_FLOW));
                    }
                });

        ChannelFuture connectFuture = b.connect(host, port);
        connectFuture.addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                    log.info("Connected to target server");

                    FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
                    response.headers().set("proxy", "text/plain; charset=UTF-8");
                    // 创建一个WebSocketFrame，将HTTP响应转换为文本帧数据
                    WebSocketFrame frame = WebSocketUtil.convertToTextWebSocketFrame(response);
//                    WebSocketFrame frame = WebSocketUtil.convertToBinaryWebSocketFrame(response);
                    // 写入并刷新到inboundChannel
                    ctx.writeAndFlush(frame);

                    // 移除HTTP处理器并设置透明转发
                    removeCheckHttpHandler(ctx, HttpServerCodec.class);
                    removeCheckHttpHandler(ctx, HttpObjectAggregator.class);

                    // 流处理器替换
                    removeCheckHttpHandler(ctx, this.getClass());  // 移除当前处理器
                    ctx.pipeline().addLast(new FramePackRelayHandler(future.channel(),ChannelFlowEnum.FUTURE_CHANNEL_FLOW));

            } else {
                // 连接失败，向客户端发送 500 错误
                ctx.writeAndFlush(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR));
                ctx.close();
            }
        });
    }

    private void handleWebSocketFrame(ChannelHandlerContext ctx, WebSocketFrame frame) {
        if (frame instanceof TextWebSocketFrame) {
            // 处理WebSocket消息并转发到目标HTTP服务器
            String reqStr = ((TextWebSocketFrame) frame).text();
            log.info("WebSocket Frame: {}", reqStr);
            if (reqStr.contains("http://")) {
                reqStr = reqStr.replace("http://", "");
            }
            // 假设目标URL在WebSocket消息中
            String[] urlArr = reqStr.split(":");
            String host = urlArr[0];
            Integer port = urlArr.length > 1 ? Integer.parseInt(urlArr[1]) : 80;
            handleHttpRequest(ctx, host, port);
        } else if (frame instanceof BinaryWebSocketFrame) {
            log.debug("WebSocket Frame: {}", frame.content().toString(CharsetUtil.UTF_8));
        } else if (frame instanceof CloseWebSocketFrame) {
            ctx.close();
        } else if (frame instanceof PingWebSocketFrame) {
            log.debug("WebSocket Frame: {}", frame.content().toString(CharsetUtil.UTF_8));
            ctx.writeAndFlush(new PongWebSocketFrame(frame.content().retain()));
        } else if (frame instanceof PongWebSocketFrame) {
            // Pong frames are ignored
        }
    }

    private void removeCheckHttpHandler(ChannelHandlerContext ctx, Class<? extends ChannelHandler> clazz) {
        if (ctx.pipeline().get(clazz) != null){
            log.debug("remove check http handler");
            ctx.pipeline().remove(clazz);
        }
    }

    /**
     *  连接成功， 此时通道是活跃的时候触发
     *  @param ctx
     */
    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        LocalDate today = LocalDate.now();
        String dateStr = today.toString(); // 默认格式为 "yyyy-MM-dd"
        ctx.writeAndFlush("welcome to server-- now :" + dateStr + "\r\n");
    }

    /**
     * 异常处理
     * @param ctx
     * @param cause
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (cause instanceof IOException && cause.getMessage().contains("Connection reset")) {
            log.debug("Connection was reset by the peer");
        } else {
            log.error("Error occurred in AnalysisWebSocketProxyHandler", cause);
        }
        ctx.close();
    }

    /**
     *  通道不活跃 ，用于处理用户下线的逻辑
     * @param ctx
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        if (ctx.channel().isActive()) {
            log.debug("server ip : [{}] disconnected", ctx.channel().remoteAddress());
            ctx.close();
        }
    }

    /**
     *
     * @param ctx 通道处理器上下文
     * 连接刚刚建立时 ，第一个被执行的方法，
     */
    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        log.debug("handlerAdded | server ip : [{}] connected", ctx.channel().remoteAddress());
    }

    /**
     *
     * @param ctx  通道处理器上下文
     * 当连接断开 最后执行的方法
     * 连接断开时 ， channel 会自动从 通道组中移除
     */
//    @Override
//    public void handlerRemoved(ChannelHandlerContext ctx) {
//        log.info("handlerRemoved | server ip : [{}] disconnected", ctx.channel().remoteAddress());
//    }
}