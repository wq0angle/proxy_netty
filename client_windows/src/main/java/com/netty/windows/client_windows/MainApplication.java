package com.netty.windows.client_windows;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.IOException;
import java.util.Objects;

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
        primaryStage.show();

    }
}