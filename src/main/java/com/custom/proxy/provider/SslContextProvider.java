package com.custom.proxy.provider;

import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;

import javax.net.ssl.KeyManagerFactory;
import java.io.FileInputStream;
import java.security.KeyStore;

public class SslContextProvider {
    public static SslContext getSslContext() throws Exception {
        String keyStoreFilePath = "你的jks证书文件";
        String keyStorePassword = "123456";

        KeyStore keyStore = KeyStore.getInstance("JKS");
        try (FileInputStream inputStream = new FileInputStream(keyStoreFilePath)) {
            keyStore.load(inputStream, keyStorePassword.toCharArray());
        }

        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(keyStore, keyStorePassword.toCharArray());

        return SslContextBuilder.forServer(keyManagerFactory).build();
    }
}