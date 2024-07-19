package com.netty.server.config;

import com.netty.server.handler.ProxyServerHandler;
import com.netty.server.handler.WebsiteServerHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ProxyServerConfig {

    @Autowired
    WebsiteServerHandler websiteServerHandler;

    @Autowired
    ProxyServerHandler proxyServerHandler;

    @Autowired
    private AppConfig appConfig;

    @Bean
    public String startServerHandler() throws Exception {
        websiteServerHandler();
        proxyServerHandler();
//        edgeProxyServerHandler();
        return "附加服务执行完毕";
    }

    public void proxyServerHandler() throws Exception {
        int port = appConfig.getServerPort(); // 设置代理服务器端口号
        proxyServerHandler.start(port);
    }

    public void websiteServerHandler() throws Exception {
        // 设置http静态网站的代理IP、端口号和静态网站目录
        String ipAddress = "127.0.0.1";
        int port =  appConfig.getWebsitePort();
        String websiteDirectory = appConfig.getWebsiteDirectory();
        websiteServerHandler.start(ipAddress, port, websiteDirectory.trim());
    }

}