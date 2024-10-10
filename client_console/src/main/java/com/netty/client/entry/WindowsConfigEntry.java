package com.netty.client.entry;

import com.netty.client.config.AppConfig;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.prefs.Preferences;

@Component
@Slf4j
public class WindowsConfigEntry {

    public void enableProxy(String host, int port) {
        try {
            String osName = System.getProperty("os.name").toLowerCase();
            System.setProperty("file.encoding", "UTF-8");
            if (Strings.isBlank(osName) || osName.contains("win")) {
                String command = "reg add \"HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings\" /v ProxyEnable /t REG_DWORD /d 1 /f";
                Runtime.getRuntime().exec(command);
                command = "reg add \"HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings\" /v ProxyServer /t REG_SZ /d \"" + host + ":" + port + "\" /f";
                Runtime.getRuntime().exec(command);
            }else {
                String command = String.format("networksetup -setwebproxy Wi-Fi %s %d", host, port);
                Runtime.getRuntime().exec(new String[]{"/bin/bash", "-c", command});
                command = String.format("networksetup -setwebproxystate Wi-Fi on");
                Runtime.getRuntime().exec(new String[]{"/bin/bash", "-c", command});
            }
            log.info("系统代理注册表设置，开启代理，系统代理地址->:{}", host + ":" + port);
        } catch (IOException e) {
            log.error("系统代理注册表设置失败", e);
        }
    }

    //程序关闭自动调用
    @PreDestroy
    public void disableProxy() {
        try {
            String osName = System.getProperty("os.name").toLowerCase();
            if (Strings.isBlank(osName) || osName.contains("win")) {
                String command = "reg add \"HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings\" /v ProxyEnable /t REG_DWORD /d 0 /f";
                Runtime.getRuntime().exec(command);
            }else {
                String command = "networksetup -setwebproxystate Wi-Fi off";
                Runtime.getRuntime().exec(new String[]{"/bin/bash", "-c", command});
            }

            log.info("系统代理注册表设置，关闭代理");
        } catch (IOException e) {
            log.error("系统代理注册表关闭设置失败", e);
        }
    }
}
