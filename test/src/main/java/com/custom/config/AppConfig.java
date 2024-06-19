package com.custom.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

@Configuration
@PropertySource("file:${user.dir}/proxy.properties")
@Data
@Component
public class AppConfig {

    @Value("${remote.host}")
    private String remoteHost;

    @Value("${remote.port}")
    private int remotePort;

    @Value("${local.port}")
    private Integer localPort;

    @Value("${ssl.request.enabled}")
    private Boolean sslRequestEnabled;

    @Value("${server.port}")
    private Integer serverPort;

    @Value("${ssl.listener.enabled}")
    private Boolean sslListenerEnabled;

    @Value("${website.directory}")
    private String websiteDirectory;

    @Value("${ssl.jks.path}")
    private String sslJksPath;

    @Value("${ssl.jks.file.password}")
    private String sslJksFilePassword;

    @Value("${sin.default.file}")
    private String sinDefaultFile;
}
