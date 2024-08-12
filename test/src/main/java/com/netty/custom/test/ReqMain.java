package com.netty.custom.test;

import kong.unirest.HttpResponse;
import kong.unirest.Unirest;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class ReqMain {
    public static void main(String[] args) throws Exception {
//        HttpResponse<String> response = Unirest.post("https://www.wq0angle.online/")
//                .header("Host","www.wq0angle.online")
//                .asString();
//
//        System.out.println(response.getBody());
//        System.out.println(response.getHeaders());

        String destinationIP = "127.0.0.1";
        int destinationPort = 4433;

        String httpRequest = "GET / HTTP/1.1\r\n" +
                "Host: " + destinationIP + "\r\n" +
                "User-Agent: MyCustomUserAgent\r\n" +
                "Accept: text/html\r\n" +
                "\r\n";

        // 使用普通 Socket 处理 HTTP 请求
        Socket socket = new Socket(destinationIP, destinationPort);
        OutputStream outputTarget = socket.getOutputStream();
        outputTarget.write(httpRequest.getBytes(StandardCharsets.UTF_8));
        outputTarget.flush();

        // 读取响应
        InputStream inputTarget = socket.getInputStream();
        byte[] responseBuffer = new byte[1024];
        int bytesRead;
        while ((bytesRead = inputTarget.read(responseBuffer)) != -1) {
            // 将响应写回到 VPN 接口
            System.out.println(new String(responseBuffer, 0, bytesRead));
        }
        socket.close(); // 关闭普通 socket
    }
}
