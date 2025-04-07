package com.codeshare.platform.ui;

import com.codeshare.platform.ui.util.SceneManager;
import javafx.application.Application;
import javafx.stage.Stage;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

public class JavaFxApplication extends Application {
    
    private ConfigurableApplicationContext springContext;
    
    @Override
    public void init() throws Exception {
        springContext = new SpringApplicationBuilder(com.codeshare.platform.CodeSharingPlatformApplication.class)
            .run();
    }
    
    @Override
    public void start(Stage primaryStage) throws Exception {
        // Get the scene manager from Spring context
        SceneManager sceneManager = springContext.getBean(SceneManager.class);
        
        // Set the main stage
        sceneManager.setMainStage(primaryStage);
        
        // Show login view
        sceneManager.showLoginView();
    }
    
    @Override
    public void stop() {
        springContext.close();
    }
    
    public static void main(String[] args) {
        launch(args);
    }
}