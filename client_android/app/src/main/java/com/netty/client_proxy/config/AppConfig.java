package com.netty.client_proxy.config;

import lombok.Data;

@Data
public class AppConfig {

    private String remoteHost = "30.75.178.148";

    private int remotePort = 4433;

    private Integer localPort = 8888;

    private Boolean sslRequestEnabled = false;

    private String proxyType = "vpn";

}
