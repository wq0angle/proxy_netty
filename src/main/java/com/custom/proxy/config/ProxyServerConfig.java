package com.custom.proxy.config;

import com.custom.proxy.handler.server.ProxyServerHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ProxyServerConfig {
    @Bean
    public ProxyServerHandler proxyServerHandler() throws Exception {
        int port = 5088; // 设置您希望的代理服务器端口号
        ProxyServerHandler.start(port);
        return new ProxyServerHandler();
    }
}