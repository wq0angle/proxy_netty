package com.netty.client_proxy.util;

import android.os.Build;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.util.CharsetUtil;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;

@Data
public class WebSocketUtil {

    private static final SecureRandom RANDOM = new SecureRandom();

    public static String frameHead = "frame-head:";

    public static WebSocketFrame convertToTextWebSocketFrame(FullHttpResponse response) {
        StringBuffer buffer =  new StringBuffer();
        buffer.append(frameHead)
                .append("HTTP/1.1 ")
                .append(response.status().toString())
                .append("\r\n");
        for (Map.Entry<String, String> header : response.headers()) {
            buffer.append(header.getKey())
                    .append(": ")
                    .append(header.getValue())
                    .append("\r\n");
        }
        buffer.append("\r\n")
                .append(response.content().toString(CharsetUtil.UTF_8));
        return new TextWebSocketFrame(buffer.toString());
    }

    public static WebSocketFrame convertToBinaryWebSocketFrame(FullHttpResponse response) {
        ByteBuf content = convertToWebBuffer(response);
        return new BinaryWebSocketFrame(content);
    }

    public static ByteBuf convertToWebBuffer(FullHttpResponse response){
        ByteBuf content = Unpooled.buffer();
        String statusLine = frameHead + "HTTP/1.1 " + response.status().toString() + "\r\n";
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

