module com.netty.windows.client_windows {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.web;

    requires org.controlsfx.controls;
    requires com.dlsc.formsfx;
    requires net.synedra.validatorfx;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.bootstrapfx.core;
    requires eu.hansolo.tilesfx;
    requires com.almasb.fxgl.all;

    opens com.netty.windows.client_windows to javafx.fxml;
    exports com.netty.windows.client_windows;
    exports com.netty.windows.client_windows.controller;
    opens com.netty.windows.client_windows.controller to javafx.fxml;
}