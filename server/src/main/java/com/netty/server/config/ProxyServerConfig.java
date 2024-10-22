package com.netty.server.config;

import com.netty.server.entry.ProxyServerEntry;
import com.netty.server.entry.WebsiteServerEntry;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ProxyServerConfig {

    @Autowired
    WebsiteServerEntry websiteServerEntry;

    @Autowired
    ProxyServerEntry proxyServerEntry;

    @Autowired
    private AppConfig appConfig;

    @Bean
    public String startServerHandler() throws Exception {
        websiteServerHandler();
        proxyServerHandler();
        return "附加服务执行完毕";
    }

    public void proxyServerHandler() throws Exception {
        // 设置代理服务器端口号
        int port = appConfig.getServerPort();
        // 启动代理服务端服务
        proxyServerEntry.start(port);
    }

    public void websiteServerHandler() throws Exception {
        // 设置http静态网站的代理IP、端口号和静态网站目录
        String ipAddress = "127.0.0.1";
        int port = appConfig.getWebsitePort();
        String websiteDirectory = appConfig.getWebsiteDirectory();
        // 启动伪装网站服务
        websiteServerEntry.start(ipAddress, port, websiteDirectory.trim());
    }

}