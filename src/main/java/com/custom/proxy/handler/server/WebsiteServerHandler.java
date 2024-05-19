package com.custom.proxy.handler.server;

import com.sun.net.httpserver.HttpServer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;

@Slf4j
@EnableAsync
@Component
public class WebsiteServerHandler {

    @Async
    public void start(String ipAddress,Integer port,String websiteDirectory) throws IOException {

        HttpServer server = HttpServer.create(new InetSocketAddress(ipAddress, port), 0);
        server.createContext("/", exchange -> {
            String requestPath = exchange.getRequestURI().getPath();
            File file = new File(websiteDirectory + requestPath);
            if (file.exists() && file.isFile()) {
                byte[] content = Files.readAllBytes(file.toPath());
                exchange.sendResponseHeaders(200, content.length);
                exchange.getResponseBody().write(content);
            } else {
                exchange.sendResponseHeaders(404, 0);
            }
            exchange.close();
        });
        server.start();

        log.info("静态网站部署启动 url-> {}:{} | 资源目录:{}", ipAddress, port, websiteDirectory);
    }
}