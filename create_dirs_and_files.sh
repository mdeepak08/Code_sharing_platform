#!/bin/bash

# Define the base directory
base_dir="src/main"

# Create directories
directories=(
    "$base_dir/java/com/codeshare/platform/ui/component"
    "$base_dir/java/com/codeshare/platform/ui/controller"
    "$base_dir/java/com/codeshare/platform/ui/service"
    "$base_dir/java/com/codeshare/platform/ui/util"
    "$base_dir/resources/fxml"
    "$base_dir/resources/styles"
)

for dir in "${directories[@]}"; do
    mkdir -p "$dir"
done

# Create files
files=(
    "$base_dir/java/com/codeshare/platform/ui/JavaFxApplication.java"
    "$base_dir/java/com/codeshare/platform/ui/component/CodeEditor.java"
    "$base_dir/java/com/codeshare/platform/ui/controller/LoginViewController.java"
    "$base_dir/java/com/codeshare/platform/ui/controller/MainViewController.java"
    "$base_dir/java/com/codeshare/platform/ui/controller/ProjectViewController.java"
    "$base_dir/java/com/codeshare/platform/ui/service/RestClient.java"
    "$base_dir/java/com/codeshare/platform/ui/util/AlertHelper.java"
    "$base_dir/java/com/codeshare/platform/ui/util/EditorTabManager.java"
    "$base_dir/java/com/codeshare/platform/ui/util/SceneManager.java"
    "$base_dir/java/com/codeshare/platform/ui/util/SessionManager.java"
    "$base_dir/java/com/codeshare/platform/CodeSharingPlatformApplication.java"
    "$base_dir/resources/fxml/LoginView.fxml"
    "$base_dir/resources/fxml/MainView.fxml"
    "$base_dir/resources/fxml/ProjectView.fxml"
    "$base_dir/resources/styles/application.css"
    "$base_dir/resources/styles/code-editor.css"
)

for file in "${files[@]}"; do
    touch "$file"
done