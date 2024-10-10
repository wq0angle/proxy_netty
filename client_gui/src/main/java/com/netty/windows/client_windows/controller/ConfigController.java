package com.netty.windows.client_windows.controller;

import com.netty.common.enums.ProxyReqEnum;
import com.netty.windows.client_windows.entity.AppConfig;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import com.netty.windows.client_windows.config.ProxyFileConfig;
import org.jetbrains.annotations.NotNull;

import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;

public class ConfigController {
    @FXML
    private ComboBox<String> proxyTypeComboBox;
    @FXML
    private ComboBox<String> sslRequestEnabledComboBox;
    @FXML
    private TextField remoteHostTextField;
    @FXML
    private TextField proxyServerPort;
    @FXML
    private TextField proxyLocalPort;

    @FXML
    public void initialize() {
        // ComboBox 的初始化
        proxyTypeComboBox.getItems().addAll(ProxyReqEnum.listAllNames()); // 动态添加选项
        sslRequestEnabledComboBox.getItems().addAll(TRUE.toString(), FALSE.toString()); // 动态添加选项

        proxyTypeComboBox.setValue(proxyTypeComboBox.getItems().getFirst()); // 默认选择第一项
        sslRequestEnabledComboBox.setValue(sslRequestEnabledComboBox.getItems().getFirst()); // 默认选择第一项

        loadCheckFile();
    }

    public void saveButtonClick(ActionEvent actionEvent) {
        MainController.appendToConsole("保存配置项... \n"); // 更新父界面的 TextArea

        saveFile(); // 保存文件

        // 获取当前的 Stage（窗口） 并关闭
        Stage stage = (Stage) proxyTypeComboBox.getScene().getWindow();
        stage.close();
    }

    public void closeButtonClick(ActionEvent actionEvent) {
        // 获取当前的 Stage（窗口） 并关闭
        Stage stage = (Stage) proxyTypeComboBox.getScene().getWindow();
        MainController.appendToConsole("关闭配置项... \n"); // 更新父界面的 TextArea
        stage.close();
    }

    /**
     * 加载配置文件, 返回是否成功，并设置到控件中
     */
    private void loadCheckFile() {
        try {
            AppConfig appConfig = ProxyFileConfig.loadFile(ProxyFileConfig.PROXY_CONFIG_FILE_NAME, AppConfig.class);
            if (appConfig == null){
                MainController.appendToConsole("未能读取配置文件, 需要创建... \n");
                return;
            }
            String sslRequestEnabledValue = sslRequestEnabledComboBox.getItems().stream()
                    .filter(item -> item.equals(appConfig.getSslRequestEnabled().toString())).findFirst().orElse(null);
            String proxyTypeValue = proxyTypeComboBox.getItems().stream()
                    .filter(item -> item.equals(appConfig.getProxyType())).findFirst().orElse(null);

            if (sslRequestEnabledValue == null || proxyTypeValue == null) {
                throw new Exception("配置文件加载失败，请检查配置文件是否正确！");
            }

            // 将 appConfig 中的值设置到对应的控件中
            proxyTypeComboBox.setValue(proxyTypeValue);
            sslRequestEnabledComboBox.setValue(sslRequestEnabledValue);
            remoteHostTextField.setText(appConfig.getRemoteHost());
            proxyServerPort.setText(appConfig.getRemotePort().toString());
            proxyLocalPort.setText(appConfig.getLocalPort().toString());
        } catch (Exception e) {
            MainController.appendToConsole(e.getMessage() + "\n");
        }
    }

    private void saveFile() {
        // 保存到 properties 文件
        try {
            // 获取控件的值并添加至对象
            AppConfig appConfig = buildProxyConfigDTO();
            ProxyFileConfig.saveFile(appConfig, ProxyFileConfig.PROXY_CONFIG_FILE_NAME, ProxyFileConfig.PROXY_CONFIG_FILE_DESC);
            MainController.appendToConsole("配置已保存到 config.properties 文件中。\n");
        } catch (Exception e) {
            MainController.appendToConsole("请检查配置文件, 保存失败: " + e.getMessage() + "\n");
        }
    }

    // 获取控件的值并添加至对象
    private @NotNull AppConfig buildProxyConfigDTO() {
        AppConfig appConfig = new AppConfig();
        appConfig.setProxyType(proxyTypeComboBox.getValue());
        appConfig.setSslRequestEnabled(Boolean.parseBoolean(sslRequestEnabledComboBox.getValue()));
        appConfig.setRemoteHost(remoteHostTextField.getText());
        appConfig.setRemotePort(Integer.parseInt(proxyServerPort.getText()));
        appConfig.setLocalPort(Integer.parseInt(proxyLocalPort.getText()));
        return appConfig;
    }
}
