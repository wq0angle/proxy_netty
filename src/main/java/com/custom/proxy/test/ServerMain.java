package com.custom.proxy.test;

import com.custom.proxy.handler.server.ProxyServerHandler;
import com.custom.proxy.handler.server.WebsiteServerHandler;

public class ServerMain {
    public static void main(String[] args) throws Exception {

        // 设置http静态网站的代理IP、端口号和静态网站目录
        String ipAddress = "127.0.0.1";
        int port = 5088;
        String websiteDirectory = "C:\\Users\\wq0angle\\Desktop\\blog";
        new WebsiteServerHandler().start(ipAddress, port, websiteDirectory);

        int remotePort = 6088;
        new ProxyServerHandler().start(remotePort);
    }
}
