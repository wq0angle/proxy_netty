package com.custom.proxy.handler.server;

import com.custom.proxy.entity.TargetConnectDTO;
import com.custom.proxy.handler.RelayHandler;
import com.custom.proxy.handler.RelayWebSocketHandler;
import com.custom.proxy.util.WebSocketUtil;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.util.CharsetUtil;
import io.netty.util.internal.StringUtil;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.time.LocalDate;

@Slf4j
public class AnalysisWebSocketProxyHandler extends SimpleChannelInboundHandler<Object> {
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof FullHttpRequest request) {
            log.info("Request Method: {}, URI: {}, Headers: {}", request.method(), request.uri(), request.headers());
            TargetConnectDTO targetConnect = new TargetConnectDTO();
            if (request.method() == HttpMethod.CONNECT) {
                String[] urlArr = request.uri().split(":");
                targetConnect.setHost(urlArr[0]);
                targetConnect.setPort(urlArr.length < 2 ? 80 : Integer.parseInt(urlArr[1]));
                handleConnectRequest(ctx, request, targetConnect.getHost(), targetConnect.getPort());
            }
            else {
                forwardRequest(request, targetConnect);
                handleHttpRequest(ctx, request, targetConnect.getHost(), targetConnect.getPort());
            }
        } else if (msg instanceof WebSocketFrame) {
            handleWebSocketFrame(ctx, (WebSocketFrame) msg);
        }
    }

    private void handleConnectRequest(ChannelHandlerContext ctx, FullHttpRequest request, String host, Integer port) {
        // 这里不需要WebSocket客户端握手，因为目标服务器是HTTP服务器
        handleHttpRequest(ctx, request, host, port);
    }

    private void handleHttpRequest(ChannelHandlerContext ctx, FullHttpRequest request, String host, Integer port) {
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
                        ch.pipeline().addLast(new RelayWebSocketHandler(ctx.channel()));
                    }
                });

        ChannelFuture connectFuture = b.connect(host, port);
        connectFuture.addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                if (request.method() == HttpMethod.CONNECT) {
                    log.info("Connected to target server");

                    FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
                    response.headers().set("test", "text/plain; charset=UTF-8");
                    // 创建一个WebSocketFrame，将HTTP响应转换为二进制数据
                    WebSocketFrame frame = WebSocketUtil.convertToWebSocketFrame(response);
                    // 写入并刷新到inboundChannel
                    ctx.writeAndFlush(frame);

                    // 移除HTTP处理器并设置透明转发
                    removeCheckHttpHandler(ctx, HttpServerCodec.class);
                    removeCheckHttpHandler(ctx, HttpObjectAggregator.class);
                    removeCheckHttpHandler(ctx, this.getClass());  // 移除当前处理器
                    ctx.pipeline().addLast(new RelayWebSocketHandler(future.channel()));  // 添加用于转发的handler
//                    ctx.pipeline().addLast(new RelayHandler(future.channel()));  // 添加用于转发的handler
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

    private void handleWebSocketFrame(ChannelHandlerContext ctx, WebSocketFrame frame) {
        if (frame instanceof TextWebSocketFrame || frame instanceof BinaryWebSocketFrame) {
            // 处理WebSocket消息并转发到目标HTTP服务器
            String reqStr = ((TextWebSocketFrame) frame).text();
            log.info("WebSocket Frame: {}", reqStr);
            // 假设目标URL在WebSocket消息中
            FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.CONNECT, reqStr);
            TargetConnectDTO targetConnect = new TargetConnectDTO();
            forwardRequest(request, targetConnect);
            handleHttpRequest(ctx, request, targetConnect.getHost(), targetConnect.getPort());
        } else if (frame instanceof CloseWebSocketFrame) {
            ctx.close();
        } else if (frame instanceof PingWebSocketFrame) {
            log.info("WebSocket Frame: {}", frame.content().toString(CharsetUtil.UTF_8));
            ctx.writeAndFlush(new PongWebSocketFrame(frame.content().retain()));
        } else if (frame instanceof PongWebSocketFrame) {
            // Pong frames are ignored
        }
    }

    private void forwardRequest(FullHttpRequest request, TargetConnectDTO targetConnect) {
        String host;
        int port;
        String targetUrl = request.headers().get("X-Target-Url");
        if (!StringUtil.isNullOrEmpty(targetUrl) && targetUrl.split(":").length > 1) {
            // 携带特定的头部信息表示客户端代理过来connect请求
            host = targetUrl.split(":")[0];
            port = Integer.parseInt(targetUrl.split(":")[1]);
            String methodName = request.headers().get("X-Target-Method");
            request.setMethod(HttpMethod.valueOf(methodName));
            request.setUri(targetUrl);
            request.headers().set("Host", host);
            targetConnect.setProxyType(1);
        } else {
            // 不带X-Target-Host头部的请求，返回指定目录的HTML内容 | 一般为get或者post请求,url为地址后面路径
            host = "127.0.0.1";
            port = 5088;
            if (request.uri().equals("/")) {
                request.setUri("/index.html");
            }
            // 修改请求的目标地址为本地HTTP代理的地址和端口
            request.setUri("http://" + host + ":" + port + request.uri());
            targetConnect.setProxyType(0);
        }
        targetConnect.setHost(host);
        targetConnect.setPort(port);
    }

    private static String getWebSocketLocation(FullHttpRequest req) {
        String location = req.headers().get(HttpHeaderNames.HOST) + req.uri();
        return "ws://" + location;
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
            log.info("Connection was reset by the peer");
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
            log.info("server ip : [{}] disconnected", ctx.channel().remoteAddress());
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
        log.info("handlerAdded | server ip : [{}] connected", ctx.channel().remoteAddress());
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