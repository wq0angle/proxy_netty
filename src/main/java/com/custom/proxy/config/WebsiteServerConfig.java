package com.custom.proxy.config;

import com.custom.proxy.handler.server.StaticWebsiteServerHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Async;

@Configuration
public class WebsiteServerConfig {
    @Bean
    public StaticWebsiteServerHandler websiteServerHandler() throws Exception {
        // 设置http静态网站的代理IP、端口号和静态网站目录
        String ipAddress = "127.0.0.1";
        int port = 5088;
        String websiteDirectory = "/root/blog";
        new StaticWebsiteServerHandler().start(ipAddress, port, websiteDirectory);
        return new StaticWebsiteServerHandler();

    }
}
