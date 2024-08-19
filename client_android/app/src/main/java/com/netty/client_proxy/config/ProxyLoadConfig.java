package com.netty.client_proxy.config;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import com.netty.client_proxy.entity.ProxyConfigDTO;
import com.netty.client_proxy.util.RegexTypeUtil;
import lombok.Getter;
import timber.log.Timber;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.Properties;

public class ProxyLoadConfig {
    @Getter
    private static ProxyConfigDTO proxyConfigDTO = new ProxyConfigDTO();
    @Getter
    private static boolean initialized = false;

    public static void loadProperties(Context context) {
        Properties properties = new Properties();

        // 查询MediaStore以获取文件的URI, 不需要具体子目录，会自动检索文件名
        String selection = MediaStore.Files.FileColumns.DISPLAY_NAME + " = ?";
        String[] selectionArgs = new String[]{ProxySaveConfig.FILE_FULL_NAME};
        ContentResolver contentResolver = context.getContentResolver();

        try (Cursor cursor = contentResolver.query(
                MediaStore.Files.getContentUri("external"),
                null,
                selection,
                selectionArgs,
                null)) {
            if (cursor != null && cursor.moveToFirst()) {
                // 获取文件的URI
                int columnIndex = cursor.getColumnIndex(MediaStore.Files.FileColumns._ID);
                long id = cursor.getLong(columnIndex);
                Uri fileUri = Uri.withAppendedPath(MediaStore.Files.getContentUri("external"), String.valueOf(id));

                // 读取文件内容
                try (InputStream inputStream = contentResolver.openInputStream(fileUri)) {
                    properties.load(inputStream);
                    mapPropertiesToDTO(properties, proxyConfigDTO);
                    initialized = true;
                } catch (IOException e) {
                    initialized = false;
                    Timber.tag("ProxyLoadConfig").e(e, "loadProperties error");
                }
            } else {
                Timber.tag("ProxyLoadConfig").e("loadProperties error 配置文件不存在");
                initialized = false;
            }
        } catch (Exception e) {
            initialized = false;
            Timber.tag("ProxyLoadConfig").e(e, "loadProperties error");
        }
    }

    private static void mapPropertiesToDTO(Properties properties, Object dto) {
        Field[] fields = dto.getClass().getDeclaredFields();
        for (Field field : fields) {
            field.setAccessible(true); // 允许访问私有字段
            String propertyValue = properties.getProperty(field.getName());
            if (propertyValue != null) {
                try {
                    Object value = convertValue(field, propertyValue);
                    field.set(dto, value);
                } catch (IllegalAccessException e) {
                    Timber.tag("ProxyLoadConfig").e(e, "mapPropertiesToDTO error");
                }
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