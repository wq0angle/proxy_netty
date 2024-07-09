package com.netty.client_proxy.enums;

import lombok.Getter;

@Getter
public enum ChannelFlowEnum {

    LOCAL_CHANNEL_FLOW(1, "本地连接通道"),
    FUTURE_CHANNEL_FLOW(2, "future连接通道")
    ;

    private final Integer code;
    private final String msg;

    ChannelFlowEnum(Integer code, String msg) {
        this.code = code;
        this.msg = msg;
    }
}
