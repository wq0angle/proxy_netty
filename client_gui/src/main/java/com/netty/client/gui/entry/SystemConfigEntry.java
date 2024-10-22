package com.netty.client.gui.entry;

import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Slf4j
public class SystemConfigEntry {
    /**
     * 开启系统代理,连接至本地代理客户端
     * @param host 连接地址
     * @param port 连接端口
     */
    public static void enableProxy(String host, int port) {
        try {
            //获取系统变量名，用以区分操作系统
            String osName = System.getProperty("os.name").toLowerCase();
            System.setProperty("file.encoding", "UTF-8");
            //如果是windows操作系统, 执行注册表设置
            if (Strings.isBlank(osName) || osName.contains("win")) {
                String command = "reg add \"HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings\" /v ProxyEnable /t REG_DWORD /d 1 /f";
                Runtime.getRuntime().exec(command);
                command = "reg add \"HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings\" /v ProxyServer /t REG_SZ /d \"" + host + ":" + port + "\" /f";
                Runtime.getRuntime().exec(command);
            }
            // 如果是mac系统，执行shell脚本
            else {
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

    //关闭系统代理设置,不再连接到本地代理客户端
    public static void disableProxy() {
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
