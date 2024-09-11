package com.netty.windows.client_windows.controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.TextArea;

public class mainController {
    @FXML
    public TextArea mainConsole;

    public void proxyStartButtonClick(ActionEvent actionEvent) {
        mainConsole.appendText("正在启动... \n");
    }

    public void proxyStopButtonClick(ActionEvent actionEvent) {
        mainConsole.appendText("正在关闭... \n");
    }

    public void proxyConfigButtonClick(ActionEvent actionEvent) {
        mainConsole.appendText("打开配置项... \n");
    }
}