package com.netty.client.gui.controller;

import com.netty.client.gui.config.ProxyFileConfig;
import com.netty.client.gui.entry.ProxyClientEntry;
import com.netty.client.gui.entry.WindowsConfigEntry;
import com.netty.client.gui.entity.AppConfig;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.TextArea;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.Objects;

public class MainController {
    @FXML
    public TextArea mainConsole;

    private static TextArea consoleText;

    ProxyClientEntry proxyClientEntry = new ProxyClientEntry();
    @FXML
    public void initialize() {
        consoleText = mainConsole;
    }

    public void proxyStartButtonClick(ActionEvent actionEvent) {
        appendToConsole("正在启动... \n");
        try {
            AppConfig appConfig = ProxyFileConfig.loadFile(ProxyFileConfig.PROXY_CONFIG_FILE_NAME, AppConfig.class);
            if (Objects.isNull(appConfig)){
                throw new Exception("未找到配置文件，请先配置代理参数！\n");
            }
            if (proxyClientEntry.isRunning()) {
                throw new Exception("请勿重复启动代理");
            }

            // 启动代理客户端
            new Thread(() -> {
                try {
                    // 设置注册表配置, 开启系统代理
                    WindowsConfigEntry.enableProxy("127.0.0.1", appConfig.getLocalPort());
                    proxyClientEntry.start(appConfig);
                } catch (Exception e) {
                    appendToConsole("启动失败: " + e.getMessage() + "\n");
                    // 设置注册表配置, 关闭系统代理
                    WindowsConfigEntry.disableProxy();
                }
            }).start();

            appendToConsole("启动完成... \n");
        } catch (Exception e) {
            appendToConsole("启动失败: " + e.getMessage() + "\n");
        }
    }

    public void proxyStopButtonClick(ActionEvent actionEvent) {
        appendToConsole("正在关闭... \n");
        try {
            if (!proxyClientEntry.isRunning()) {
                throw new Exception("请勿重复关闭代理");
            }
            // 关闭代理客户端
            proxyClientEntry.stop();
            appendToConsole("关闭成功... \n");
        }
        catch (Exception e) {
            appendToConsole("关闭失败: " + e.getMessage() + "\n");
        } finally {
            // 设置注册表配置, 关闭系统代理
            WindowsConfigEntry.disableProxy();
        }
    }

    public void proxyConfigButtonClick(ActionEvent actionEvent) {
        appendToConsole("打开配置项... \n");
        try {
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/view/config-view.fxml"));

            Parent root = fxmlLoader.load();

            Scene scene = new Scene(root);
            Stage configStage = new Stage();
            configStage.setTitle("代理配置");
            configStage.setScene(scene);

            // 设置为模态窗口
            configStage.initModality(Modality.APPLICATION_MODAL);
            configStage.initOwner(mainConsole.getScene().getWindow()); // 设置主窗口为拥有者

            configStage.showAndWait(); // 显示模态窗口
        }catch (Exception e){
            appendToConsole(e + "\n");
        }
    }

    public static void appendToConsole(String text) {
        Platform.runLater(() -> consoleText.appendText(text));
    }
}