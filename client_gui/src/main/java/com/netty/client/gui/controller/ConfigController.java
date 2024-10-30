package com.netty.client.gui.controller;

import com.netty.client.gui.config.ProxyFileConfig;
import com.netty.common.enums.ProxyReqEnum;
import com.netty.client.gui.entity.AppConfig;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;

import javafx.util.Duration;
import org.jetbrains.annotations.NotNull;

import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;

public class ConfigController {
    @FXML
    public Label infoLabel;
    @FXML
    private TextArea whiteAddress;
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
        // 创建 Tooltip
        Tooltip tooltip = new Tooltip("请输入白名单地址，多个地址用分号 (;) 分隔。" + "\r\n" +
                "可以使用通配符进行匹配配置，通配符使用 * ，可以兼容 域名 / IP" + "\r\n" +
                "如: 使用 *domain.com 可以匹配到 www.domain.com 、 domain.com/path1/path2 等等，依次类推" + "\r\n" +
                "如: 使用 192.168* 可以匹配到 192.168.1.100 、 192.168.100.100/path1/path2 等等，依次类推");
        tooltip.setShowDelay(Duration.seconds(0.1)); // 显示延迟
        tooltip.setHideDelay(Duration.seconds(30)); // 隐藏延迟

        Tooltip.install(infoLabel, tooltip); // 将 Tooltip 安装到问号标签上

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
            // @n 处理换行 转义为 \n
            whiteAddress.setText((appConfig.getWhiteAddress() + "").replaceAll("@n", "\n"));
        } catch (Exception e) {
            MainController.appendToConsole(e.getMessage() + "\n");
        }
    }

    // 保存到 properties 文件
    private void saveFile() {
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
        // \n 转义替换为 @n 处理换行存储以兼容 properties 文件
        appConfig.setWhiteAddress((whiteAddress.getText() + "").replaceAll("\n","@n"));
        return appConfig;
    }
}
