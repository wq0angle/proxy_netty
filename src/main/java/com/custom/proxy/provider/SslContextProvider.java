package com.custom.proxy.provider;

import io.netty.handler.ssl.SniHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
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

        return SslContextBuilder.forServer(keyManagerFactory).build();
    }

    public static SniHandler getSniHandler(SslContext sslContext1, SslContext sslContext2) {
        return new SniHandler(hostname -> {
            if (hostname.startsWith("wq0angle.fun")) {
                return sslContext1;
            } else if (hostname.startsWith("wq0angle.online") || hostname.startsWith("d31z4tkdw2rsym.cloudfront.net")) {
                return sslContext2;
            } else {
                return sslContext1; // SNI回落，若未命中匹配的规则，选择默认的SslContext
            }
        });
    }
}