package com.netty.client_proxy.ui.fragment;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.appcompat.app.AlertDialog;
import com.netty.client_proxy.R;
import com.netty.client_proxy.config.ProxyLoadConfig;
import com.netty.client_proxy.config.ProxySaveConfig;
import com.netty.client_proxy.entity.ProxyConfigDTO;
import com.netty.client_proxy.enums.ProxyReqEnum;
import timber.log.Timber;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class ProxyConfigFragment extends DialogFragment {

    private EditText textRemoteIp;
    private EditText textRemotePort;
    private EditText textLocalNettyPort;
    private Spinner spinnerProxyType;
    private Spinner spinnerSslType;

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        // 使用LayoutInflater来加载自定义布局
        LayoutInflater inflater = requireActivity().getLayoutInflater();
        View view = inflater.inflate(R.layout.proxy_config, null);

        // 初始化加载组件
        loadComponent(view);

        //加载配置文件数据到组件
        loadComponentDataByConfig();

        // 创建对话框
        AlertDialog.Builder builder = new AlertDialog.Builder(requireActivity());
        builder.setView(view)
                .setPositiveButton("确认", (dialog, id) -> saveConfig())
                .setNegativeButton("取消", (dialog, id) -> dialog.dismiss());

        return builder.create();
    }

    private void saveConfig(){
        // 获取控件的值
        String remoteIp = textRemoteIp.getText().toString().trim();
        String remotePort = textRemotePort.getText().toString().trim();
        String localPort = textLocalNettyPort.getText().toString().trim();
        String proxyType = spinnerProxyType.getSelectedItem().toString();
        String sslType = spinnerSslType.getSelectedItem().toString();

        ProxyConfigDTO proxyConfigDTO = new ProxyConfigDTO();
        proxyConfigDTO.setRemoteHost(remoteIp);
        proxyConfigDTO.setRemotePort(Integer.parseInt(remotePort));
        proxyConfigDTO.setLocalPort(Integer.parseInt(localPort));
        proxyConfigDTO.setProxyType(proxyType);
        proxyConfigDTO.setSslRequestEnabled(Boolean.parseBoolean(sslType));

        //保存配置
        new ProxySaveConfig(requireActivity()).saveProperties(proxyConfigDTO);
    }

    private void loadComponent(View view){
        // 初始化控件
        textRemoteIp = view.findViewById(R.id.textRemoteIp);
        textRemotePort = view.findViewById(R.id.textRemotePort);
        textLocalNettyPort = view.findViewById(R.id.textLocalNettyPort);
        spinnerProxyType = view.findViewById(R.id.spinnerProxyType);
        spinnerSslType = view.findViewById(R.id.spinnerSslType);

        // 设置代理类型选择器初始数据
        List<String> proxyTypeList = ProxyReqEnum.listProxyTypeNames();
        ArrayAdapter<String> proxyTypeAdapter = new ArrayAdapter<>(getContext(),
                android.R.layout.simple_spinner_item, proxyTypeList);
        proxyTypeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerProxyType.setAdapter(proxyTypeAdapter);
        //设置开启Ssl类型选择器初始数据
        List<String> sslTypeList = Arrays.asList("true", "false");
        ArrayAdapter<String> sslAdapter = new ArrayAdapter<>(getContext(),
                android.R.layout.simple_spinner_item, sslTypeList);
        sslAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerSslType.setAdapter(sslAdapter);
    }

    private void loadComponentDataByConfig(){
        ProxyLoadConfig.loadProperties(requireActivity());
        if (!ProxyLoadConfig.isInitialized()){
            Timber.tag("ProxyConfigFragment").e(new Throwable("配置文件初始加载错误"),"loadComponentDataByConfig");
            return;
        }
        ProxyConfigDTO proxyConfigDTO = ProxyLoadConfig.getProxyConfigDTO();
        textRemoteIp.setText(proxyConfigDTO.getRemoteHost());
        textRemotePort.setText(String.valueOf(proxyConfigDTO.getRemotePort()));
        textLocalNettyPort.setText(String.valueOf(proxyConfigDTO.getLocalPort()));
    }
}