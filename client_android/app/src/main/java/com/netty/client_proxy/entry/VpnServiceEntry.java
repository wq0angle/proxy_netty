package com.netty.client_proxy.entry;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.ProxyInfo;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import com.netty.client_proxy.config.ProxyLoadConfig;
import com.netty.client_proxy.entity.ProxyConfigDTO;
import timber.log.Timber;

import java.io.*;

public class VpnServiceEntry extends VpnService {
    private ParcelFileDescriptor vpnInterface = null;
    ProxyConfigDTO proxyConfigDTO = ProxyLoadConfig.getProxyConfigDTO();

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            if (intent != null && intent.getBooleanExtra("stop", false)) {
                Timber.tag("VPN").i("Stopping VPN service...");
                if (vpnInterface != null) {
                    vpnInterface.close();  // 自定义方法来停止 VPN
                }
                stopSelf();
                return START_NOT_STICKY;
            }
            setupVpn();
        }  catch (IOException | PackageManager.NameNotFoundException e) {
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
        builder.setHttpProxy(ProxyInfo.buildDirectProxy("127.0.0.1", proxyConfigDTO.getLocalPort()));
        vpnInterface = builder.establish();
        if (vpnInterface == null) {
            Timber.tag("VPN").e("Failed to establish VPN");
        }
    }

    @Override
    public void onDestroy() {
        try {
            if (vpnInterface != null) {
                vpnInterface.close();
            }
        } catch (IOException e) {
            Timber.tag("VPN").e(e, "onDestroy");
        }
        super.onDestroy();
    }

}