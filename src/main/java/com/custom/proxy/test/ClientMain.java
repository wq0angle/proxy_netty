package com.custom.proxy.test;

import com.custom.proxy.handler.client.ProxyClientHandler;

public class ClientMain {
    public static void main(String[] args) throws Exception {
        int localPort = 8888;
        int remotePort = 443;
        String remoteHost = "d31z4tkdw2rsym.cloudfront.net";
        ProxyClientHandler.start(localPort, remoteHost, remotePort);
    }
}
