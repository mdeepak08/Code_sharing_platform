package com.codeshare.platform.ui.controller;

import com.codeshare.platform.model.Project;
import com.codeshare.platform.model.User;
import com.codeshare.platform.service.ProjectService;
import com.codeshare.platform.ui.util.AlertHelper;
import com.codeshare.platform.ui.util.SessionManager;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ProjectViewController {

    @Autowired
    private ProjectService projectService;
    
    @Autowired
    private SessionManager sessionManager;
    
    @FXML
    private TextField projectNameField;
    
    @FXML
    private TextArea descriptionField;
    
    @FXML
    private RadioButton publicRadio;
    
    @FXML
    private Label messageLabel;
    
    @FXML
    private void handleCreate() {
        String projectName = projectNameField.getText();
        String description = descriptionField.getText();
        boolean isPublic = publicRadio.isSelected();
        
        if (projectName.isEmpty()) {
            messageLabel.setText("Project name is required");
            return;
        }
        
        try {
            Project project = new Project();
            project.setName(projectName);
            project.setDescription(description);
            project.setPublic(isPublic);
            
            // In a real application, get the current user
            User currentUser = new User();
            currentUser.setId(sessionManager.getUserId());
            currentUser.setUsername(sessionManager.getUsername());
            
            project.setOwner(currentUser);
            
            projectService.createProject(project);
            
            // Close the dialog
            Stage stage = (Stage) projectNameField.getScene().getWindow();
            stage.close();
        } catch (Exception e) {
            messageLabel.setText("Failed to create project: " + e.getMessage());
            AlertHelper.showErrorAlert("Error", "Failed to create project", e.getMessage());
        }
    }
    
    @FXML
    private void handleCancel() {
        // Close the dialog
        Stage stage = (Stage) projectNameField.getScene().getWindow();
        stage.close();
    }
}