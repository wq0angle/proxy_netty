package com.netty.client_proxy.entity;

import lombok.Data;

@Data
public class ProxyConfigDTO {

    private String remoteHost;

    private int remotePort;

    private Integer localPort;

    private Boolean sslRequestEnabled;

    private String proxyType;

}
