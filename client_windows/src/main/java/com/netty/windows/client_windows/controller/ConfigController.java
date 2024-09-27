package com.netty.windows.client_windows.controller;

import com.netty.common.enums.ProxyReqEnum;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;

import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;

public class ConfigController {

    @FXML
    private ComboBox<String> proxyReqTypeComboBox;
    @FXML
    private ComboBox<String> proxySslTypeComboBox;
    @FXML
    private TextField proxyServerAddress;
    @FXML
    private TextField proxyServerPort;
    @FXML
    private TextField proxyLocalPort;

    @FXML
    public void initialize() {
        // ComboBox 的初始化
        proxyReqTypeComboBox.getItems().addAll(ProxyReqEnum.listAllNames()); // 动态添加选项
        proxySslTypeComboBox.getItems().addAll(TRUE.toString(), FALSE.toString()); // 动态添加选项
    }

        public void saveButtonClick(ActionEvent actionEvent) {
    }

    public void closeButtonClick(ActionEvent actionEvent) {
    }
}
