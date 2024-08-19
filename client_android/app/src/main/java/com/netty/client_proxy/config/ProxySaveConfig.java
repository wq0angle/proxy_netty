package com.netty.client_proxy.config;

import android.app.Application;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import lombok.Getter;
import timber.log.Timber;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.util.Properties;

public class ProxySaveConfig {
    private final Context context;
    private final static String FILE_NAME = "proxy_netty_config";
    public final static String FILE_FULL_NAME = FILE_NAME + ".txt";

    public ProxySaveConfig(Context context) {
        this.context = context.getApplicationContext(); // 使用应用上下文
    }

    //将DTO的字段值保存到Properties文件
    public void saveProperties(Object dto) {
        Properties properties = new Properties();
        Field[] fields = dto.getClass().getDeclaredFields();
        for (Field field : fields) {
            field.setAccessible(true); // 允许访问私有字段
            try {
                Object value = field.get(dto);
                if (value != null) {
                    properties.setProperty(field.getName(), value.toString());
                }
            } catch (IllegalAccessException e) {
                Timber.tag("ProxySaveConfig").e(e, "saveProperties error");
            }
        }

        //保存文件
        saveFile(properties);
    }

    // 使用MediaStore保存文件
    private void saveFile(Properties properties){
        try {
            // 查询文件是否存在
            String selection = MediaStore.Files.FileColumns.DISPLAY_NAME + " = ?";
            String[] selectionArgs = new String[]{FILE_FULL_NAME};
            Uri contentUri = MediaStore.Files.getContentUri("external");

            try (Cursor cursor = context.getContentResolver().query(contentUri, null, selection, selectionArgs, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    // 文件存在，获取其 URI
                    int columnIndex = cursor.getColumnIndex(MediaStore.Files.FileColumns._ID);
                    long id = cursor.getLong(columnIndex);
                    Uri existingFileUri = Uri.withAppendedPath(contentUri, String.valueOf(id));

                    // 删除旧文件
                    context.getContentResolver().delete(existingFileUri, null, null);
                }
            }

            // 保存新文件
            ContentValues values = new ContentValues();
            values.put(MediaStore.Files.FileColumns.DISPLAY_NAME, FILE_NAME);
            values.put(MediaStore.Files.FileColumns.MIME_TYPE, "text/plain");
            values.put(MediaStore.Files.FileColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/netty_proxy");

            Uri uri = context.getContentResolver().insert(MediaStore.Files.getContentUri("external"), values);
            if (uri != null) {
                try (OutputStream outputStream = context.getContentResolver().openOutputStream(uri)) {
                    if (outputStream != null) {
                        properties.store(outputStream, null);
                    }
                } catch (IOException e) {
                    Timber.tag("ProxySaveConfig").e(e, "saveProperties IO error");
                }
            } else {
                Timber.tag("ProxySaveConfig").e("Failed to create new MediaStore entry");
            }
        }catch (Exception e){
            Timber.tag("ProxySaveConfig").e(e, "saveProperties error");
        }
    }
}
