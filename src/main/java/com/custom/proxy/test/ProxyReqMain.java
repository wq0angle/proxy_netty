package com.custom.proxy.test;

import lombok.extern.slf4j.Slf4j;

import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;

@Slf4j
public class ProxyReqMain {

    public static void main(String[] args) {
        String targetUrl = "https://fanyi.baidu.com/";
        String proxyHost = "127.0.0.1";
        int proxyPort = 6666;

        try {
            URL url = new URL(targetUrl);

            Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort));
            HttpsURLConnection connection = (HttpsURLConnection) url.openConnection(proxy);

            connection.setRequestMethod("GET");
            connection.setHostnameVerifier((_, _) -> true);

            // 发送请求并获取响应
            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String line;
            StringBuilder response = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();

            // 打印响应内容
            log.info("Response: {}", response);

            connection.disconnect();
        } catch (Exception e) {
            log.error("Error: ", e);
        }
    }
}