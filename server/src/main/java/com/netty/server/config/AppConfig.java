package com.netty.server.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

@Configuration
@PropertySource("file:${user.dir}/proxy-server.properties")
@Data
@Component
public class AppConfig {
    @Value("${server.port}")
    private Integer serverPort;

    @Value("${ssl.listener.enabled}")
    private Boolean sslListenerEnabled;

    @Value("${website.directory}")
    private String websiteDirectory;

    @Value("${website.port}")
    private Integer websitePort;

    @Value("${ssl.jks.path}")
    private String sslJksPath;

    @Value("${ssl.jks.file.password}")
    private String sslJksFilePassword;

    @Value("${sin.default.file}")
    private String sinDefaultFile;
}
