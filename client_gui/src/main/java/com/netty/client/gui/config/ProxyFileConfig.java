package com.netty.client.gui.config;

import java.io.*;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

public class ProxyFileConfig {

    public static final String PROXY_CONFIG_FILE_NAME = "proxy_windows_config.txt";
    public static final String PROXY_CONFIG_FILE_DESC = "# GUI代理客户端配置文件";
    public static void saveFile(Object dto,String fileName,String desc) throws IllegalAccessException {
        // 获取程序根目录
        String rootDirectory = System.getProperty("user.dir");
        File file = new File(rootDirectory, fileName); // 在根目录下创建文件

        // 使用 BufferedWriter 手动写入文件
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
            writer.write(desc);
            writer.newLine();
            Field[] fields = dto.getClass().getDeclaredFields();
            for (Field field : fields) {
                field.setAccessible(true); // 允许访问私有字段
                Object value = field.get(dto);
                if (value != null) {
                    // 保存properties文件内容格式
                    writer.write(field.getName() + "=" + value);
                    writer.newLine();
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static <T> T loadFile(String fileName, Class<T> clazz) {
        // 获取程序根目录
        String rootDirectory = System.getProperty("user.dir");
        File file = new File(rootDirectory, fileName); // 在根目录下创建文件

        // 从 properties 文件加载配置，使用 UTF-8 编码
        Properties properties = new Properties();
        try (InputStreamReader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException e) {
            return null;
        }

        // 创建对象并设置属性值
        try {
            T dto = clazz.getDeclaredConstructor().newInstance(); // 创建对象实例
            mapPropertiesToDTO(properties, dto); // 将属性映射到对象
            return dto; // 返回填充好的对象
        } catch (Exception e) {
            throw new RuntimeException("配置文件属性填充失败: " + e.getMessage(), e);
        }
    }

    private static void mapPropertiesToDTO(Properties properties, Object dto) throws IllegalAccessException {
        Field[] fields = dto.getClass().getDeclaredFields();
        for (Field field : fields) {
            field.setAccessible(true); // 允许访问私有字段
            String propertyValue = properties.getProperty(field.getName());
            if (propertyValue != null) {
                Object value = convertValue(field, propertyValue);
                field.set(dto, value);
            }
        }
    }

    private static Object convertValue(Field field, String value) {
        if (field.getType() == String.class) {
            return value;
        } else if (field.getType() == int.class || field.getType() == Integer.class) {
            return Integer.valueOf(value);
        } else if (field.getType() == boolean.class || field.getType() == Boolean.class) {
            return Boolean.valueOf(value);
        }

        String caseStr = String.format("配置类型错误，fieldName: %s, value: %s", field.getName(), value);
        throw new IllegalArgumentException(caseStr); // 或抛出异常
    }
}
