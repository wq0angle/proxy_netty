package com.netty.common.util;

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

@Slf4j
@Data
public class WebSocketUtil {

    private static final SecureRandom RANDOM = new SecureRandom();

    // frame头,用以区分 connect请求 的 200 ok 响应 和 其他透传数据流的响应
    public static String frameHead = "frame-head:";

    public static String base64Encode(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    public static String generateWebSocketKey() {
        byte[] keyBytes = new byte[16];
        RANDOM.nextBytes(keyBytes);
        return base64Encode(keyBytes);
    }

    /**
     * 将FullHttpResponse 转换成 TextWebSocketFrame
     */
    public static WebSocketFrame convertToTextWebSocketFrame(FullHttpResponse response) {
        StringBuffer buffer =  new StringBuffer();
        buffer.append(frameHead)
                .append(response.protocolVersion().text())
                .append(" ")
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

    /**
     * 将FullHttpResponse 转换成 BinaryWebSocketFrame
     */
    public static WebSocketFrame convertToBinaryWebSocketFrame(FullHttpResponse response) {
        ByteBuf content = convertToWebBuffer(response);
        return new BinaryWebSocketFrame(content);
    }

    /**
     * 将FullHttpResponse 转换成 ByteBuf
     */
    public static ByteBuf convertToWebBuffer(FullHttpResponse response){
        ByteBuf content = Unpooled.buffer();
        StringBuffer buffer =  new StringBuffer();
        buffer.append(frameHead)
                .append(response.protocolVersion().text())
                .append(" ")
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
        content.writeBytes(buffer.toString().getBytes(CharsetUtil.UTF_8));
        return content;
    }

}

