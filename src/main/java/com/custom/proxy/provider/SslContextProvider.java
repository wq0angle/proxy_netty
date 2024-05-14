package com.custom.proxy.provider;

import io.netty.handler.ssl.SniHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import lombok.extern.slf4j.Slf4j;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

@Slf4j
public class SslContextProvider {
    public static SslContext getSslContext1() throws Exception {
        String keyStoreFilePath = "/root/tls/www.wq0angle.fun.jks";
        String keyStorePassword = "123456";
        return getSslContextByJKS(keyStoreFilePath,keyStorePassword);
    }
    public static SslContext getSslContext2() throws Exception {
        String keyStoreFilePath = "/root/tls/www.wq0angle.online.jks";
        String keyStorePassword = "123456";
        return getSslContextByJKS(keyStoreFilePath,keyStorePassword);
    }

    private static SslContext getSslContextByJKS(String keyStoreFilePath, String keyStorePassword) throws Exception {
        log.info("loading SSL file in {}", keyStoreFilePath);

        KeyStore keyStore = KeyStore.getInstance("JKS");
        try (FileInputStream inputStream = new FileInputStream(keyStoreFilePath)) {
            keyStore.load(inputStream, keyStorePassword.toCharArray());
        }

        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(keyStore, keyStorePassword.toCharArray());

        return SslContextBuilder.forServer(keyManagerFactory).build();
    }

    public static SslContext getAwsTrustedSslContext() throws Exception {
        // 加载AWS的根证书或其他自签名证书
        KeyStore awsKeyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        try (FileInputStream certificateStream = new FileInputStream("aws-root-ca.pem")) {
            awsKeyStore.load(null, null);
            awsKeyStore.setCertificateEntry("awsRootCert", loadCertificate(certificateStream));
        }

        // 创建信任管理器工厂并初始化它，使其信任我们的customTrustStore
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(awsKeyStore);

        // 使用信任管理器工厂构建客户端SSL上下文
        return SslContextBuilder.forClient()
                .trustManager(tmf)
                .build();
    }

    private static X509Certificate loadCertificate(InputStream certificateStream) throws CertificateException {
        CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
        return (X509Certificate) certificateFactory.generateCertificate(certificateStream);
    }

    public static SniHandler getSniHandler(SslContext sslContext1, SslContext sslContext2) {
        return new SniHandler(hostname -> {
            if (hostname.contains("wq0angle.fun") || hostname.contains("cloudflare.com")) {
                return sslContext1;
            } else if (hostname.contains("wq0angle.online") || hostname.contains("cloudfront.net")) {
                return sslContext2;
            } else {
                log.info("SNI Fallback, hostname: {}", hostname);
                return sslContext1; // SNI回落，若未命中匹配的规则，选择默认的SslContext
            }
        });
    }
}