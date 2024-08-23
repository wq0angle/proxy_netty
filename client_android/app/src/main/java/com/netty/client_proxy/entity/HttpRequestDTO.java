package com.netty.client_proxy.entity;

import lombok.Data;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Data
public class HttpRequestDTO {
    private String uri;
    private String method;
    private String version;
    private Map<String, String> headers;
    private List<Byte> content;

    public HttpRequestDTO(String uri, String method, String version, Map<String, String> headers, byte[] content) {
        this.uri = uri;
        this.method = method;
        this.version = version;
        this.headers = headers;
        this.content = convertContent(content);
    }

    private List<Byte> convertContent(byte[] content){
        List<Byte> list = new ArrayList<>();
        for(byte b : content){
            list.add(b);
        }
        return list;
    }
}
