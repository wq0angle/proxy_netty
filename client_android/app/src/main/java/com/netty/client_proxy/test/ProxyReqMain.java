package com.netty.client_proxy.test;

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

    public static void reqTest() {
//        new Thread(ProxyReqMain::startReq).start();
        new Thread(ProxyReqMain::sendDataTarget).start();
    }

    private static void sendDataTarget(){
        try {
            Timber.tag("VPN").i("send data start");
            Socket socketTarget1 = new Socket("127.0.0.1", 8888);
            Socket socketTarget = new Socket("30.75.178.142", 4433);

            Timber.tag("VPN").i("send data");
            OutputStream output = socketTarget.getOutputStream();
            output.write(new byte[0]);
            output.flush(); // 确保数据被发送
            Timber.tag("VPN").i("send finished");
        }catch (Exception e) {
            Timber.tag("VPN").e(e,"Error: ");
        }
    }

    private static void startReq() {
        String targetUrl = "https://www.baidu.com/";
        String proxyHost = "127.0.0.1";
        int proxyPort = 8888;

        try {
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

            // 打印响应内容
            Timber.tag("reqTest").i("Response: %s", response);

            connection.disconnect();
        } catch (Exception e) {
            Timber.tag("reqTest").e(e,"Error: ");
        }
    }
}
