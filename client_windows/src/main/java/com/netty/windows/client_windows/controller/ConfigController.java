package com.netty.windows.client_windows.controller;

import com.netty.common.enums.ProxyReqEnum;
import com.netty.windows.client_windows.entity.ProxyConfigDTO;
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
    // 设置父控制器
    private MainController mainController; // 父控制器引用
    public void setMainController(MainController mainController) {
        this.mainController = mainController;
        //初始化控件
        init();
    }

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

    public void init() {
        // ComboBox 的初始化
        proxyTypeComboBox.getItems().addAll(ProxyReqEnum.listAllNames()); // 动态添加选项
        sslRequestEnabledComboBox.getItems().addAll(TRUE.toString(), FALSE.toString()); // 动态添加选项

        proxyTypeComboBox.setValue(proxyTypeComboBox.getItems().getFirst()); // 默认选择第一项
        sslRequestEnabledComboBox.setValue(sslRequestEnabledComboBox.getItems().getFirst()); // 默认选择第一项

        loadCheckFile();
    }

    public void saveButtonClick(ActionEvent actionEvent) {
        if (mainController != null) {
            mainController.appendToConsole("保存配置项... \n"); // 更新父界面的 TextArea
        }

        saveFile(); // 保存文件

        // 获取当前的 Stage（窗口） 并关闭
        Stage stage = (Stage) proxyTypeComboBox.getScene().getWindow();
        stage.close();
    }

    public void closeButtonClick(ActionEvent actionEvent) {
        // 获取当前的 Stage（窗口） 并关闭
        Stage stage = (Stage) proxyTypeComboBox.getScene().getWindow();
        mainController.appendToConsole("关闭配置项... \n"); // 更新父界面的 TextArea
        stage.close();
    }

    /**
     * 加载配置文件, 返回是否成功，并设置到控件中
     */
    private void loadCheckFile() {
        try {
            ProxyConfigDTO proxyConfigDTO = ProxyFileConfig.loadFile(ProxyFileConfig.PROXY_CONFIG_FILE_NAME, ProxyConfigDTO.class);
            if (proxyConfigDTO == null){
                mainController.appendToConsole("未能读取配置文件, 需要创建... \n");
                return;
            }
            String sslRequestEnabledValue = sslRequestEnabledComboBox.getItems().stream()
                    .filter(item -> item.equals(proxyConfigDTO.getSslRequestEnabled().toString())).findFirst().orElse(null);
            String proxyTypeValue = proxyTypeComboBox.getItems().stream()
                    .filter(item -> item.equals(proxyConfigDTO.getProxyType())).findFirst().orElse(null);

            if (sslRequestEnabledValue == null || proxyTypeValue == null) {
                throw new Exception("配置文件加载失败，请检查配置文件是否正确！");
            }

            // 将 proxyConfigDTO 中的值设置到对应的控件中
            proxyTypeComboBox.setValue(proxyTypeValue);
            sslRequestEnabledComboBox.setValue(sslRequestEnabledValue);
            remoteHostTextField.setText(proxyConfigDTO.getRemoteHost());
            proxyServerPort.setText(proxyConfigDTO.getRemotePort().toString());
            proxyLocalPort.setText(proxyConfigDTO.getLocalPort().toString());
        } catch (Exception e) {
            mainController.appendToConsole(e.getMessage() + "\n");
        }
    }

    private void saveFile() {
        // 保存到 properties 文件
        try {
            // 获取控件的值并添加至对象
            ProxyConfigDTO proxyConfigDTO = buildProxyConfigDTO();
            ProxyFileConfig.saveFile(proxyConfigDTO, ProxyFileConfig.PROXY_CONFIG_FILE_NAME, ProxyFileConfig.PROXY_CONFIG_FILE_DESC);
            mainController.appendToConsole("配置已保存到 config.properties 文件中。\n");
        } catch (Exception e) {
            mainController.appendToConsole("请检查配置文件, 保存失败: " + e.getMessage() + "\n");
        }
    }

    // 获取控件的值并添加至对象
    private @NotNull ProxyConfigDTO buildProxyConfigDTO() {
        ProxyConfigDTO proxyConfigDTO = new ProxyConfigDTO();
        proxyConfigDTO.setProxyType(proxyTypeComboBox.getValue());
        proxyConfigDTO.setSslRequestEnabled(Boolean.parseBoolean(sslRequestEnabledComboBox.getValue()));
        proxyConfigDTO.setRemoteHost(remoteHostTextField.getText());
        proxyConfigDTO.setRemotePort(Integer.parseInt(proxyServerPort.getText()));
        proxyConfigDTO.setLocalPort(Integer.parseInt(proxyLocalPort.getText()));
        return proxyConfigDTO;
    }
}
