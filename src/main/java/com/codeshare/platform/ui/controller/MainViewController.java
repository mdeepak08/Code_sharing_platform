package com.codeshare.platform.ui.controller;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.codeshare.platform.model.File;
import com.codeshare.platform.model.Project;
import com.codeshare.platform.service.FileService;
import com.codeshare.platform.service.ProjectService;
import com.codeshare.platform.ui.util.AlertHelper;
import com.codeshare.platform.ui.util.EditorTabManager;
import com.codeshare.platform.ui.util.SceneManager;
import com.codeshare.platform.ui.util.SessionManager;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;

@Component
public class MainViewController {

    @Autowired
    private ProjectService projectService;
    
    @Autowired
    private FileService fileService;
    
    @Autowired
    private SceneManager sceneManager;
    
    @Autowired
    private SessionManager sessionManager;
    
    @Autowired
    private EditorTabManager editorTabManager;
    
    @FXML
    private ListView<Project> projectListView;
    
    @FXML
    private TreeView<String> fileTreeView;
    
    @FXML
    private TabPane editorTabPane;
    
    @FXML
    private Label statusLabel;
    
    private final ObservableList<Project> projects = FXCollections.observableArrayList();
    private Project currentProject;
    
    @FXML
    public void initialize() {
        // Set tab pane for editor manager
        editorTabManager.setTabPane(editorTabPane);
        
        // Configure project list
        projectListView.setItems(projects);
        projectListView.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(Project item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.getName());
                }
            }
        });
        
        // Add selection listener for projects
        projectListView.getSelectionModel().selectedItemProperty().addListener(
            (observable, oldValue, newValue) -> {
                if (newValue != null) {
                    currentProject = newValue;
                    loadProjectFiles(newValue);
                }
            });
        
        // Add double-click handler for file tree view
        fileTreeView.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                TreeItem<String> selectedItem = fileTreeView.getSelectionModel().getSelectedItem();
                if (selectedItem != null && selectedItem.isLeaf()) {
                    // Get the full path of the file
                    String filePath = getFilePathFromTreeItem(selectedItem);
                    
                    // Load and open the file
                    Optional<File> fileOpt = fileService.getFileByProjectAndPath(currentProject, filePath);
                    if (fileOpt.isPresent()) {
                        editorTabManager.openFile(fileOpt.get());
                    }
                }
            }
        });
        
        // Load projects
        loadProjects();
    }
    
    private String getFilePathFromTreeItem(TreeItem<String> item) {
        StringBuilder path = new StringBuilder(item.getValue());
        TreeItem<String> parent = item.getParent();
        
        while (parent != null && parent.getParent() != null) {
            path.insert(0, parent.getValue() + "/");
            parent = parent.getParent();
        }
        
        return path.toString();
    }
    
    private void loadProjects() {
        try {
            // In a real application, get the current user's projects
            List<Project> userProjects = projectService.getAllProjects();
            projects.setAll(userProjects);
            statusLabel.setText("Projects loaded successfully");
        } catch (Exception e) {
            AlertHelper.showErrorAlert("Error", "Failed to load projects", e.getMessage());
            statusLabel.setText("Failed to load projects");
        }
    }
    
    private void loadProjectFiles(Project project) {
        try {
            // Clear existing tree
            TreeItem<String> root = new TreeItem<>(project.getName());
            root.setExpanded(true);
            
            // Load files from the project
            List<File> files = fileService.getFilesByProject(project);
            
            // Organize files into a tree structure
            for (File file : files) {
                String path = file.getPath();
                String[] parts = path.split("/");
                
                TreeItem<String> current = root;
                for (int i = 0; i < parts.length; i++) {
                    String part = parts[i];
                    boolean isFile = (i == parts.length - 1);
                    
                    TreeItem<String> found = null;
                    for (TreeItem<String> child : current.getChildren()) {
                        if (child.getValue().equals(part)) {
                            found = child;
                            break;
                        }
                    }
                    
                    if (found == null) {
                        TreeItem<String> newItem = new TreeItem<>(part);
                        current.getChildren().add(newItem);
                        current = newItem;
                    } else {
                        current = found;
                    }
                }
            }
            
            fileTreeView.setRoot(root);
            statusLabel.setText("Project files loaded");
        } catch (Exception e) {
            AlertHelper.showErrorAlert("Error", "Failed to load project files", e.getMessage());
            statusLabel.setText("Failed to load project files");
        }
    }
    
    @FXML
    private void handleNewProject() {
        try {
            sceneManager.showProjectDialog();
            loadProjects(); // Refresh after adding
        } catch (Exception e) {
            AlertHelper.showErrorAlert("Error", "Failed to open project dialog", e.getMessage());
        }
    }
    
    @FXML
    private void handleOpenProject() {
        Project selectedProject = projectListView.getSelectionModel().getSelectedItem();
        if (selectedProject != null) {
            loadProjectFiles(selectedProject);
        } else {
            AlertHelper.showWarningAlert("Warning", "No Project Selected", "Please select a project to open.");
        }
    }
    
    @FXML
    private void handleAddProject() {
        handleNewProject();
    }
    
    @FXML
    private void handleSaveAll() {
        editorTabManager.saveAllFiles();
        statusLabel.setText("All files saved");
    }
    
    @FXML
    private void handleExit() {
        // Ask to save unsaved changes
        for (Tab tab : editorTabPane.getTabs()) {
            if (tab.getText().endsWith("*")) {
                boolean shouldSave = AlertHelper.showConfirmationAlert(
                    "Unsaved Changes",
                    "Save Changes?",
                    "There are unsaved changes. Do you want to save before exiting?"
                );
                
                if (shouldSave) {
                    handleSaveAll();
                }
                break;
            }
        }
        
        // Get the current stage and close it
        Stage stage = (Stage) projectListView.getScene().getWindow();
        stage.close();
    }
    
    @FXML
    private void handleUndo() {
        Tab selectedTab = editorTabPane.getSelectionModel().getSelectedItem();
        if (selectedTab != null) {
            // Get the code editor from the tab
            // Implement undo functionality
        }
    }
    
    @FXML
    private void handleRedo() {
        Tab selectedTab = editorTabPane.getSelectionModel().getSelectedItem();
        if (selectedTab != null) {
            // Get the code editor from the tab
            // Implement redo functionality
        }
    }
    
    @FXML
    private void handleCommit() {
        if (currentProject == null) {
            AlertHelper.showWarningAlert("Warning", "No Project Selected", "Please select a project first.");
            return;
        }
        
        // Save all files before commit
        handleSaveAll();
        
        // Implement commit dialog and functionality
    }
    
    @FXML
    private void handleSwitchBranch() {
        // Implement branch switching functionality
    }
    
    @FXML
    private void handleCreateBranch() {
        // Implement branch creation functionality
    }
    
    @FXML
    private void handleMergeBranch() {
        // Implement branch merging functionality
    }
    
    @FXML
    private void handleLogout() {
        // Clear session
        sessionManager.clearSession();
        
        // Close all tabs
        editorTabManager.closeAllTabs();
        
        // Show login view
        try {
            sceneManager.showLoginView();
        } catch (Exception e) {
            AlertHelper.showErrorAlert("Error", "Failed to logout", e.getMessage());
        }
    }
    
    @FXML
    private void handleAbout() {
        AlertHelper.showInformationAlert("About", "Code Sharing Platform", 
            "A collaborative code sharing platform inspired by GitHub\n\nVersion 1.0");
    }
}