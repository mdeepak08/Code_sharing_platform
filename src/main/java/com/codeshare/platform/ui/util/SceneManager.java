package com.codeshare.platform.ui.util;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class SceneManager {

    @Autowired
    private ApplicationContext applicationContext;
    
    // Current main stage
    private Stage mainStage;
    
    public void setMainStage(Stage stage) {
        this.mainStage = stage;
    }
    
    public void showLoginView() throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/LoginView.fxml"));
        loader.setControllerFactory(applicationContext::getBean);
        
        Parent root = loader.load();
        Scene scene = new Scene(root, 400, 300);
        scene.getStylesheets().add(getClass().getResource("/styles/application.css").toExternalForm());
        
        mainStage.setTitle("Login - Code Sharing Platform");
        mainStage.setScene(scene);
        mainStage.show();
    }
    
    public void showMainView() throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/MainView.fxml"));
        loader.setControllerFactory(applicationContext::getBean);
        
        Parent root = loader.load();
        Scene scene = new Scene(root, 1000, 700);
        scene.getStylesheets().add(getClass().getResource("/styles/application.css").toExternalForm());
        
        mainStage.setTitle("Code Sharing Platform");
        mainStage.setScene(scene);
        mainStage.show();
    }
    
    public void showRegisterView() throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/RegisterView.fxml"));
        loader.setControllerFactory(applicationContext::getBean);
        
        Parent root = loader.load();
        Scene scene = new Scene(root, 400, 400);
        scene.getStylesheets().add(getClass().getResource("/styles/application.css").toExternalForm());
        
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Register - Code Sharing Platform");
        stage.setScene(scene);
        stage.showAndWait();
    }
    
    public void showProjectDialog() throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/ProjectView.fxml"));
        loader.setControllerFactory(applicationContext::getBean);
        
        Parent root = loader.load();
        Scene scene = new Scene(root, 500, 400);
        scene.getStylesheets().add(getClass().getResource("/styles/application.css").toExternalForm());
        
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Create Project");
        stage.setScene(scene);
        stage.showAndWait();
    }
}