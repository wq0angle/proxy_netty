package com.custom.proxy.test;

import com.custom.proxy.handler.client.ProxyClientHandler;

public class ClientMain {
    public static void main(String[] args) throws Exception {
        int localPort = 8888;
        int remotePort = 6088;
        String remoteHost = "127.0.0.1";
        ProxyClientHandler.start(localPort, remoteHost, remotePort);
    }
}
