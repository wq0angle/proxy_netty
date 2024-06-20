package com.netty.custom.config;

import com.netty.custom.handler.server.ProxyServerHandler;
import com.netty.custom.handler.server.WebsiteServerHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class StartHandler implements ApplicationRunner {

    @Autowired
    private ThreadPoolTaskExecutor taskExecutor; // 假设已配置好的TaskExecutor

    @Autowired
    private WebsiteServerHandler websiteServerHandler;

    @Autowired
    private ProxyServerHandler proxyServerHandler;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        websiteServerHandler.start("127.0.0.1", 5088, "/root/blog");
        proxyServerHandler.start(443);
    }
}
