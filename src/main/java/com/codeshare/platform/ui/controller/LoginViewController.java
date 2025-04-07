package com.codeshare.platform.ui.controller;

import com.codeshare.platform.dto.JwtAuthResponse;
import com.codeshare.platform.dto.LoginRequest;
import com.codeshare.platform.ui.util.AlertHelper;
import com.codeshare.platform.ui.util.SceneManager;
import com.codeshare.platform.ui.util.SessionManager;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

@Component
public class LoginViewController {

    @Autowired
    private RestTemplate restTemplate;
    
    @Autowired
    private SceneManager sceneManager;
    
    @Autowired
    private SessionManager sessionManager;
    
    @FXML
    private TextField usernameField;
    
    @FXML
    private PasswordField passwordField;
    
    @FXML
    private Label messageLabel;
    
    @FXML
    private void handleLogin() {
        String username = usernameField.getText();
        String password = passwordField.getText();
        
        if (username.isEmpty() || password.isEmpty()) {
            messageLabel.setText("Please enter both username and password");
            return;
        }
        
        try {
            LoginRequest loginRequest = new LoginRequest();
            loginRequest.setUsername(username);
            loginRequest.setPassword(password);
            
            ResponseEntity<JwtAuthResponse> response = restTemplate.postForEntity(
                "http://localhost:8080/api/auth/login", 
                loginRequest, 
                JwtAuthResponse.class
            );
            
            if (response.getBody() != null) {
                sessionManager.setAuthToken(response.getBody().getAccessToken());
                sessionManager.setUsername(username);
                
                // Show main view
                sceneManager.showMainView();
            }
        } catch (HttpClientErrorException.Unauthorized e) {
            messageLabel.setText("Invalid username or password");
        } catch (Exception e) {
            messageLabel.setText("Login failed: " + e.getMessage());
            AlertHelper.showErrorAlert("Login Error", "Failed to login", e.getMessage());
        }
    }
    
    @FXML
    private void handleRegister() {
        try {
            sceneManager.showRegisterView();
        } catch (Exception e) {
            AlertHelper.showErrorAlert("Error", "Failed to open registration form", e.getMessage());
        }
    }
}