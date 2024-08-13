package com.netty.common.enums;


public enum ProxyReqEnum {
    HTTP(1, "http代理请求"),
    WEBSOCKET(2, "websocket代理请求")
    ;
    private final Integer code;
    private final String msg;

    ProxyReqEnum(Integer code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    public static ProxyReqEnum parse(String name){
        StringBuilder sb = new StringBuilder();
        for (char c : name.toCharArray()) {
            if (Character.isLetter(c)) { // 判断是否为字母
                sb.append(Character.toUpperCase(c)); // 转换为大写
            } else {
                sb.append(c); // 不是字母，直接添加到结果中
            }
        }
        return ProxyReqEnum.valueOf(sb.toString());
    }
}
