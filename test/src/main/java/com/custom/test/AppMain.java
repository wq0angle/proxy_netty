package com.custom.test;

import com.custom.handler.client.ProxyClientHandler;
import com.custom.handler.server.ProxyServerHandler;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.ReferenceCountUtil;
import org.bouncycastle.jce.X509Principal;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.x509.X509V3CertificateGenerator;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLEngine;
import java.io.IOException;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Date;


public class AppMain {

    public void start(int port) throws Exception {
        EventLoopGroup bossGroup = new NioEventLoopGroup();
        EventLoopGroup workerGroup = new NioEventLoopGroup();

        try {
            Security.addProvider(new BouncyCastleProvider());
            KeyStore keyStore = generateSelfSignedCertificate();
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(keyStore, "password".toCharArray());
            SslContext sslCtx = SslContextBuilder.forServer(kmf).build();

            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel ch) {
                            ChannelPipeline p = ch.pipeline();
//                            SSLEngine engine = sslCtx.newEngine(ch.alloc());
//                            p.addLast(new SslHandler(engine));
                            p.addLast(new HttpServerCodec());
                            p.addLast(new HttpObjectAggregator(65536));
                            p.addLast(new HttpProxyHandler(sslCtx));
                        }
                    });

            Channel ch = b.bind(port).sync().channel();
            System.out.println("HTTP代理服务器启动，监听端口: " + port);
            ch.closeFuture().sync();
        } finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }

    public class HttpProxyHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
        private final SslContext sslCtx;

        public HttpProxyHandler(SslContext sslCtx) {
            this.sslCtx = sslCtx;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
            System.out.println("Request Method: " + request.method());
            System.out.println("Request Headers: " + request.headers());
            if (HttpMethod.CONNECT.equals(request.method())) {
                handleConnectRequest(ctx, request);
            } else {
                handleHttpRequest(ctx, request);
            }
        }

        private void handleConnectRequest(ChannelHandlerContext ctx, FullHttpRequest request) {
            String[] hostPort = request.uri().split(":");
            String host = hostPort[0];
            int port = Integer.parseInt(hostPort[1]);

            // 建立与目标服务器的连接
            Bootstrap b = new Bootstrap();
            b.group(ctx.channel().eventLoop())
                    .channel(ctx.channel().getClass())
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
//                            SSLEngine engine = sslCtx.newEngine(ctx.alloc(), host, port);
//                            engine.setUseClientMode(true);
//                            ch.pipeline().addLast("ssl", new SslHandler(engine));
                            // 仅添加用于转发的handler，不处理SSL，因为它是由客户端和目标服务器协商的
                            ch.pipeline().addLast(new HttpProxyHandler.RelayHandler(ctx.channel()));
                        }
                    });

            ChannelFuture connectFuture = b.connect(host, port);
            connectFuture.addListener((ChannelFutureListener) future -> {
                if (future.isSuccess()) {
//                    ctx.channel().pipeline().addLast(new RelayHandler(future.channel()));  // 添加用于转发的handler

                    // 向客户端发送 HTTP 200 OK 响应,表示连接建立成功
                    FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
                    ctx.writeAndFlush(response);
//                    ctx.channel().config().setAutoRead(true);// 确保自动读取数据
                    // 移除HTTP处理器并设置透明转发
                    ctx.pipeline().remove(HttpServerCodec.class);
                    ctx.pipeline().remove(HttpObjectAggregator.class);
                    ctx.pipeline().addLast(new RelayHandler(future.channel()));  // 添加用于转发的handler
                    ctx.pipeline().remove(this.getClass());  // 移除当前处理器
                } else {
                    // 连接失败，向客户端发送 500 错误
                    ctx.writeAndFlush(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR));
                    ctx.close();
                }
            });
        }

        // 数据转发处理器
        private static class RelayHandler extends ChannelInboundHandlerAdapter {
            private final Channel relayChannel;

            public RelayHandler(Channel relayChannel) {
                this.relayChannel = relayChannel;
            }

            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                if (relayChannel.isActive()) {
                    relayChannel.writeAndFlush(msg);
                } else {
                    ReferenceCountUtil.release(msg);
                    ctx.channel().close();
                }
            }

            @Override
            public void channelInactive(ChannelHandlerContext ctx) throws Exception {
                if (relayChannel.isActive()) {
                    relayChannel.close();
                }
            }

            @Override
            public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                if (cause instanceof IOException && cause.getMessage().contains("Connection reset")) {
                    System.out.println("Connection was reset by the peer");
                } else {
                    cause.printStackTrace();
                }
                ctx.close();
            }
        }

        private void handleHttpRequest(ChannelHandlerContext ctx, FullHttpRequest request) throws InterruptedException {
            String url = request.uri();
//            if (!url.startsWith("www.baidu.com")) {
//                return;
//            }

            String host = url.split(":")[0];
            int port = url.split(":").length == 2 ? Integer.parseInt(url.split(":")[1]) : 80;
            boolean httpsFlag = port == 443;

            // 创建与目标服务器的连接
            Bootstrap b = new Bootstrap();
            b.group(ctx.channel().eventLoop())
                    .channel(ctx.channel().getClass())
                    .handler(new ChannelInitializer<Channel>() {
                        @Override
                        protected void initChannel(Channel ch) {
                            ChannelPipeline p = ch.pipeline();
                            if (httpsFlag) {
                                SSLEngine engine = sslCtx.newEngine(ctx.alloc(), host, port);
                                engine.setUseClientMode(true);
                                p.addLast("ssl", new SslHandler(engine));
                            }
                            p.addLast(new HttpClientCodec());
                            p.addLast(new HttpObjectAggregator(65536));
                            p.addLast(new com.custom.handler.RelayHandler(ctx.channel()));
                        }
                    });
        }
    }

    private KeyStore generateSelfSignedCertificate() throws KeyStoreException, NoSuchAlgorithmException, CertificateException, SignatureException, InvalidKeyException, NoSuchProviderException, IOException {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();

        X509Certificate cert = generateCertificate(keyPair);

        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null, null);
        keyStore.setKeyEntry("alias", keyPair.getPrivate(), "password".toCharArray(), new X509Certificate []{cert});

        return keyStore;
    }

    private X509Certificate generateCertificate(KeyPair keyPair) throws CertificateException, InvalidKeyException, NoSuchAlgorithmException, SignatureException, NoSuchProviderException {
        X509V3CertificateGenerator certGenerator = new X509V3CertificateGenerator();
        certGenerator.setSerialNumber(BigInteger.valueOf(System.currentTimeMillis()));
        certGenerator.setSubjectDN(new X509Principal("CN=localhost"));
        certGenerator.setIssuerDN(new X509Principal("CN=localhost"));
        certGenerator.setPublicKey(keyPair.getPublic());
        certGenerator.setNotBefore(new Date(System.currentTimeMillis() - 1000L * 60 * 60 * 24));
        certGenerator.setNotAfter(new Date(System.currentTimeMillis() + 1000L * 60 * 60 * 24 * 365));
        certGenerator.setSignatureAlgorithm("SHA256WithRSAEncryption");

        return certGenerator.generate(keyPair.getPrivate(), "BC");
    }

    public static void main(String[] args) throws Exception {
        int localPort = 8888;
        int remotePort = 5088;
        String remoteHost = "127.0.0.1";
        new ProxyServerHandler().start(remotePort);
        ProxyClientHandler.start(localPort, remoteHost, remotePort);
    }
}