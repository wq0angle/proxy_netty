package com.netty.client.gui.entity;

import lombok.Data;

@Data
public class AppConfig {

    /**
     * 远程服务器地址
     */
    private String remoteHost;

    /**
     * 远程服务器端口
     */
    private Integer remotePort;

    /**
     * 本地监听端口
     */
    private Integer localPort;

    /**
     * 是否开启ssl
     */
    private Boolean sslRequestEnabled;

    /**
     * 代理类型
     */
    private String proxyType;
}
