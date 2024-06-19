package com.custom.provider;

import io.netty.handler.ssl.SniHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.util.internal.StringUtil;
import lombok.extern.slf4j.Slf4j;
import org.testng.collections.Maps;

import javax.net.ssl.KeyManagerFactory;
import java.io.FileInputStream;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.Map;

@Slf4j
public class SslContextProvider {

    public static Map<String,SslContext> mapSslContext(String sslJksPath, String sslJksFilePassword) throws Exception {
        if (StringUtil.isNullOrEmpty(sslJksPath)){
            throw new Exception("启用了SSL证书监听，sslJksPath 配置不能为空");
        }
        if (StringUtil.isNullOrEmpty(sslJksFilePassword)){
            throw new Exception("启用了SSL证书监听，sslJksFilePassword 配置不能为空");
        }
        Map<String,SslContext> sslContextMap = Maps.newHashMap();
        String[] sslJksFilePasswordArr = sslJksFilePassword.trim().split(",");

        for (String filePassword : sslJksFilePasswordArr) {
            if (filePassword.split(":").length != 2){
                throw new Exception("sslJksFilePassword 配置格式错误，请检查");
            }
            String fileName = filePassword.split(":")[0];
            String password = filePassword.split(":")[1];
            SslContext sslContext = getSslContextByJKS(Path.of(sslJksPath).resolve(fileName), password);
            sslContextMap.put(fileName,sslContext);
        }
        return sslContextMap;
    }

    private static SslContext getSslContextByJKS(Path keyStoreFilePath, String keyStorePassword) throws Exception {
        log.info("loading SSL file in {}", keyStoreFilePath);

        KeyStore keyStore = KeyStore.getInstance("JKS");
        try (FileInputStream inputStream = new FileInputStream(keyStoreFilePath.toString())) {
            keyStore.load(inputStream, keyStorePassword.toCharArray());
        }

        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(keyStore, keyStorePassword.toCharArray());

        return SslContextBuilder
                .forServer(keyManagerFactory)
                .protocols("TLSv1.1", "TLSv1.2", "TLSv1.3")
                .ciphers(null)  // 默认使用所有可用的加密套件
                .build();
    }

    public static SniHandler getSniHandler(Map<String,SslContext> sslContextMap,String sinDefaultFile) {
        return new SniHandler(hostname -> {
            if (StringUtil.isNullOrEmpty(hostname)){
                log.info("SNI Fallback, hostname is null");
                return sslContextMap.get(sinDefaultFile);
            }
            String filterHostname = sslContextMap.keySet()
                    .stream().map(p -> p.replace("www.", ""))
                    .filter(hostname::contains)
                    .findFirst().orElse(null);

            if (StringUtil.isNullOrEmpty(filterHostname)){
                log.info("SNI Fallback, missed hostname");
                return sslContextMap.get(sinDefaultFile);
            }

            return sslContextMap.get(filterHostname);
        });
    }

}