package com.netty.windows.client_windows.controller;

import com.netty.windows.client_windows.MainApplication;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.TextArea;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class MainController {
    @FXML
    public TextArea mainConsole;

    public void proxyStartButtonClick(ActionEvent actionEvent) {
        appendToConsole("正在启动... \n");
    }

    public void proxyStopButtonClick(ActionEvent actionEvent) {
        appendToConsole("正在关闭... \n");
    }

    public void proxyConfigButtonClick(ActionEvent actionEvent) {
        mainConsole.appendText("打开配置项... \n");
        try {
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/view/config-view.fxml"));

            Parent root = fxmlLoader.load();

            // 获取子控制器
            ConfigController configController = fxmlLoader.getController();
            configController.setMainController(this); // 传递父控制器的引用

            Scene scene = new Scene(root);
            Stage configStage = new Stage();
            configStage.setTitle("代理配置");
            configStage.setScene(scene);

            // 设置为模态窗口
            configStage.initModality(Modality.APPLICATION_MODAL);
            configStage.initOwner(mainConsole.getScene().getWindow()); // 设置主窗口为拥有者

            configStage.showAndWait(); // 显示模态窗口
        }catch (Exception e){
            mainConsole.appendText(e.toString() + "\n");
        }
    }

    public void appendToConsole(String text) {
        mainConsole.appendText(text);
    }
}