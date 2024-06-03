package com.custom.proxy.util;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.security.SecureRandom;
import java.util.Base64;

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
}

