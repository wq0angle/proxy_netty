package com.custom.proxy.handler.server;

import com.custom.proxy.handler.RelayHandler;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import io.netty.incubator.codec.quic.*;
import io.netty.util.CharsetUtil;
import lombok.extern.slf4j.Slf4j;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

@Slf4j
public class H3AnalysisProxyHandler {
    public static void main(String[] args) throws Exception {
        H3AnalysisProxyHandler.start(5066);
    }
    public static void start(int port) throws Exception {
        SelfSignedCertificate selfSignedCertificate = new SelfSignedCertificate();
        QuicSslContext context = QuicSslContextBuilder.forServer(
                        selfSignedCertificate.privateKey(), null, selfSignedCertificate.certificate())
                .applicationProtocols("h3").build();

        NioEventLoopGroup group = new NioEventLoopGroup();
        try {
            QuicServerCodecBuilder codecBuilder = new QuicServerCodecBuilder()
                    .sslContext(context)
                    .maxIdleTimeout(5000, TimeUnit.MILLISECONDS)
                    .initialMaxData(10000000)
                    .initialMaxStreamDataBidirectionalLocal(1000000)
                    .initialMaxStreamDataBidirectionalRemote(1000000)
                    .initialMaxStreamsBidirectional(100)
                    .initialMaxStreamsUnidirectional(100)
                    .tokenHandler(InsecureQuicTokenHandler.INSTANCE)
                    .handler(new ChannelInboundHandlerAdapter() {
                        @Override
                        public void channelActive(ChannelHandlerContext ctx) {
                            QuicChannel channel = (QuicChannel) ctx.channel();
                        }

                        @Override
                        public void channelInactive(ChannelHandlerContext ctx) {
                            ((QuicChannel) ctx.channel()).collectStats().addListener(future -> {
                                if (future.isSuccess()) {
                                    System.out.println("Connection closed: " + future.getNow());
                                }
                            });
                        }

                        @Override
                        public boolean isSharable() {
                            return true;
                        }
                    })
                    .streamHandler(new ChannelInitializer<QuicStreamChannel>() {
                        @Override
                        protected void initChannel(QuicStreamChannel ch) {
                            ch.pipeline().addLast(new Http3ServerFrameCodec());
                            ch.pipeline().addLast(new Http3ServerStreamInboundHandler());
                            ch.pipeline().addLast(new RelayHandler(ch));
                        }
                    });

            Bootstrap bs = new Bootstrap();
            Channel channel = bs.group(group)
                    .channel(NioDatagramChannel.class)
                    .handler(codecBuilder.build())
                    .bind(new InetSocketAddress(port)).sync().channel();

            log.info("QUIC server started, listening on port: {}", port);
            channel.closeFuture().sync();
        } finally {
            group.shutdownGracefully();
        }
    }
}

@Slf4j
class Http3ServerStreamInboundHandler extends SimpleChannelInboundHandler<ByteBuf> {

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
        try {
            if (msg.toString(CharsetUtil.US_ASCII).trim().equals("GET /")) {
                ByteBuf buffer = ctx.alloc().directBuffer();
                buffer.writeCharSequence("Hello World! 何亮\r\n", CharsetUtil.UTF_8);
                ctx.writeAndFlush(buffer).addListener(QuicStreamChannel.SHUTDOWN_OUTPUT);
            }
        } finally {
            msg.release();
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("Error in HTTP/3 stream", cause);
        ctx.close();
    }
}

/**
 * HTTP/3 服务端帧编解码器
 */
class Http3ServerFrameCodec extends ChannelInboundHandlerAdapter {

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof ByteBuf buf) {
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