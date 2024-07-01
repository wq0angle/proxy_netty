package com.netty.client.config;

import com.netty.client.entry.ProxyClientEntry;
import com.netty.client.entry.WindowsConfigEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ProxyClientConfig {

    private static final Logger log = LoggerFactory.getLogger(ProxyClientConfig.class);
    @Autowired
    ProxyClientEntry proxyClientEntry;

    @Autowired
    private AppConfig appConfig;

    @Autowired
    private WindowsConfigEntry windowsConfigEntry;

    @Bean
    public String startServerHandler() throws Exception {
        addWindowsConfigEntry();
        addProxyClientEntry();

        //添加程序关闭钩子，处理关闭事件
//        addShutdownHook();
        return "附加服务执行完毕";
    }

    private void addProxyClientEntry() throws Exception {
        int localPort = appConfig.getLocalPort();
        String remoteHost = appConfig.getRemoteHost();
        int remotePort = appConfig.getRemotePort();

        proxyClientEntry.start(localPort,remoteHost,remotePort);
    }

    private void addWindowsConfigEntry(){
        String localHost = "127.0.0.1";
        int localPort = appConfig.getLocalPort();

        windowsConfigEntry.enableProxy(localHost,localPort);
    }

    private void addShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                windowsConfigEntry.disableProxy();
            } catch (Exception e) {
                log.error("关闭代理失败", e);
            }
        }));
    }
}