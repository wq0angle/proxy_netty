package com.netty.windows.client_windows;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import java.io.IOException;

public class HelloApplication extends Application {
    @Override
    public void start(Stage primaryStage) throws IOException {
        Button btn = new Button("点击我");
        btn.setOnAction(event -> {
            FXMLLoader fxmlLoader = new FXMLLoader(HelloApplication.class.getResource("hello-view.fxml"));
            Scene scene;
            try {
                scene = new Scene(fxmlLoader.load(), 320, 240);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            primaryStage.setTitle("跳转示例!");
            primaryStage.setScene(scene);
            primaryStage.show();
        });

        StackPane root = new StackPane();
        root.getChildren().add(btn);

        Scene scene = new Scene(root, 400, 300);
        primaryStage.setTitle("JavaFX 示例");
        primaryStage.setScene(scene);
        primaryStage.show();

    }

    public static void main(String[] args) {
        launch(args);
    }
}