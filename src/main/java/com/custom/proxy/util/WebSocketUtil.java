package com.custom.proxy.util;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.util.CharsetUtil;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;

@Slf4j
@Data
public class WebSocketUtil {

    private static final SecureRandom RANDOM = new SecureRandom();

    public static String base64Encode(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    public static String generateWebSocketKey() {
        byte[] keyBytes = new byte[16];
        RANDOM.nextBytes(keyBytes);
        return base64Encode(keyBytes);
    }

    public static WebSocketFrame convertToWebSocketFrame(FullHttpResponse response) {
        ByteBuf content = convertToWebBuffer(response);
        return new BinaryWebSocketFrame(content);
    }

    public static ByteBuf convertToWebBuffer(FullHttpResponse response){
        ByteBuf content = Unpooled.buffer();
        String statusLine = "HTTP/1.1 " + response.status().toString() + "\r\n";
        content.writeBytes(statusLine.getBytes(CharsetUtil.UTF_8));
        for (Map.Entry<String, String> header : response.headers()) {
            String headerLine = header.getKey() + ": " + header.getValue() + "\r\n";
            content.writeBytes(headerLine.getBytes(CharsetUtil.UTF_8));
        }
        content.writeBytes("\r\n".getBytes(CharsetUtil.UTF_8)); // HTTP头部和正文之间的空行
        content.writeBytes(response.content()); // HTTP响应正文
        return content;
    }

}

