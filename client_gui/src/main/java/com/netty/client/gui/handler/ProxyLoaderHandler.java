package com.netty.client.gui.handler;

import com.netty.client.gui.entity.AppConfig;
import com.netty.common.enums.ProxyReqEnum;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.util.internal.StringUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.regex.Pattern;

@Slf4j
public class ProxyLoaderHandler extends SimpleChannelInboundHandler<FullHttpRequest> {

    private AppConfig appConfig;

    public ProxyLoaderHandler(AppConfig appConfig) {
        this.appConfig = appConfig;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, FullHttpRequest request) throws Exception {
        String host = request.uri();
        if (getWhiteFilterFlag(appConfig.getWhiteAddress(), host)) {
            // 白名单过滤，本地转发
            ctx.pipeline().addLast(new LocalProxyHandler(appConfig));
        } else {
            // 根据设置,选择代理请求类型
            if (ProxyReqEnum.parse(appConfig.getProxyType()).equals(ProxyReqEnum.HTTP)) {
                //http 代理
                ctx.pipeline().addLast(new FillProxyHandler(appConfig.getRemoteHost(), appConfig.getRemotePort(), appConfig));
            } else if (ProxyReqEnum.parse(appConfig.getProxyType()).equals(ProxyReqEnum.WEBSOCKET)) {
                //websocket 代理
                ctx.pipeline().addLast(new FillWebSocketProxyHandler(appConfig));
            } else {
                log.error("请检查配置, 不支持的代理类型: {}", appConfig.getProxyType());
            }
        }

        ctx.fireChannelRead(request.retain()); // 增加引用计数并继续传递
        removeCheckHttpHandler(ctx, this.getClass());
    }

    private void removeCheckHttpHandler(ChannelHandlerContext ctx, Class<? extends ChannelHandler> clazz) {
        if (ctx.pipeline().get(clazz) != null){
            log.debug("remove check http handler");
            ctx.pipeline().remove(clazz);
        }
    }

    private static boolean getWhiteFilterFlag(String whiteAddress, String host) {
        if (StringUtil.isNullOrEmpty(whiteAddress)) {
            return false;
        }

        // 提取主机名部分
        String hostName = host.replace("http://","")
                .replace("https://","")
                .split("/")[0]; // 获取主机名，例如 https://www.domain.com:443/path1/path2 -> www.domain.com:443
        String[] whiteList = whiteAddress.split(";");

        for (String pattern : whiteList) {
            // 将通配符转换为正则表达式
            String regex = pattern
                    .replace(".", "\\.") // 转义点
                    .replace("*", ".*"); // 将通配符*替换为.*

            // 添加正则表达式以匹配主机名
            regex = "^" + regex + "(?::\\d+)?$"; // 匹配可选的端口

            // 创建正则表达式模式
            Pattern compiledPattern = Pattern.compile(regex);

            // 检查 hostName 是否匹配
            if (compiledPattern.matcher(hostName).matches()) {
                return true; // 找到匹配的白名单规则
            }
        }
        return false; // 没有匹配的规则
    }

}
