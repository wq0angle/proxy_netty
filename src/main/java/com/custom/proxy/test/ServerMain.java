package com.custom.proxy.test;

import com.custom.proxy.handler.server.ProxyServerHandler;

public class ServerMain {
    public static void main(String[] args) throws Exception {
        int remotePort = 5088;
        ProxyServerHandler.start(remotePort);
    }
}
