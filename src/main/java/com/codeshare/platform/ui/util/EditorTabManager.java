package com.codeshare.platform.ui.util;

import com.codeshare.platform.model.File;
import com.codeshare.platform.service.FileService;
import com.codeshare.platform.ui.component.CodeEditor;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Component
public class EditorTabManager {

    @Autowired
    private FileService fileService;
    
    private TabPane tabPane;
    private final Map<String, Tab> openTabs = new HashMap<>();
    
    public void setTabPane(TabPane tabPane) {
        this.tabPane = tabPane;
    }
    
    public void openFile(File file) {
        String filePath = file.getPath();
        
        // Check if the file is already open
        if (openTabs.containsKey(filePath)) {
            // Select the existing tab
            Tab tab = openTabs.get(filePath);
            tabPane.getSelectionModel().select(tab);
            return;
        }
        
        // Create a new tab for the file
        Tab tab = new Tab(file.getName());
        
        // Create a code editor for the file
        CodeEditor codeEditor = new CodeEditor(filePath);
        codeEditor.replaceText(file.getContent() != null ? file.getContent() : "");
        
        // Reset dirty flag after loading content
        codeEditor.setDirty(false);
        
        // Add change listener to track modifications
        codeEditor.textProperty().addListener((observable, oldValue, newValue) -> {
            if (!oldValue.equals(newValue)) {
                codeEditor.setDirty(true);
                
                // Mark unsaved files with an asterisk
                if (!tab.getText().endsWith("*")) {
                    tab.setText(tab.getText() + "*");
                }
            }
        });
        
        tab.setContent(codeEditor);
        
        // Handle tab close request
        tab.setOnCloseRequest(event -> {
            if (codeEditor.isDirty()) {
                boolean shouldClose = AlertHelper.showConfirmationAlert(
                    "Unsaved Changes",
                    "Save Changes?",
                    "The file has unsaved changes. Do you want to save before closing?"
                );
                
                if (shouldClose) {
                    saveFile(file, codeEditor.getText());
                }
            }
            
            openTabs.remove(filePath);
        });
        
        // Add tab to the tab pane
        tabPane.getTabs().add(tab);
        openTabs.put(filePath, tab);
        
        // Select the new tab
        tabPane.getSelectionModel().select(tab);
    }
    
    public void saveFile(File file, String content) {
        try {
            file.setContent(content);
            fileService.updateFile(file);
            
            // Update the tab title by removing the asterisk
            Tab tab = openTabs.get(file.getPath());
            if (tab != null && tab.getText().endsWith("*")) {
                tab.setText(tab.getText().substring(0, tab.getText().length() - 1));
            }
            
            // Reset dirty flag
            CodeEditor codeEditor = (CodeEditor) tab.getContent();
            codeEditor.setDirty(false);
        } catch (Exception e) {
            AlertHelper.showErrorAlert("Error", "Failed to save file", e.getMessage());
        }
    }
    
    public void saveAllFiles() {
        for (Tab tab : tabPane.getTabs()) {
            if (tab.getText().endsWith("*")) {
                CodeEditor codeEditor = (CodeEditor) tab.getContent();
                String filePath = codeEditor.getFilePath();
                
                Optional<File> fileOpt = fileService.getFileByProjectAndPath(null, filePath);
                if (fileOpt.isPresent()) {
                    saveFile(fileOpt.get(), codeEditor.getText());
                }
            }
        }
    }
    
    public void closeAllTabs() {
        tabPane.getTabs().clear();
        openTabs.clear();
    }
}