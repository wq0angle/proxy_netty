package com.netty.client_proxy.util;

public class RegexTypeUtil {

    // 正则表达式判断字符串是否为整数
    private static final String INTEGER_REGEX = "-?\\d+";

    // 正则表达式判断字符串是否为布尔值
    private static final String BOOLEAN_REGEX = "true|false";

    // 判断字符串是否为有效字符串（非null且非空）
    public static boolean isValidString(String str) {
        return str != null && !str.trim().isEmpty();
    }

    // 判断字符串是否可以转换为int
    public static boolean isInteger(String str) {
        return isValidString(str) && str.matches(INTEGER_REGEX);
    }

    // 判断字符串是否可以转换为boolean
    public static boolean isBoolean(String str) {
        return isValidString(str) && str.matches(BOOLEAN_REGEX);
    }
}