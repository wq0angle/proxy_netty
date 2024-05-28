package com.custom.proxy.handler.client;

import com.custom.proxy.handler.RelayHandler;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.ChannelInputShutdownReadComplete;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.incubator.codec.quic.*;
import io.netty.util.CharsetUtil;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

@Slf4j
public class H3ProxyClientHandler {

    public static void main(String[] args) throws Exception {
        start(6666, "127.0.0.1", 5066);
    }

    public static void start(int localPort, String remoteHost, int remotePort) throws Exception {
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup();

        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .handler(new LoggingHandler(LogLevel.INFO))
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) throws Exception {
                            ChannelPipeline p = ch.pipeline();
                            p.addLast(new HttpServerCodec());
                            p.addLast(new HttpObjectAggregator(1024 * 1024 * 10));
                            p.addLast(new CdnFillProxyHandler(remoteHost, remotePort));
                        }
                    });

            Channel ch = b.bind(localPort).sync().channel();
            log.info("HTTP/3 proxy client started, listening on port: {}", localPort);
            ch.closeFuture().sync();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }
}

@Slf4j
class CdnFillProxyHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private final String remoteHost;
    private final int remotePort;

    public CdnFillProxyHandler(String remoteHost, int remotePort) {
        this.remoteHost = remoteHost;
        this.remotePort = remotePort;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
        if (request.method() == HttpMethod.CONNECT) {
            log.info("Received CONNECT request: {}", request.uri());
            handleConnect(ctx, request);
        } else {
            // 直接转发其他请求
            ctx.fireChannelRead(request.retain());
        }
    }

    private void handleConnect(ChannelHandlerContext ctx, FullHttpRequest request) {
        NioEventLoopGroup group = new NioEventLoopGroup();
        try {
            // 创建SSL上下文
            QuicSslContext sslContext = QuicSslContextBuilder.forClient()
                    .trustManager(InsecureTrustManagerFactory.INSTANCE)
                    .applicationProtocols("h3")
                    .build();

            QuicClientCodecBuilder codecBuilder = new QuicClientCodecBuilder()
                    .sslContext(sslContext)
                    .maxIdleTimeout(5000, TimeUnit.MILLISECONDS)
                    .initialMaxData(10000000)
                    .initialMaxStreamDataBidirectionalLocal(1000000)
                    .initialMaxStreamDataBidirectionalRemote(1000000)
                    .initialMaxStreamsBidirectional(100);

            Bootstrap b = new Bootstrap();
            b.group(group)
                    .channel(NioDatagramChannel.class)
                    .handler(new ChannelInitializer<NioDatagramChannel>() {
                        @Override
                        protected void initChannel(NioDatagramChannel ch) {
                            ch.pipeline().addLast(codecBuilder.build());
                        }
                    });

            Channel channel = b.bind(0).sync().channel();

            QuicChannelBootstrap quicBootstrap = QuicChannel.newBootstrap(channel)
                    .handler(new Http3ClientConnectionHandler())
                    .streamHandler(new ChannelInitializer<QuicStreamChannel>() {
                        @Override
                        protected void initChannel(QuicStreamChannel ch) {
//                            ch.pipeline().addLast(new Http3ClientFrameCodec());
//                            ch.pipeline().addLast(new Http3ClientConnectionHandler());
                            ch.pipeline().addLast(new RelayHandler(ctx.channel()));
                        }
                    });

            QuicChannel quicChannel = quicBootstrap
                    .remoteAddress(new InetSocketAddress(remoteHost, remotePort))
                    .connect()
                    .sync()
                    .get();

            QuicStreamChannel streamChannel = quicChannel.createStream(QuicStreamType.BIDIRECTIONAL,
                    new ChannelInboundHandlerAdapter() {
                        @Override
                        public void channelRead(ChannelHandlerContext ctx, Object msg) {
                            ByteBuf byteBuf = (ByteBuf) msg;
                            ctx.channel().writeAndFlush(byteBuf.retain());
                        }

                        @Override
                        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
                            if (evt == ChannelInputShutdownReadComplete.INSTANCE) {
                                ((QuicChannel) ctx.channel().parent()).close(true, 0,
                                        ctx.alloc().directBuffer(16)
                                                .writeBytes(new byte[]{'k', 't', 'h', 'x', 'b', 'y', 'e'}));
                            }
                        }
                    }).sync().getNow();

            streamChannel.writeAndFlush(request.content().retain()).addListener(QuicStreamChannel.SHUTDOWN_OUTPUT);
//            streamChannel.pipeline().remove();

            streamChannel.closeFuture().addListener(future -> {
                quicChannel.close();
                group.shutdownGracefully();
            });
        } catch (Exception e) {
            log.error("Error occurred while handling request", e);
            FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR);
            ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (cause != null && cause.getMessage().contains("Connection reset")) {
            log.info("Connection was reset by the peer");
        } else {
            log.error("Error occurred in CdnFillProxyHandler", cause);
        }
        ctx.close();
    }
}

@Slf4j
class Http3ClientConnectionHandler extends ChannelInboundHandlerAdapter {

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        // 连接成功后可以处理初始化逻辑
        log.info("HTTP/3 connection established");
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        // 处理传入的消息
        log.info("Received message: {}", msg);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        // 错误处理
        log.error("Error in HTTP/3 connection", cause);
        ctx.close();
    }
}

@Slf4j
class Http3ClientStreamInboundHandler extends SimpleChannelInboundHandler<HttpObject> {

    private final Channel clientChannel;

    public Http3ClientStreamInboundHandler(Channel clientChannel) {
        this.clientChannel = clientChannel;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, HttpObject msg) {
        clientChannel.writeAndFlush(msg);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("Error in HTTP/3 stream", cause);
        ctx.close();
    }
}

/**
 * HTTP/3 客户端帧编解码器
 */
class Http3ClientFrameCodec extends ChannelInboundHandlerAdapter {

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof ByteBuf) {
            // 在这里解析 HTTP/3 帧
            ByteBuf buf = (ByteBuf) msg;
            // 简单的打印示例，可以替换为实际的处理逻辑
            System.out.println("Received HTTP/3 frame: " + buf.toString(CharsetUtil.UTF_8));
        }
        ctx.fireChannelRead(msg);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }
}