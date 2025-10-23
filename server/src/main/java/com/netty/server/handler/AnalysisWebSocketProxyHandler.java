package com.netty.server.handler;

import com.alibaba.fastjson.JSON;
import com.netty.common.entity.HttpRequestDTO;
import com.netty.common.enums.ChannelFlowEnum;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;
import lombok.extern.slf4j.Slf4j;
import com.netty.common.util.WebSocketUtil;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.LocalDate;

@Slf4j
public class AnalysisWebSocketProxyHandler extends SimpleChannelInboundHandler<WebSocketFrame> {

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame msg) throws URISyntaxException {
        if (msg != null) {
            handleWebSocketFrame(ctx, msg);
        } else {
            log.info("检测到WebSocket帧请求为空");
        }
    }

    private void handleHttpRequest(ChannelHandlerContext ctx, String host, Integer port, FullHttpRequest httpRequest) {
        String uri = httpRequest.uri();

        Bootstrap b = new Bootstrap();
        b.group(ctx.channel().eventLoop())
                .channel(NioSocketChannel.class)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        // 仅添加用于转发的handler,代理服务端无需SSL处理，因为握手过程处理交由代理客户端处理
//                        ch.pipeline().addLast(new LoggingHandler(LogLevel.INFO));
                        if (httpRequest.method() != HttpMethod.CONNECT) {
                            // 添加HTTP处理器
                            ch.pipeline().addLast(new HttpClientCodec());
                            ch.pipeline().addLast(new HttpObjectAggregator(Integer.MAX_VALUE));
                        }
                        ch.pipeline().addLast(new FramePackRelayHandler(ctx.channel(), ChannelFlowEnum.LOCAL_CHANNEL_FLOW));
                    }
                });

        ChannelFuture connectFuture = b.connect(host, port);
        connectFuture.addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                if (httpRequest.method() == HttpMethod.CONNECT) {
                    log.info("Connected to target server | uri:{}", uri);

                    FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
                    response.headers().set("proxy", "text/plain; charset=UTF-8");
                    // 创建一个WebSocketFrame，将HTTP响应(对应 connect请求 的 response响应 -> 200 ok)转换为文本帧数据
                    WebSocketFrame frame = WebSocketUtil.convertToTextWebSocketFrame(response);
//                    WebSocketFrame frame = WebSocketUtil.convertToBinaryWebSocketFrame(response);
                    // 写入并刷新到inboundChannel
                    ctx.writeAndFlush(frame);

                    /*
                    释放该请求的全局监听的http解析器,不再解析TCP流并透明转发后续生命周期内的所有请求
                    如果为加密https请求，在完成服务端代理的connect请求回写后,转为SSL隧道模式,与代理客户端同理
                    只作为代理桥接器使用，不会涉及到请求的操作，如解密请求或过滤请求，当然在websocket中多了一步帧转化的操作
                     */
                    removeCheckHttpHandler(ctx, HttpServerCodec.class);
                    removeCheckHttpHandler(ctx, HttpObjectAggregator.class);

                } else {
                    log.info("Request to target server | uri:{}",uri);

                    future.channel().writeAndFlush(httpRequest);
                }

                // 流处理器替换,不再涉及请求的操作,只透明转发请求
                removeCheckHttpHandler(ctx, this.getClass());  // 移除当前处理器
                // 添加用于透明转发的handler,不过会涉及到加密流和websocket帧的相互转换
                ctx.pipeline().addLast(new FramePackRelayHandler(future.channel(), ChannelFlowEnum.FUTURE_CHANNEL_FLOW));
            } else {
                // 连接失败，向客户端发送 500 错误
                ctx.writeAndFlush(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR));
                ctx.close();
            }
        });
    }

    private void handleWebSocketFrame(ChannelHandlerContext ctx, WebSocketFrame frame) throws URISyntaxException {
        if (frame instanceof TextWebSocketFrame) {
            // 处理WebSocket消息并转发到目标HTTP服务器
            String reqStr = ((TextWebSocketFrame) frame).text();

            HttpRequestDTO httpRequest = JSON.parseObject(reqStr, HttpRequestDTO.class);
            FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.valueOf(httpRequest.getVersion()),
                    HttpMethod.valueOf(httpRequest.getMethod()), httpRequest.getUri(), Unpooled.wrappedBuffer(httpRequest.getContent()));
            httpRequest.getHeaders().forEach((key, value) -> request.headers().add(key, value));
            log.info("WebSocket Frame: {}", reqStr);

            String host;
            int port ;
            if (request.method() == HttpMethod.CONNECT){
                // 若为 connect请求,url只有域名+端口,不会携带 http:// 或 https:// 的前缀，如：www.baidu.com:443
                String[] urlArr = request.uri().split(":");
                host = urlArr[0];
                port = urlArr.length < 2 ? 80 :Integer.parseInt(urlArr[1]);
            }else {
                // 非 connect请求 只会存在解密的 https请求 或 http请求 这两种情况,会携带 https:// 或 http:// 前缀,转为 URI对象 处理
                URI uri = new URI(request.uri());
                host = uri.getHost();
                port = uri.getPort();
                // 如果端口未指定，则返回 -1
                if (port == -1) {
                    port = uri.getScheme().equals("https") ? 443 : 80; // 默认端口
                }
            }

            // HTTP请求处理及转发
            handleHttpRequest(ctx, host, port, request);
            ReferenceCountUtil.release(request);

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

    public static void main(String[] args) {
//        String str = "{\"content\":\"\",\"headers\":{\"content-length\":\"0\",\"User-Agent\":\"Dalvik/2.1.0 (Linux; U; Android 14; sdk_gphone64_x86_64 Build/UE1A.230829.036.A4)\",\"Host\":\"google.com:443\",\"Proxy-Connection\":\"Keep-Alive\"},\"method\":\"CONNECT\",\"uri\":\"google.com:443\",\"version\":\"HTTP/1.1\"}";
        String str1 = "{\"content\":[],\"headers\":{\"content-length\":\"0\",\"User-Agent\":\"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0.0.0 Safari/537.36\",\"Host\":\"fanyi.baidu.com:443\",\"Proxy-Connection\":\"keep-alive\"},\"method\":\"CONNECT\",\"uri\":\"fanyi.baidu.com:443\",\"version\":\"HTTP/1.1\"}";
//        HttpRequestDTO httpRequest = JSON.parseObject(str, HttpRequestDTO.class);
        HttpRequestDTO httpRequest1 = JSON.parseObject(str1, HttpRequestDTO.class);
        System.out.println("成功");
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