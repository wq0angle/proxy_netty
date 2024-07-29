package com.netty.client.entry;

import com.netty.client.config.AppConfig;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
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

    //程序关闭自动调用
    @PreDestroy
    public void disableProxy() {
        try {
            String command = "reg add \"HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings\" /v ProxyEnable /t REG_DWORD /d 0 /f";
            Runtime.getRuntime().exec(command);
            log.info("系统代理注册表设置，关闭代理");
        } catch (IOException e) {
            log.error("系统代理注册表关闭设置失败", e);
        }
    }

    public void enableVPN(String vpnName, String username, String password) {
        try {
            String command = String.format("rasdial \"%s\" %s %s", vpnName, username, password);
            Process process = Runtime.getRuntime().exec(command);

            // 读取输出流
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            StringBuilder output = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }

            int exitCode = process.waitFor();
            log.info("VPN 系统注册表开启命令执行->vpnName:{},username:{},password:{},执行结果:{}, 输出:{}",
                    vpnName, username, password, exitCode, output.toString());
        } catch (Exception e) {
            log.error("VPN 系统注册表开启失败", e);
        }
    }

    public void disableVPN(String vpnName) {
        try {
            String command = String.format("rasdial \"%s\" /DISCONNECT", vpnName);
            Process process = Runtime.getRuntime().exec(command);

            // 读取输出流
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            StringBuilder output = new StringBuilder();
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }

            int exitCode = process.waitFor();
            log.info("VPN 系统注册表关闭命令执行->vpnName:{},执行结果:{}, 输出:{}",
                    vpnName, exitCode, output.toString());
        } catch (Exception e) {
            log.error("VPN 系统注册表关闭失败", e);
        }
    }
}
