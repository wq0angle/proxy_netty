package com.netty.client.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

@Configuration
@PropertySource("file:${user.dir}/proxy-client.properties")
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
}
