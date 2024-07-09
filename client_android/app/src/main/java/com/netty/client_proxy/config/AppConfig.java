package com.netty.client_proxy.config;

import lombok.Data;

@Data
public class AppConfig {

    private String remoteHost = "";

    private int remotePort = 443;

    private Integer localPort = 8888;

    private Boolean sslRequestEnabled = true;

    private String proxyType = "websocket";

}
