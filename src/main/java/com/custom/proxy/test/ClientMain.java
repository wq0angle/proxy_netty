package com.custom.proxy.test;

import com.custom.proxy.handler.client.ProxyClientHandler;

public class ClientMain {
    public static void main(String[] args) throws Exception {
        int localPort = 8888;
        int remotePort = 5088;
        String remoteHost = "www.wq0angle.online";
        ProxyClientHandler.start(localPort, remoteHost, remotePort);
    }
}
