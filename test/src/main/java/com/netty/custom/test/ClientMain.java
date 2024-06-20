package com.netty.custom.test;

import com.netty.custom.handler.client.ProxyClientHandler;

public class ClientMain {
    public static void main(String[] args) throws Exception {
        int localPort = 8888;
        int remotePort = 443;
        String remoteHost = "www.wq0angle.online";
        ProxyClientHandler.start(localPort, remoteHost, remotePort);
    }
}
