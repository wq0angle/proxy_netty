package com.netty.client_proxy.entry;

import android.content.Intent;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import timber.log.Timber;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;

public class VpnServiceEntry extends VpnService {
    private ParcelFileDescriptor vpnInterface = null;
    private Thread vpnThread = null;
    private Socket socket = null;
    private OutputStream outputStream = null;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        setupVpn();

        // 初始化Socket连接
        initializeSocketConnection();

        vpnThread = new Thread(() -> {
            try {
                FileInputStream in = new FileInputStream(vpnInterface.getFileDescriptor());
                byte[] buffer = new byte[1500];
                int length;

                while (true) {
                    length = in.read(buffer);
                    if (length > 0) {
                        sendDataToNetty(buffer, length);
                    }
                }
            } catch (Exception e) {
                Timber.tag("VPN").e(e, "onStartCommand");
            }
        });
        vpnThread.start();
        return START_STICKY;
    }

    private void setupVpn() {
        VpnService.Builder builder = new VpnService.Builder();
        builder.addAddress("10.0.0.2", 24);
        builder.addRoute("0.0.0.0", 0);
        vpnInterface = builder.establish();
    }

    private void initializeSocketConnection() {
        try {
            socket = new Socket("127.0.0.1", 8888);
            outputStream = socket.getOutputStream();
        } catch (Exception e) {
            Timber.tag("VPN").e(e, "Failed to initialize socket connection");
        }
    }

    private void sendDataToNetty(byte[] data, int length) {
        if (outputStream != null) {
            try {
                outputStream.write(data, 0, length);
                outputStream.flush();
            } catch (Exception e) {
                Timber.tag("VPN").e(e, "Failed to send data");
                // 重新初始化连接
                initializeSocketConnection();
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            if (vpnInterface != null) {
                vpnInterface.close();
            }
            if (vpnThread != null) {
                vpnThread.interrupt();
            }
            if (socket != null) {
                socket.close();
            }
        } catch (IOException e) {
            Timber.tag("VPN").e(e, "onDestroy");
        }
    }
}