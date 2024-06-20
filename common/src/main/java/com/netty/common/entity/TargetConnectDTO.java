package com.netty.common.entity;

import lombok.Data;

@Data
public class TargetConnectDTO {

    private String host;

    private Integer port;

    String url;

    private Integer proxyType;
}
