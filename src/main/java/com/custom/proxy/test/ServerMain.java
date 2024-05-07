package com.custom.proxy.test;

import com.custom.proxy.handler.server.ProxyServerHandler;

public class ServerMain {
    public static void main(String[] args) throws Exception {
        int localPort = 8888;
        int remotePort = 5088;
        String remoteHost = "127.0.0.1";
        ProxyServerHandler.start(remotePort);
    }
}
