package com.netty.server.provider;

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
import java.util.Objects;

@Slf4j
public class SslContextProvider {

    /**
     * 加载返回所有的证书上下文
     * @param sslJksPath 证书目录的路径
     * @param sslJksFilePassword 证书文件名及密码
     * @return 所有的ssl证书上下文
     */
    public static Map<String,SslContext> mapSslContext(String sslJksPath, String sslJksFilePassword) throws Exception {
        if (StringUtil.isNullOrEmpty(sslJksPath)) {
            throw new Exception("启用了SSL证书监听，sslJksPath 配置不能为空");
        }
        if (StringUtil.isNullOrEmpty(sslJksFilePassword)) {
            throw new Exception("启用了SSL证书监听，sslJksFilePassword 配置不能为空");
        }
        sslJksPath = sslJksPath.trim();

        Map<String,SslContext> sslContextMap = Maps.newHashMap();
        String[] sslJksFilePasswordArr = sslJksFilePassword.trim().split(",");

        for (String filePassword : sslJksFilePasswordArr) {
            filePassword = filePassword.trim();
            if (filePassword.split(":").length != 2) {
                throw new Exception("sslJksFilePassword 配置格式错误，请检查");
            }
            String fileName = filePassword.split(":")[0];
            String password = filePassword.split(":")[1];
            // 获取证书上下文
            SslContext sslContext = getSslContextByJKS(Path.of(sslJksPath).resolve(fileName), password);
            sslContextMap.put(fileName,sslContext);
        }
        return sslContextMap;
    }

    /**
     * 从JKS文件中加载SSL证书上下文
     * @param keyStoreFullFilePath 证书文件完整路径
     * @param keyStorePassword 证书密码
     * @return ssl证书上下文
     */
    private static SslContext getSslContextByJKS(Path keyStoreFullFilePath, String keyStorePassword) throws Exception {
        log.info("loading SSL file in {}", keyStoreFullFilePath);

        KeyStore keyStore = KeyStore.getInstance("JKS");
        try (FileInputStream inputStream = new FileInputStream(keyStoreFullFilePath.toString())) {
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

    /**
     * sin 匹配规则,从多个ssl证书上下文中,获取对应域名的SniHandler
     * @param sslContextMap 所有的ssl证书上下文
     * @param sinDefaultFile 设置默认的 sin 域名证书文件
     * @return sin 处理器
     */
    public static SniHandler getSniHandler(Map<String,SslContext> sslContextMap,String sinDefaultFile) {
        return new SniHandler(hostName -> {
            if (StringUtil.isNullOrEmpty(hostName)){
                log.info("SNI Fallback, hostName is null");
                return sslContextMap.get(sinDefaultFile);
            }
            String[] hostNameArr = hostName.split("\\.");
            if (hostNameArr.length < 2) {
                log.info("SNI Fallback, hostName Illegal format");
                hostName = hostNameArr[0];
            } else {
                hostName = hostNameArr[hostNameArr.length - 2] + hostNameArr[hostNameArr.length - 1];
            }
            String finalHostName = hostName;
            String filterHostname = sslContextMap.keySet()
                    .stream()
                    .filter(fileNameKey -> {
                        String[] keyNameArr = fileNameKey.replace(".jks", "").split("\\.");
                        String keyName = keyNameArr.length == 1 ? keyNameArr[0] : keyNameArr[keyNameArr.length - 2] + keyNameArr[keyNameArr.length - 1];
                        return Objects.equals(finalHostName, keyName);
                    })
                    .findFirst().orElse(null);

            if (StringUtil.isNullOrEmpty(filterHostname)) {
                log.info("SNI Fallback, missed hostname");
                return sslContextMap.get(sinDefaultFile);
            }

            return sslContextMap.get(filterHostname);
        });
    }

}