package com.custom.proxy.config;

import com.custom.proxy.handler.server.ProxyServerHandler;
import com.custom.proxy.handler.server.StaticWebsiteServerHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Async;

@Configuration
public class ProxyServerConfig {
    @Bean
    public ProxyServerHandler proxyServerHandler() throws Exception {
        int port = 443; // 设置代理服务器端口号
        new ProxyServerHandler().start(port);
        return new ProxyServerHandler();
    }


}