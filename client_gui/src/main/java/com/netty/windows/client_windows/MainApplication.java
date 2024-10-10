package com.netty.windows.client_windows;

import com.dustinredmond.fxtrayicon.FXTrayIcon;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.MenuItem;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Objects;

@Slf4j
public class MainApplication extends Application {

    @Override
    public void start(Stage primaryStage) throws IOException {
        // 设置窗口图标
        primaryStage.getIcons().add(new Image(Objects.requireNonNull(getClass().getResourceAsStream("/img/main_icon.png")))); // 使用相对路径
        primaryStage.initStyle(StageStyle.DECORATED); // 默认样式
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/view/main-view.fxml"));

        Scene scene = new Scene(fxmlLoader.load());
        primaryStage.setTitle("代理windows客户端");
        primaryStage.setScene(scene);

        // 将应用程序放入系统托盘
        if (FXTrayIcon.isSupported()) {
            addTrayIcon(primaryStage);
            primaryStage.setOnCloseRequest(event -> {
                // 隐藏窗口而不是退出
                primaryStage.hide();
                event.consume();
            });
        } else {
            log.info("系统不支持后台执行该代理客户端程序");
        }

        primaryStage.show();

    }

    private void addTrayIcon(Stage primaryStage) {
        try {
            FXTrayIcon trayIcon = new FXTrayIcon (primaryStage, getClass().getResource("/img/main_icon.png"));

            // 添加系统托盘菜单
            MenuItem openItem = new MenuItem("打开");
            MenuItem exitItem = new MenuItem("退出");

            trayIcon.addMenuItem(openItem);
            trayIcon.addMenuItem(exitItem);
            // 为菜单添加事件处理
            openItem.setOnAction(actionEvent -> {
                primaryStage.show();
            });
            exitItem.setOnAction(actionEvent -> {
                // 退出程序
                System.exit(0);
            });

            // 显示系统托盘
            trayIcon.show();
        } catch (Exception e) {
            log.error("添加系统托盘图标失败", e);
            throw e;
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}