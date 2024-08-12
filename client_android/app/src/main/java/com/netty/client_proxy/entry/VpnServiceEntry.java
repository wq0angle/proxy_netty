package com.netty.client_proxy.entry;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.IpPrefix;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import com.netty.client_proxy.config.AppConfig;
import timber.log.Timber;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;

public class VpnServiceEntry extends VpnService {
    private ParcelFileDescriptor vpnInterface = null;
    private Thread vpnThread = null;
    private Socket socket = null;
    private OutputStream outputStream = null;
    AppConfig appConfig = new AppConfig();

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            setupVpn();
        } catch (PackageManager.NameNotFoundException e) {
            throw new RuntimeException(e);
        }
        if (vpnInterface == null) {
            Timber.tag("VPN").e("VPN 服务未启动");
            stopSelf();  // 停止服务
            return START_NOT_STICKY;
        }

        vpnThread = new Thread(() -> {
            try {

                // 初始化Socket连接
                initializeSocketConnection();

                FileInputStream in = new FileInputStream(vpnInterface.getFileDescriptor());
                byte[] buffer = new byte[1024 * 1024 * 10];
                int length;

                while (true) {
                    length = in.read(buffer);
                    if (length > 0) {
//                        sendDataToNetty(buffer, length);
                        sendDataToTargetServer(buffer, length);
                    }
                }
            } catch (Exception e) {
                Timber.tag("VPN").e(e, "onStartCommand");
            }
        });
        vpnThread.start();
        return START_STICKY;
    }

    private void setupVpn() throws PackageManager.NameNotFoundException {
        VpnService.Builder builder = new VpnService.Builder();
        builder.addAddress("10.0.0.2", 24);
        // 添加默认路由，允许所有流量
        builder.addRoute("0.0.0.0", 0);
        builder.addDisallowedApplication("com.netty.client_proxy");
        vpnInterface = builder.establish();
        if (vpnInterface == null) {
            Timber.tag("VPN").e("Failed to establish VPN");
        }
    }

    private void initializeSocketConnection() {
        try {
            socket = new Socket("127.0.0.1", appConfig.getLocalPort());
            outputStream = socket.getOutputStream();
        } catch (Exception e) {
            Timber.tag("VPN").e(e, "Failed to initialize socket connection");
        }
    }

    private void sendDataToNetty(byte[] data, int length) {
        if (outputStream != null) {
            try {
                String destinationIP = extractDestinationIP(data);
                int destinationPort = extractDestinationPort(data);

                // 检查是否是对端口443的连接尝试
                if (isHttpsConnectionAttempt(data)) {
//                    // 构建CONNECT请求
//                    String connectRequest = buildConnectRequest(data);
//                    // 发送CONNECT请求
//                    outputStream.write(connectRequest.getBytes());
//                    outputStream.flush();

                }
//                if (destinationIP.equals(appConfig.getRemoteHost()) && destinationPort == appConfig.getRemotePort()) {
//                    // 如果目标地址和端口与配置一致，则报错
//                    Timber.tag("sendDataToNetty").e("VPN: 目标地址和端口与代理配置一致，连接异常");
//                }
//
//                // 发送原始数据
                outputStream.write(data, 0, length);
                outputStream.flush();
            } catch (Exception e) {
                Timber.tag("VPN").e(e, "Failed to send data");
                // 重新初始化连接
                initializeSocketConnection();
            }
        }
    }

    private void sendDataToTargetServer(byte[] data, int length) {
        // 解析 IP 和 TCP 头部
        int ipHeaderLength = (data[0] & 0x0F) * 4; // IP 头部长度
        int tcpHeaderLength = ((data[ipHeaderLength + 12] & 0xF0) >> 4) * 4; // TCP 头部长度
        int applicationDataStartIndex = ipHeaderLength + tcpHeaderLength; // 应用层数据起始位置

        // 获取应用层数据
        byte[] applicationData = new byte[length - applicationDataStartIndex];
        System.arraycopy(data, applicationDataStartIndex, applicationData, 0, applicationData.length);

        String destinationIP = extractDestinationIP(data);
        int destinationPort = extractDestinationPort(data);

        String httpRequest = "GET / HTTP/1.1\r\n" +
                "Host: " + destinationIP + "\r\n" +
                "User-Agent: MyCustomUserAgent\r\n" +
                "Accept: text/html\r\n" +
                "\r\n";
        FileOutputStream out = null;

        try {
            out = new FileOutputStream(vpnInterface.getFileDescriptor());

            // 检查是否是 HTTPS 请求
            if (isHttpsConnectionAttempt(data)) {
                SSLSocketFactory sslSocketFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
                SSLSocket sslSocket = (SSLSocket) sslSocketFactory.createSocket(destinationIP, destinationPort);
                sslSocket.startHandshake(); // 完成握手

                // 发送应用层数据
                OutputStream outputTarget = sslSocket.getOutputStream();
                outputTarget.write(httpRequest.getBytes(StandardCharsets.UTF_8));
//                outputTarget.write(applicationData);
//                outputTarget.write(data);
                outputTarget.flush();

                // 读取响应
                InputStream inputTarget = sslSocket.getInputStream();
                byte[] responseBuffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = inputTarget.read(responseBuffer)) != -1) {
                    // 将响应写回到 VPN 接口
                    out.write(responseBuffer, 0, bytesRead);
                    out.flush();
                }
                sslSocket.close(); // 关闭 SSL socket
            } else {

                // 使用普通 Socket 处理 HTTP 请求
                Socket socket = new Socket(destinationIP, destinationPort);
                OutputStream outputTarget = socket.getOutputStream();
                outputTarget.write(applicationData);
//                outputTarget.write(httpRequest.getBytes(StandardCharsets.UTF_8));
                outputTarget.flush();

                // 读取响应
                InputStream inputTarget = socket.getInputStream();
                byte[] responseBuffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = inputTarget.read(responseBuffer)) != -1) {
                    // 将响应写回到 VPN 接口
                    out.write(responseBuffer, 0, bytesRead);
                    out.flush();
                }
                socket.close(); // 关闭普通 socket
            }
        } catch (Exception e) {
            Timber.tag("VPN").e(e, "Failed to send data to target server");
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                    Timber.tag("VPN").e(e, "Failed to close output stream");
                }
            }
        }
    }

    private boolean isHttpsConnectionAttempt(byte[] data) {
        // 检查数据长度是否足够包含基本的 IP 头部和 TCP 头部
        if (data.length < 40) return false;  // IP 头部 (最小20字节) + TCP 头部 (最小20字节)

        // 获取 IP 头部中的协议字段，确定是否是 TCP 协议
        // IP 协议字段位于第10个字节，TCP 协议的值为 6
        int protocol = data[9] & 0xFF;
        if (protocol != 6) return false;  // 不是 TCP 协议

        // 计算 IP 头部长度，IP 头部的第一个字节的低四位表示 IP 头部长度（单位为4字节）
        int ipHeaderLength = (data[0] & 0x0F) * 4;
        if (data.length < ipHeaderLength + 20) return false;  // 数据不足以包含完整的 TCP 头部

        // 提取 TCP 头部中的目标端口，位于 IP 头部之后的第2和第3个字节
        int destPort = ((data[ipHeaderLength + 2] & 0xFF) << 8) | (data[ipHeaderLength + 3] & 0xFF);

        // 检查目标端口是否为 HTTPS 的默认端口 443
        return destPort == 443;
    }

    private String buildConnectRequest(byte[] data) throws UnknownHostException {
        String destinationIP = extractDestinationIP(data);
        int destinationPort = extractDestinationPort(data);
        String hostName = ipToHostName(destinationIP);  // 尝试将 IP 转换为域名

        String requestData = new String(data, StandardCharsets.UTF_8);

        // 构建 CONNECT 请求
        return "CONNECT " + hostName + ":" + destinationPort + " HTTP/1.1\r\n" +
                "Host: " + hostName + "\r\n\r\n";
    }

    private String ipToHostName(String ip) {
        try {
            InetAddress addr = InetAddress.getByName(ip);
            String hostName = addr.getHostName();
            if (hostName.equals(ip)) {
                // 如果 getCanonicalHostName 返回的仍然是 IP 地址，则说明没有找到对应的域名
                return ip;
            }
            return hostName;
        } catch (UnknownHostException e) {
            Timber.tag("VPN").w(e, "DNS查询IP未能获取域名,IP: %s", ip);
            return ip;  // 在解析失败时回退到 IP 地址
        }
    }

    private String extractDestinationIP(byte[] data) {
        // IP 地址位于 IP 头部的第16到第19字节
        return (data[16] & 0xFF) + "." +
                (data[17] & 0xFF) + "." +
                (data[18] & 0xFF) + "." +
                (data[19] & 0xFF);
    }

    private int extractDestinationPort(byte[] data) {
        // 假设 IP 头部长度为20字节（无选项字段）
        return ((data[20 + 2] & 0xFF) << 8) | (data[20 + 3] & 0xFF);
    }



    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            if (vpnInterface != null) {
                vpnInterface.close();
            }
            if (vpnThread != null) {
                vpnThread.interrupt();
            }
            if (socket != null) {
                socket.close();
            }
        } catch (IOException e) {
            Timber.tag("VPN").e(e, "onDestroy");
        }
    }
}