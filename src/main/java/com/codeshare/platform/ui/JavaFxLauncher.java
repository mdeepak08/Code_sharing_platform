package com.codeshare.platform.ui;

import javafx.application.Application;
import javafx.stage.Stage;

public class JavaFxLauncher {
    public static void main(String[] args) {
        Application.launch(JavaFxApplication.class, args);
    }
    
    public static class JavaFxApplication extends Application {
        @Override
        public void start(Stage primaryStage) {
            // Your JavaFX initialization code
            primaryStage.setTitle("Code Sharing Platform");
            primaryStage.show();
        }
    }
}