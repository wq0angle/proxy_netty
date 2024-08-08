package com.netty.server.entry;

import com.sun.net.httpserver.HttpServer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
@EnableAsync
@Component
public class WebsiteServerEntry {

    /**
     * 启动静态网站服务(部署伪装网站, 应对流量嗅探并提高隐蔽性)
     */
    @Async
    public void start(String ipAddress,Integer port,String websiteDirectory) throws IOException {

        HttpServer server = HttpServer.create(new InetSocketAddress(ipAddress, port), 0);
        server.createContext("/", exchange -> {
            try {
                String requestPath = exchange.getRequestURI().getPath();
                //增加安全性检查，以防止目录遍历攻击,比如./xx 或 ../xx
                File file = new File(Path.of(websiteDirectory + requestPath).normalize().toString());
                String filePth = file.getPath().replace("\\", "/");
                if (!filePth.startsWith(websiteDirectory)) {
                    throw new SecurityException("Attempted directory traversal attack");
                }
                if (file.exists() && file.isFile()) {
                    byte[] content = Files.readAllBytes(file.toPath());
                    exchange.sendResponseHeaders(200, content.length);
                    exchange.getResponseBody().write(content);
                } else {
                    exchange.sendResponseHeaders(403, 0);
                }
            } catch (Exception e) {
                log.error("Error handling request", e);
                exchange.sendResponseHeaders(500, 0);
            } finally {
                exchange.close();
            }
        });
        server.start();

        log.info("静态网站部署启动 url-> {}:{} | 资源目录:{}", ipAddress, port, websiteDirectory);
    }
}