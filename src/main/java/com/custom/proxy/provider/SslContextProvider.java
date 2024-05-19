package com.custom.proxy.provider;

import io.netty.handler.ssl.SniHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.util.internal.StringUtil;
import lombok.extern.slf4j.Slf4j;

import javax.net.ssl.KeyManagerFactory;
import java.io.FileInputStream;
import java.security.KeyStore;

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

        return SslContextBuilder
                .forServer(keyManagerFactory)
                .protocols("TLSv1.1","TLSv1.2","TLSv1.3")
                .ciphers(null)  // 默认使用所有可用的加密套件
                .build();
    }

    public static SniHandler getSniHandler(SslContext sslContext1, SslContext sslContext2) {
        return new SniHandler(hostname -> {
            if (StringUtil.isNullOrEmpty(hostname)){
                log.info("SNI Fallback, hostname is null");
                return sslContext2;
            }
            if (hostname.contains("wq0angle.fun")) {
                return sslContext1;
            } else if (hostname.contains("wq0angle.online")) {
                return sslContext2;
            } else {
                log.info("SNI Fallback, hostname: {}", hostname);
                return sslContext2; // SNI回落，若未命中匹配的规则，选择默认的SslContext
            }
        });
    }
}