package com.custom.proxy.test;

import com.custom.proxy.handler.client.ProxyClientHandler;
import com.custom.proxy.handler.inner.ProxyInnerHandler;

public class InnerMain {
    public static void main(String[] args) throws Exception {
        int localPort = 9999;
        int remotePort = 443;
        String remoteHost = "wq0angle.online";
        ProxyInnerHandler.start(localPort, remoteHost, remotePort);
    }
}
