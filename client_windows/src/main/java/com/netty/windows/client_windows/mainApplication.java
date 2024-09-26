package com.netty.windows.client_windows;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.IOException;
import java.util.Objects;

public class mainApplication extends Application {
    @Override
    public void start(Stage primaryStage) throws IOException {
//        Button btn = new Button("点击我");
//        btn.setOnAction(event -> {
//            FXMLLoader fxmlLoader = new FXMLLoader(mainApplication.class.getResource("main-view.fxml"));
//            Scene scene;
//            try {
//                scene = new Scene(fxmlLoader.load(), 320, 240);
//            } catch (IOException e) {
//                throw new RuntimeException(e);
//            }
//            primaryStage.setTitle("跳转示例!");
//            primaryStage.setScene(scene);
//            primaryStage.show();
//        });
        // 设置窗口图标
        primaryStage.getIcons().add(new Image(Objects.requireNonNull(getClass().getResourceAsStream("/main_icon.png")))); // 使用相对路径
        primaryStage.initStyle(StageStyle.DECORATED); // 默认样式
        FXMLLoader fxmlLoader = new FXMLLoader(mainApplication.class.getResource("main-view.fxml"));

        Scene scene = new Scene(fxmlLoader.load(), 720, 360);
        primaryStage.setTitle("代理windows客户端");
        primaryStage.setScene(scene);
        primaryStage.show();

    }

    public static void main(String[] args) {
        launch(args);
    }
}