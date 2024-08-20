package com.netty.client_proxy.test;

import android.app.Activity;
import android.content.Context;
import com.google.android.material.snackbar.Snackbar;
import com.netty.client_proxy.R;
import com.netty.client_proxy.config.ProxyLoadConfig;
import com.netty.client_proxy.entity.ProxyConfigDTO;
import lombok.SneakyThrows;
import timber.log.Timber;

import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.net.URL;

public class ProxyReqMain {

    public static void startReq(Activity activity) {
        ProxyConfigDTO proxyConfigDTO = ProxyLoadConfig.getProxyConfigDTO();
        String targetUrl = "https://google.com";
        String proxyHost = "127.0.0.1";
        int proxyPort = proxyConfigDTO.getLocalPort();

        try {
            long startTime = System.currentTimeMillis(); // 记录开始时间
            URL url = new URL(targetUrl);

            Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort));
            HttpsURLConnection connection = (HttpsURLConnection) url.openConnection(proxy);

            connection.setRequestMethod("GET");
            connection.setHostnameVerifier((a, b) -> true);

            // 发送请求并获取响应
            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String line;
            StringBuilder response = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();

            long endTime = System.currentTimeMillis(); // 记录结束时间
            long responseTime = endTime - startTime; // 计算响应时间

            // 打印响应内容和响应时间
            Timber.tag("reqTest").i("Response: %s", response);
            Timber.tag("reqTest").i("Response Time: %d ms", responseTime); // 打印响应时间
            Snackbar.make(activity.findViewById(R.id.startVpnButton), "测试真连接 " + targetUrl + " 响应时间: " + responseTime + " ms", Snackbar.LENGTH_SHORT).show();

            connection.disconnect();
        } catch (Exception e) {
            Timber.tag("reqTest").e(e,"Error: ");
    }
    }
}
