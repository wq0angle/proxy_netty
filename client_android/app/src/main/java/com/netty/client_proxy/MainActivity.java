package com.netty.client_proxy;

import android.app.IntentService;
import android.content.Intent;
import android.net.VpnService;
import android.os.Bundle;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.DialogFragment;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.snackbar.Snackbar;
import com.netty.client_proxy.config.ProxyLoadConfig;
import com.netty.client_proxy.databinding.ActivityMainBinding;
import com.netty.client_proxy.entry.ProxyClientEntry;
import com.netty.client_proxy.entry.VpnServiceEntry;
import com.netty.client_proxy.test.ProxyReqMain;
import com.netty.client_proxy.ui.fragment.ProxyConfigFragment;
import timber.log.Timber;

public class MainActivity extends AppCompatActivity {

    private AppBarConfiguration mAppBarConfiguration;
    private ActivityMainBinding binding;
    ProxyClientEntry proxyClientEntry = new ProxyClientEntry();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Timber.plant(new Timber.DebugTree());

        setContentView(R.layout.activity_main);

        ProxyLoadConfig.loadProperties(getApplication());

        // 获取按钮的引用
        Button startVpnButton = findViewById(R.id.startVpnButton);
        Button stopVpnButton = findViewById(R.id.stopVpnButton);
        Button openConfigButton = findViewById(R.id.openConfigButton);
        Button startTestButton = findViewById(R.id.startTestButton);

        //netty测试
        startTestButton.setOnClickListener(v -> {
            if (!proxyClientEntry.isRunning()){
                Snackbar.make(findViewById(R.id.startTestButton), "未开启代理，不能测试连接", Snackbar.LENGTH_SHORT).show();
                return;
            }
            new Thread(() -> ProxyReqMain.startReq(this)) .start();
        });

        // 设置启动VPN按钮的点击监听器
        startVpnButton.setOnClickListener(v -> {
            if (!ProxyLoadConfig.isInitialized()){
                Snackbar.make(findViewById(R.id.startVpnButton), "代理配置未进行设置保存", Snackbar.LENGTH_SHORT).show();
                Timber.tag("VPN").e( "代理未读取到配置");
                return;
            }
            if (proxyClientEntry.isRunning()){
                Snackbar.make(findViewById(R.id.startTestButton), "请勿重复开启", Snackbar.LENGTH_SHORT).show();
                return;
            }
            new Thread(() -> proxyClientEntry.start()).start();
            startVpnService();  // 调用启动VPN服务的方法
        });

        // 设置停止VPN按钮的点击监听器
        stopVpnButton.setOnClickListener(v -> {
            proxyClientEntry.stop();
            stopVpnService();  // 调用停止VPN服务的方法
        });

        openConfigButton.setOnClickListener(v -> {
            DialogFragment newFragment = new ProxyConfigFragment();
            newFragment.show(getSupportFragmentManager(), "dialog");
        });
    }
    private void startVpnService() {
        Intent intent = VpnService.prepare(this);
        if (intent != null) {
            // 用户尚未授权，需要请求授权
            startActivityForResult(intent, 0);
        } else {
            // 用户已经授权，可以直接启动服务
            Intent serviceIntent = new Intent(this, VpnServiceEntry.class);
            startService(serviceIntent);
        }
        Snackbar.make(findViewById(R.id.startVpnButton), "VPN 服务已开启", Snackbar.LENGTH_SHORT).show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 0 && resultCode == RESULT_OK) {
            // 用户授权成功，启动 VPN 服务
            Intent serviceIntent = new Intent(this, VpnServiceEntry.class);
            super.startService(serviceIntent);
        } else {
            // 用户拒绝授权或取消，可以适当处理
            Timber.tag("VPN").e("VPN 授权取消");
        }
    }

    private void stopVpnService() {
        // 实现停止VPN服务的逻辑
        try {
            Intent serviceIntent = new Intent(this, VpnServiceEntry.class);
            serviceIntent.putExtra("stop",true);
            super.startService(serviceIntent);
            Snackbar.make(findViewById(R.id.stopVpnButton), "VPN 服务已停止", Snackbar.LENGTH_SHORT).show();
        }catch (Exception e){
            Timber.tag("VPN").e(e, "VPN 服务停止失败");
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onSupportNavigateUp() {
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        return NavigationUI.navigateUp(navController, mAppBarConfiguration)
                || super.onSupportNavigateUp();
    }
}