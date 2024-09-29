package com.netty.windows.client_windows.entry;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

@Component
@Slf4j
public class WindowsConfigEntry {

    public static void enableProxy(String host, int port) {
        try {
            System.setProperty("file.encoding", "UTF-8");
            String command = "reg add \"HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings\" /v ProxyEnable /t REG_DWORD /d 1 /f";
            Runtime.getRuntime().exec(command);
            command = "reg add \"HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings\" /v ProxyServer /t REG_SZ /d \"" + host + ":" + port + "\" /f";
            Runtime.getRuntime().exec(command);
            log.info("系统代理注册表设置，开启代理，系统代理地址->:{}", host + ":" + port);
        } catch (IOException e) {
            log.error("系统代理注册表设置失败", e);
        }
    }

    public static void disableProxy() {
        try {
            String command = "reg add \"HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings\" /v ProxyEnable /t REG_DWORD /d 0 /f";
            Runtime.getRuntime().exec(command);
            log.info("系统代理注册表设置，关闭代理");
        } catch (IOException e) {
            log.error("系统代理注册表关闭设置失败", e);
        }
    }

}
