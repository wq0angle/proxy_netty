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
//        addWindowsConfigEntry();
        addProxyClientEntry();

        return "附加服务执行完毕";
    }

    private void addProxyClientEntry() throws Exception {
        // 设置本地代理的端口，连接远程的地址、端口
        int localPort = appConfig.getLocalPort();
        String remoteHost = appConfig.getRemoteHost();
        int remotePort = appConfig.getRemotePort();

        proxyClientEntry.start(localPort,remoteHost,remotePort);
    }

    private void addWindowsConfigEntry(){
        // 修改windows注册表设置,跟随程序启动而自动启用代理,其本质上和手动修改wifi代理的效果是一样的
        String localHost = "127.0.0.1";
        int localPort = appConfig.getLocalPort();

        windowsConfigEntry.enableProxy(localHost,localPort);
    }

}