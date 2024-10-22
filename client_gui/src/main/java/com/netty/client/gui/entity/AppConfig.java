package com.netty.client.gui.entity;

import lombok.Data;

@Data
public class AppConfig {

    private String remoteHost;

    private Integer remotePort;

    private Integer localPort;

    private Boolean sslRequestEnabled;

    private String proxyType;
}
