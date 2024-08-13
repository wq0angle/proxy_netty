package com.netty.client_proxy.entry;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.IpPrefix;
import android.net.ProxyInfo;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import com.netty.client_proxy.config.AppConfig;
import timber.log.Timber;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;

public class VpnServiceEntry extends VpnService {
    private ParcelFileDescriptor vpnInterface = null;
    AppConfig appConfig = new AppConfig();

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            setupVpn();
        } catch (PackageManager.NameNotFoundException e) {
            throw new RuntimeException(e);
        }
        if (vpnInterface == null) {
            Timber.tag("VPN").e("VPN 服务未启动");
            stopSelf();  // 停止服务
            return START_NOT_STICKY;
        }
        return START_STICKY;
    }

    private void setupVpn() throws PackageManager.NameNotFoundException {
        VpnService.Builder builder = new VpnService.Builder();
        builder.addAddress("10.0.0.2", 24);
//        // 添加默认路由，允许所有流量
//        builder.addRoute("0.0.0.0", 0);
//        builder.addDisallowedApplication("com.netty.client_proxy");
        builder.setHttpProxy(ProxyInfo.buildDirectProxy("127.0.0.1", appConfig.getLocalPort()));
        vpnInterface = builder.establish();
        if (vpnInterface == null) {
            Timber.tag("VPN").e("Failed to establish VPN");
        }
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            if (vpnInterface != null) {
                vpnInterface.close();
            }
        } catch (IOException e) {
            Timber.tag("VPN").e(e, "onDestroy");
        }
    }
}