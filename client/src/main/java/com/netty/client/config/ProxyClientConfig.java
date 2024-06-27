package com.netty.client.config;

import com.netty.client.handler.ProxyClientHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ProxyClientConfig {

    @Autowired
    ProxyClientHandler proxyClientHandler;

    @Autowired
    private AppConfig appConfig;

    @Bean
    public String startServerHandler() throws Exception {
        proxyServerHandler();
        return "附加服务执行完毕";
    }

    public void proxyServerHandler() throws Exception {
        int localPort = appConfig.getLocalPort();
        String remoteHost = appConfig.getRemoteHost();
        int remotePort = appConfig.getRemotePort();

        proxyClientHandler.start(localPort,remoteHost,remotePort);
    }

}