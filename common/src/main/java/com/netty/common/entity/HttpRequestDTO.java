package com.netty.common.entity;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@Slf4j
@Data
public class HttpRequestDTO {
    private String uri;
    private String method;
    private String version;
    private Map<String, String> headers;
    private byte[] content;

    public HttpRequestDTO(String uri, String method, String version, Map<String, String> headers, byte[] content) {
        this.uri = uri;
        this.method = method;
        this.version = version;
        this.headers = headers;
        this.content = content;
    }
}
