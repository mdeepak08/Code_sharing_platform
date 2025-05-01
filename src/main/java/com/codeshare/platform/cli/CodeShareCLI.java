package com.codeshare.platform.cli;
import java.io.BufferedReader;
import java.io.Console;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codeshare.platform.util.ConfigManager;
import com.codeshare.platform.util.FileSync;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class CodeShareCLI {
    private static final String API_BASE_URL = "http://localhost:8080/api";
    private static String authToken;
    private static final Logger logger = LoggerFactory.getLogger(CodeShareCLI.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static void main(String[] args) {
        if (args.length == 0) {
            printHelp();
            return;
        }
        
        String command = args[0];
        
        try {
            // Load token if exists
            authToken = ConfigManager.loadToken();
            
            switch(command) {
                case "init":
                    initRepository(args);
                    break;
                case "clone":
                    cloneRepository(args);
                    break;
                case "commit":
                    commitChanges(args);
                    break;
                case "push":
                    pushChanges(args);
                    break;
                case "pull":
                    pullChanges(args);
                    break;
                case "branch":
                    manageBranches(args);
                    break;
                case "status":
                    showStatus(args);
                    break;
                case "login":
                    login(args);
                    break;
                default:
                    System.out.println("Unknown command: " + command);
                    printHelp();
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void login(String[] args) {
        Console console = System.console();
        if (console == null) {
            System.err.println("Error: No console available. Cannot read credentials.");
            return;
        }

        String username = console.readLine("Username: ");
        char[] passwordChars = console.readPassword("Password: ");
        String password = new String(passwordChars);

        // Clear password from memory ASAP
        Arrays.fill(passwordChars, ' ');

        HttpURLConnection conn = null;

        try {
            URL url = new URL(API_BASE_URL + "/auth/login");
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; utf-8");
            conn.setRequestProperty("Accept", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);

            // Prepare JSON payload
            Map<String, String> loginData = new HashMap<>();
            loginData.put("username", username);
            loginData.put("password", password);
            String jsonInputString = objectMapper.writeValueAsString(loginData);
            password = null; // Clear the String version of the password

            // Send request payload
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonInputString.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int responseCode = conn.getResponseCode();
            System.out.println("DEBUG: Server responded with HTTP code: " + responseCode);

            if (responseCode == HttpURLConnection.HTTP_OK) {
                // Read response body
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder response = new StringBuilder();
                    String responseLine;
                    while ((responseLine = br.readLine()) != null) {
                        response.append(responseLine.trim());
                    }
                    
                    String responseStr = response.toString();
                    System.out.println("DEBUG: Raw server response: " + responseStr);

                    // Parse response using Jackson
                    JsonNode rootNode = objectMapper.readTree(responseStr);
                    JsonNode dataNode = rootNode.path("data");
                    if (!dataNode.isMissingNode()) {
                        String token = dataNode.path("accessToken").asText();
                        
                        if (token != null && !token.isEmpty()) {
                            authToken = token;
                            System.out.println("===========================================");
                            System.out.println("Token extracted successfully.");
                            ConfigManager.saveToken(authToken);
                            System.out.println("Login successful!");
                            System.out.println("=============================================");
                        } else {
                            System.err.println("Login Error: Could not extract access token from response.");
                        }
                    } else {
                        System.err.println("Login Error: Response data structure is not as expected.");
                        System.err.println("Response was: " + responseStr);
                    }
                }
            } else {
                System.err.println("Login failed. Server responded with HTTP code: " + responseCode + " " + conn.getResponseMessage());
                
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8))) {
                    StringBuilder errorResponse = new StringBuilder();
                    String responseLine;
                    while ((responseLine = br.readLine()) != null) {
                        errorResponse.append(responseLine.trim());
                    }
                    
                    if (!errorResponse.toString().isEmpty()) {
                        System.err.println("Server error details: " + errorResponse.toString());
                    } else {
                        System.err.println("No detailed error message body received from server.");
                    }
                } catch (IOException | NullPointerException e) {
                    System.err.println("Could not read detailed error message from server.");
                }
            }
        } catch (MalformedURLException e) {
            System.err.println("Internal Error: Invalid API URL configured: " + e.getMessage());
        } catch (IOException e) {
            System.err.println("Network Error: Could not connect to server or timed out during login.");
            System.err.println("Details: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Unexpected error during login: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
            password = null;
            username = null;
        }
    }
    
    private static void initRepository(String[] args) throws IOException {
        if (args.length < 2) {
            System.out.println("Usage: codeshare init <project-name> [description]");
            return;
        }
        
        // Check if token exists
        if (authToken == null) {
            System.out.println("Please login first: codeshare login");
            return;
        }
        
        String projectName = args[1];
        String description = args.length > 2 ? args[2] : "";
        
        // URL encode parameters
        String encodedProjectName = URLEncoder.encode(projectName, StandardCharsets.UTF_8.toString());
        String encodedDescription = URLEncoder.encode(description, StandardCharsets.UTF_8.toString());
        
        // Get current directory
        File currentDir = new File(System.getProperty("user.dir"));
        
        // Check if directory is empty
        if (currentDir.list().length > 0) {
            System.out.println("Warning: Directory is not empty");
            Console console = System.console();
            String confirm = console.readLine("Initialize anyway? (y/n): ");
            if (!confirm.equalsIgnoreCase("y")) {
                return;
            }
        }
        
        // Debug: Show token being used
        System.out.println("DEBUG: Token length: " + (authToken != null ? authToken.length() : 0));
        if (authToken != null && authToken.length() > 20) {
            System.out.println("DEBUG: Token starts with: " + authToken.substring(0, 20) + "...");
        }
        
        // Create API request to init repository
        URL url = new URL(API_BASE_URL + "/cli/init?projectName=" + encodedProjectName + "&description=" + encodedDescription);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", "Bearer " + authToken);
        
        // Handle response
        int responseCode = conn.getResponseCode();
        if (responseCode >= 200 && responseCode < 300) {
            // Read response and parse project ID
            try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                StringBuilder response = new StringBuilder();
                String responseLine;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }
                
                // Parse JSON response with Jackson
                JsonNode rootNode = objectMapper.readTree(response.toString());
                
                if (rootNode.has("data") && rootNode.get("data").has("id")) {
                    String projectId = rootNode.get("data").get("id").asText();
                    
                    // Initialize local config
                    Map<String, String> config = new HashMap<>();
                    config.put("projectId", projectId);
                    config.put("projectName", projectName);
                    config.put("remoteUrl", API_BASE_URL);
                    
                    // Save config
                    ConfigManager.saveRepoConfig(currentDir, config);
                    
                    System.out.println("Repository initialized successfully");
                } else {
                    System.out.println("Failed to initialize repository: Unexpected response format");
                }
            }
        } else {
            System.out.println("Failed to initialize repository: " + responseCode);
            if (conn.getErrorStream() != null) {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getErrorStream()))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        System.out.println(line);
                    }
                } catch (Exception e) {
                    System.out.println("Error reading error stream: " + e.getMessage());
                }
            } else {
                System.out.println("No additional error information available");
            }
        }
    }
    
    private static void cloneRepository(String[] args) throws IOException, NoSuchAlgorithmException {
        if (args.length < 2) {
            System.out.println("Usage: codeshare clone <project-id> [directory] [branch]");
            return;
        }
        
        // Check if token exists
        if (authToken == null) {
            System.out.println("Please login first: codeshare login");
            return;
        }
        
        String projectId = args[1];
        String directory = args.length > 2 ? args[2] : projectId;
        String branch = args.length > 3 ? args[3] : "";
        
        // Create target directory
        File targetDir = new File(directory);
        if (!targetDir.exists() && !targetDir.mkdirs()) {
            System.out.println("Failed to create directory: " + directory);
            return;
        }
        
        // Check if directory is empty
        if (targetDir.list().length > 0) {
            System.out.println("Directory is not empty: " + directory);
            return;
        }
        
        // Create API request to clone repository
        URL url = new URL(API_BASE_URL + "/cli/clone?projectId=" + projectId + (branch.isEmpty() ? "" : "&branch=" + branch));
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", "Bearer " + authToken);
        
        // Handle response
        int responseCode = conn.getResponseCode();
        if (responseCode >= 200 && responseCode < 300) {
            // Read response and parse files
            try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                StringBuilder response = new StringBuilder();
                String responseLine;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }
                
                // Parse JSON response with Jackson
                JsonNode rootNode = objectMapper.readTree(response.toString());
                JsonNode dataNode = rootNode.path("data");
                
                if (!dataNode.isMissingNode()) {
                    // Convert JSON data to Map<String, String>
                    Map<String, String> files = objectMapper.convertValue(
                        dataNode, new TypeReference<Map<String, String>>() {});
                    
                    // Write files to target directory
                    for (Map.Entry<String, String> entry : files.entrySet()) {
                        String filePath = entry.getKey();
                        String content = entry.getValue();
                        
                        // Normalize line endings to match the local system
                        content = content.replaceAll("\\r\\n|\\r|\\n", System.lineSeparator());
                        
                        File file = new File(targetDir, filePath);
                        File parentDir = file.getParentFile();
                        if (!parentDir.exists() && !parentDir.mkdirs()) {
                            System.out.println("Failed to create directory: " + parentDir);
                            continue;
                        }
                        
                        try (FileWriter writer = new FileWriter(file)) {
                            writer.write(content);
                        }
                    }
                    
                    // Initialize local config
                    Map<String, String> config = new HashMap<>();
                    config.put("projectId", projectId);
                    config.put("remoteUrl", API_BASE_URL);
                    config.put("branch", branch.isEmpty() ? "main" : branch);
                    
                    // Save config
                    ConfigManager.saveRepoConfig(targetDir, config);
                    
                    // Save current state
                    FileSync.saveCurrentState(targetDir, files);
                    
                    System.out.println("Repository cloned successfully");
                } else {
                    System.out.println("Failed to parse repository data: Data node missing");
                }
            }
        } else {
            System.out.println("Failed to clone repository: " + responseCode);
            try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getErrorStream()))) {
                String line;
                while ((line = br.readLine()) != null) {
                    System.out.println(line);
                }
            }
        }
    }

    private static void pushChanges(String[] args) throws IOException, NoSuchAlgorithmException {
        // Parse branch name and commit message
        String branchName = null;
        String commitMessage = "Update";
        
        for (int i = 1; i < args.length; i++) {
            if (args[i].equals("-m") && i + 1 < args.length) {
                commitMessage = args[i + 1];
                i++; // Skip the next argument as we've already processed it
            } else if (!args[i].startsWith("-") && branchName == null) {
                branchName = args[i];
            }
        }
        
        // Check if token exists
        if (authToken == null) {
            System.out.println("Please login first: codeshare login");
            return;
        }
        
        File currentDir = new File(System.getProperty("user.dir"));

        // Check if this is a CodeShare repository
        File configDir = ConfigManager.getRepoConfigDir(currentDir);
        if (!configDir.exists()) {
            System.out.println("Not a CodeShare repository");
            return;
        }

        // Load repository config
        Map<String, String> config = ConfigManager.loadRepoConfig(currentDir);
        String projectId = config.get("projectId");
        if (projectId == null) {
            System.out.println("Invalid repository configuration");
            return;
        }

        // Use branch from config if not specified
        if (branchName == null) {
            branchName = config.get("branch");
            if (branchName == null || branchName.isEmpty()) {
                System.out.println("Error: Current branch not set. Please specify a branch name.");
                return;
            }
            System.out.println("Using branch from config: " + branchName);
        }

        // Detect local changes
        System.out.println("Detecting changes...");
        List<String> changedFiles = FileSync.detectLocalChanges(currentDir);

        if (changedFiles.isEmpty()) {
            System.out.println("No changes detected");
            return;
        }

        System.out.println("Found " + changedFiles.size() + " changed files");
        
        // Group files by directory for easier viewing
        Map<String, List<String>> filesByDirectory = new HashMap<>();
        for (String filePath : changedFiles) {
            String directory = "./";
            if (filePath.contains("/")) {
                directory = filePath.substring(0, filePath.lastIndexOf('/'));
            }
            
            filesByDirectory.computeIfAbsent(directory, k -> new ArrayList<>()).add(filePath);
        }
        
        // Print files by directory
        System.out.println("Changes by directory:");
        for (Map.Entry<String, List<String>> entry : filesByDirectory.entrySet()) {
            System.out.println("  " + entry.getKey() + "/");
            for (String file : entry.getValue()) {
                String fileName = file;
                if (file.contains("/")) {
                    fileName = file.substring(file.lastIndexOf('/') + 1);
                }
                System.out.println("    " + fileName);
            }
        }

        // Create a single map for all file changes
        Map<String, String> allChanges = new HashMap<>();
        int totalFileCount = 0;
        
        // Process all files at once
        for (String filePath : changedFiles) {
            // Read file content
            File file = new File(currentDir, filePath);
            if (!file.exists() || !file.isFile()) {
                System.out.println("Skipping non-existent or non-file: " + filePath);
                continue;
            }
        
            try {
                // Read file content
                String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
                
                // FIXED: Normalize to Unix line endings (LF) when sending to server
                // Use ACTUAL newline characters, not the literal string "\\n"
                content = content.replaceAll("\\r\\n|\\r", "\n");
                
                // Filter out null bytes
                content = content.replace("\u0000", "");
                
                allChanges.put(filePath, content);
                totalFileCount++;
            } catch (IOException e) {
                System.err.println("Error reading file: " + filePath + ": " + e.getMessage());
            }
        }
        
        if (allChanges.isEmpty()) {
            System.out.println("No valid files to push");
            return;
        }
        
        System.out.println("Pushing " + totalFileCount + " files as a single commit...");
        
        // Now send a single push request with all files
        try {
            sendCommit(projectId, branchName, allChanges, commitMessage);
            
            // Update state after successful push
            FileSync.saveCurrentState(currentDir, allChanges);
            
            System.out.println("Push completed successfully");
        } catch (Exception e) {
            System.err.println("Failed to push changes: " + e.getMessage());
        }
    }

    private static void sendCommit(String projectId, String branchName, Map<String, String> changes, String commitMessage) throws IOException {
        // Create API request
        URL url = new URL(API_BASE_URL + "/cli/push?projectId=" + projectId + 
                (branchName != null ? "&branchName=" + URLEncoder.encode(branchName, "UTF-8") : "") +
                "&commitMessage=" + URLEncoder.encode(commitMessage, "UTF-8"));
        
        System.out.println("Sending commit to server...");
        
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Authorization", "Bearer " + authToken);
        // Increase timeouts for large requests
        conn.setConnectTimeout(60000); // 60 seconds connection timeout
        conn.setReadTimeout(60000);    // 60 seconds read timeout
        conn.setDoOutput(true);
        
        // Convert changes map to JSON using Jackson
        String jsonChanges = objectMapper.writeValueAsString(changes);
        
        // Send request
        try (OutputStream os = conn.getOutputStream()) {
            byte[] input = jsonChanges.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }
        
        // Check response
        int responseCode = conn.getResponseCode();
        if (responseCode >= 200 && responseCode < 300) {
            System.out.println("Changes pushed successfully");
        } else {
            System.out.println("Failed to push changes: " + responseCode);
            
            // Safely handle error stream which might be null
            try {
                InputStream errorStream = conn.getErrorStream();
                if (errorStream != null) {
                    try (BufferedReader br = new BufferedReader(new InputStreamReader(errorStream))) {
                        // Read error details
                        StringBuilder errorResponse = new StringBuilder();
                        String line;
                        while ((line = br.readLine()) != null) {
                            errorResponse.append(line);
                        }
                        System.out.println("Error details: " + errorResponse.toString());
                    }
                } else {
                    System.out.println("No error details available from server");
                }
            } catch (Exception e) {
                System.out.println("Error reading error response: " + e.getMessage());
            }
            
            throw new IOException("Failed to push changes, server returned: " + responseCode);
        }
    }

    private static void commitChanges(String[] args) throws IOException, NoSuchAlgorithmException {
        // Check command arguments
        if (args.length < 3 || !args[1].equals("-m")) {
            System.out.println("Usage: codeshare commit -m \"commit message\"");
            return;
        }

        String commitMessage = args[2];

        // Get current directory
        File currentDir = new File(System.getProperty("user.dir"));

        // Check if this is a CodeShare repository
        File configDir = ConfigManager.getRepoConfigDir(currentDir);
        if (!configDir.exists()) {
            System.out.println("Not a CodeShare repository");
            return;
        }

        // Detect local changes
        System.out.println("Detecting changes...");
        List<String> changedFiles = FileSync.detectLocalChanges(currentDir);

        if (changedFiles.isEmpty()) {
            System.out.println("No changes detected");
            return;
        }

        System.out.println("Found " + changedFiles.size() + " changed files");

        // Save the changes for later push
        // We'll create a pending commit directory
        File pendingDir = new File(configDir, "pending");
        if (!pendingDir.exists()) {
            pendingDir.mkdir();
        }

        // Save commit message
        try (FileWriter writer = new FileWriter(new File(pendingDir, "message"))) {
            writer.write(commitMessage);
        }

        // Save the list of changed file paths
        File changesListFile = new File(pendingDir, "changes.list");
        try (FileWriter writer = new FileWriter(changesListFile)) {
            for (String relativePath : changedFiles) {
                writer.write(relativePath + "\n");
            }
        }

        // Update the local state
        Map<String, String> currentHashes = new HashMap<>();
        for (String relativePath : changedFiles) {
            File file = new File(currentDir, relativePath);
            if (file.isFile()) {
                String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
                currentHashes.put(relativePath, FileSync.calculateHash(content));
            }
        }
        FileSync.saveCurrentState(currentDir, currentHashes);

        System.out.println("Changes committed locally. Use 'codeshare push' to push to remote repository.");
    }

    private static void pullChanges(String[] args) throws IOException, NoSuchAlgorithmException {
        // Parse branch name
        String branchName = args.length > 1 ? args[1] : null;
        
        // Check if token exists
        if (authToken == null) {
            System.out.println("Please login first: codeshare login");
            return;
        }
        
        // Get current directory
        File currentDir = new File(System.getProperty("user.dir"));
        
        // Check if this is a CodeShare repository
        File configDir = ConfigManager.getRepoConfigDir(currentDir);
        if (!configDir.exists()) {
            System.out.println("Not a CodeShare repository");
            return;
        }
        
        // Load repository config
        Map<String, String> config = ConfigManager.loadRepoConfig(currentDir);
        String projectId = config.get("projectId");
        if (projectId == null) {
            System.out.println("Invalid repository configuration");
            return;
        }
        
        // Detect local changes first to warn user if there are uncommitted changes
        List<String> localChanges = FileSync.detectLocalChanges(currentDir);

        if (!localChanges.isEmpty()) {
            System.out.println("Warning: You have " + localChanges.size() + " uncommitted local changes.");
            System.out.print("Do you want to continue? (y/n): ");

            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            String answer = reader.readLine();
            if (!answer.equalsIgnoreCase("y")) {
                System.out.println("Pull aborted");
                return;
            }
        }
        
        // Pull changes from remote
        System.out.println("Pulling changes from remote...");
        
        // Create API request
        URL url = new URL(API_BASE_URL + "/cli/pull?projectId=" + projectId + 
                (branchName != null ? "&branchName=" + branchName : ""));
        
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + authToken);
        
        // Check response
        int responseCode = conn.getResponseCode();
        if (responseCode >= 200 && responseCode < 300) {
            // Read response and parse files
            try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                StringBuilder response = new StringBuilder();
                String responseLine;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }
                
                // Parse JSON response with Jackson
                JsonNode rootNode = objectMapper.readTree(response.toString());
                JsonNode dataNode = rootNode.path("data");
                
                if (!dataNode.isMissingNode()) {
                    // Convert JSON data to Map<String, String>
                    Map<String, String> remoteFiles = objectMapper.convertValue(
                        dataNode, new TypeReference<Map<String, String>>() {});
                    
                    // Write files to local repository
                    int updatedFiles = 0;
                    
                    for (Map.Entry<String, String> entry : remoteFiles.entrySet()) {
                        String filePath = entry.getKey();
                        String content = entry.getValue();
                        
                        // Normalize line endings to match the local system
                        content = content.replaceAll("\\r\\n|\\r|\\n", System.lineSeparator());
                        
                        File file = new File(currentDir, filePath);
                        File parentDir = file.getParentFile();
                        if (!parentDir.exists() && !parentDir.mkdirs()) {
                            System.out.println("Failed to create directory: " + parentDir);
                            continue;
                        }
                        
                        // Check if file exists and content is different
                        boolean shouldUpdate = true;
                        if (file.exists()) {
                            String currentContent = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
                            // Normalize current content for comparison to avoid false positives due to line endings
                            currentContent = currentContent.replaceAll("\\r\\n|\\r|\\n", "\\n");
                            String normalizedNewContent = content.replaceAll("\\r\\n|\\r|\\n", "\\n");
                            shouldUpdate = !currentContent.equals(normalizedNewContent);
                        }
                        
                        if (shouldUpdate) {
                            try (FileWriter writer = new FileWriter(file)) {
                                writer.write(content);
                                updatedFiles++;
                            }
                        }
                    }
                    
                    // Save current state after pull
                    FileSync.saveCurrentState(currentDir, remoteFiles);
                    
                    System.out.println("Pull completed, " + updatedFiles + " files updated");
                } else {
                    System.out.println("Failed to parse repository data: Data node missing");
                }
            }
        } else {
            System.out.println("Failed to pull changes: " + responseCode);
            try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getErrorStream()))) {
                String line;
                while ((line = br.readLine()) != null) {
                    System.out.println(line);
                }
            }
        }
    }

    private static void manageBranches(String[] args) throws IOException, NoSuchAlgorithmException {
        // Check if token exists
        if (authToken == null) {
            System.out.println("Please login first: codeshare login");
            return;
        }
        
        // Get current directory
        File currentDir = new File(System.getProperty("user.dir"));
        
        // Check if this is a CodeShare repository
        File configDir = ConfigManager.getRepoConfigDir(currentDir);
        if (!configDir.exists()) {
            System.out.println("Not a CodeShare repository");
            return;
        }
        
        // Load repository config
        Map<String, String> config = ConfigManager.loadRepoConfig(currentDir);
        String projectId = config.get("projectId");
        if (projectId == null) {
            System.out.println("Invalid repository configuration");
            return;
        }
        
        // Determine operation: list, create, or switch
        if (args.length == 1) {
            // List branches
            listBranches(projectId);
        } else if (args.length == 2) {
            // Create or switch branch
            String branchName = args[1];
            
            // Get current branch from config
            String currentBranch = config.get("branch");
            if (currentBranch != null && currentBranch.equals(branchName)) {
                System.out.println("Already on branch '" + branchName + "'");
                return;
            }
            
            // Ask if the user wants to create or switch to the branch
            Console console = System.console();
            if (console == null) {
                System.out.println("No console available");
                return;
            }
            
            String action = console.readLine("Do you want to (c)reate a new branch or (s)witch to an existing branch? (c/s): ");
            if (action.equalsIgnoreCase("c")) {
                createBranch(projectId, branchName);
            } else if (action.equalsIgnoreCase("s")) {
                switchBranch(projectId, branchName);
            } else {
                System.out.println("Invalid choice");
            }
        } else if (args.length == 3) {
            // Check for specific branch commands
            if (args[1].equals("switch") || args[1].equals("checkout")) {
                String branchName = args[2];
                switchBranch(projectId, branchName);
            } else if (args[1].equals("create") || args[1].equals("new")) {
                String branchName = args[2];
                createBranch(projectId, branchName);
            } else {
                System.out.println("Unknown branch command: " + args[1]);
                System.out.println("Usage: codeshare branch [switch|create] <branch-name>");
            }
        } else {
            System.out.println("Usage: codeshare branch [switch|create] <branch-name>");
        }
    }
    
    private static void listBranches(String projectId) throws IOException {
        System.out.println("Fetching branches...");
        
        // Get current branch from config
        File currentDir = new File(System.getProperty("user.dir"));
        String currentBranch = null;
        try {
            Map<String, String> config = ConfigManager.loadRepoConfig(currentDir);
            currentBranch = config.get("branch");
        } catch (Exception e) {
            // Ignore error, just won't mark current branch
        }
        
        // Create API request
        URL url = new URL(API_BASE_URL + "/cli/branches?projectId=" + projectId);
        
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + authToken);
        System.out.println("DEBUG: Using token: " + (authToken != null ? 
            authToken.substring(0, Math.min(10, authToken.length())) + "..." : "null"));
        
        // Check response
        int responseCode = conn.getResponseCode();
        if (responseCode >= 200 && responseCode < 300) {
            // Read response and parse branches
            try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                StringBuilder response = new StringBuilder();
                String responseLine;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }
                
                // Parse JSON response with Jackson
                JsonNode rootNode = objectMapper.readTree(response.toString());
                JsonNode dataNode = rootNode.path("data");
                
                if (!dataNode.isMissingNode() && dataNode.isArray()) {
                    // First show current branch
                    if (currentBranch != null && !currentBranch.isEmpty()) {
                        System.out.println("Current branch: " + currentBranch);
                    }
                    
                    System.out.println("Available branches:");
                    
                    for (JsonNode branchNode : dataNode) {
                        String name = branchNode.path("name").asText();
                        boolean isDefault = branchNode.path("default").asBoolean();
                        
                        boolean isCurrent = currentBranch != null && currentBranch.equals(name);
                        System.out.println("  " + name + 
                                          (isDefault ? " (default)" : "") + 
                                          (isCurrent ? " *" : ""));
                    }
                } else {
                    System.out.println("No branches found");
                }
            }
        } else { // Handle non-200 responses
            System.out.println("Failed to fetch branches: " + responseCode);
            try {
                InputStream errorStream = conn.getErrorStream();
                if (errorStream != null) {
                    try (BufferedReader br = new BufferedReader(new InputStreamReader(errorStream))) {
                        String line;
                        while ((line = br.readLine()) != null) {
                            System.out.println(line);
                        }
                    }
                } else {
                    System.out.println("No detailed error message body received from server.");
                }
            } catch (IOException e) {
                System.out.println("Error reading error stream: " + e.getMessage());
            }
        }
    }
    
    private static void switchBranch(String projectId, String branchName) throws IOException, NoSuchAlgorithmException {
        System.out.println("Switching to branch: " + branchName);
        
        // Get current directory
        File currentDir = new File(System.getProperty("user.dir"));
        
        // Check if this is a CodeShare repository
        File configDir = ConfigManager.getRepoConfigDir(currentDir);
        if (!configDir.exists()) {
            System.out.println("Not a CodeShare repository");
            return;
        }
        
        // Load repository config
        Map<String, String> config = ConfigManager.loadRepoConfig(currentDir);
        
        // Check if branchName exists
        boolean branchExists = false;
        try {
            URL url = new URL(API_BASE_URL + "/cli/branches?projectId=" + projectId);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "Bearer " + authToken);
            
            int responseCode = conn.getResponseCode();
            if (responseCode >= 200 && responseCode < 300) {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                    StringBuilder responseStr = new StringBuilder();
                    String responseLine;
                    while ((responseLine = br.readLine()) != null) {
                        responseStr.append(responseLine.trim());
                    }
                    
                    // Parse response with Jackson
                    JsonNode rootNode = objectMapper.readTree(responseStr.toString());
                    JsonNode dataNode = rootNode.path("data");
                    
                    if (!dataNode.isMissingNode() && dataNode.isArray()) {
                        for (JsonNode branchNode : dataNode) {
                            if (branchName.equals(branchNode.path("name").asText())) {
                                branchExists = true;
                                break;
                            }
                        }
                    }
                }
            } else {
                System.out.println("Failed to check branches: " + responseCode);
                return;
            }
        } catch (Exception e) {
            System.out.println("Error checking branches: " + e.getMessage());
            return;
        }
        
        if (!branchExists) {
            System.out.println("Branch '" + branchName + "' does not exist. Available branches:");
            listBranches(projectId);
            
            Console console = System.console();
            String confirm = console.readLine("Create branch '" + branchName + "'? (y/n): ");
            if (confirm.equalsIgnoreCase("y")) {
                createBranch(projectId, branchName);
            } else {
                return;
            }
        }
        
        // Update the branch in config
        config.put("branch", branchName);
        ConfigManager.saveRepoConfig(currentDir, config);
        
        System.out.println("Switched to branch: " + branchName);
        
        // Optionally pull changes from the branch
        Console console = System.console();
        String confirm = console.readLine("Pull latest changes from branch '" + branchName + "'? (y/n): ");
        if (confirm.equalsIgnoreCase("y")) {
            pullChanges(new String[]{"pull", branchName});
        }
    }
    
    private static void createBranch(String projectId, String branchName) throws IOException {
        System.out.println("Creating branch: " + branchName);
        
        // Create API request
        URL url = new URL(API_BASE_URL + "/cli/branch?projectId=" + projectId + "&branchName=" + URLEncoder.encode(branchName, "UTF-8"));
        
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", "Bearer " + authToken);
        
        // Check response
        int responseCode = conn.getResponseCode();
        if (responseCode >= 200 && responseCode < 300) {
            System.out.println("Branch created successfully");
            
            // Ask if user wants to switch to the new branch
            System.out.print("Do you want to switch to the new branch? (y/n): ");
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
            String answer = reader.readLine();
            
            if (answer.equalsIgnoreCase("y")) {
                // Update local config to use the new branch
                Map<String, String> config = ConfigManager.loadRepoConfig(new File(System.getProperty("user.dir")));
                config.put("branch", branchName);
                ConfigManager.saveRepoConfig(new File(System.getProperty("user.dir")), config);
                System.out.println("Switched to branch: " + branchName);
            }
        } else {
            System.out.println("Failed to create branch: " + responseCode);
            try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getErrorStream()))) {
                String line;
                while ((line = br.readLine()) != null) {
                    System.out.println(line);
                }
            }
        }
    }
    
    private static void showStatus(String[] args) throws IOException {
        // Get current directory
        File currentDir = new File(System.getProperty("user.dir"));
        
        // Check if this is a CodeShare repository
        File configDir = ConfigManager.getRepoConfigDir(currentDir);
        if (!configDir.exists()) {
            System.out.println("Not a CodeShare repository");
            return;
        }
        
        // Load repository config
        Map<String, String> config = ConfigManager.loadRepoConfig(currentDir);
        String projectId = config.get("projectId");
        String projectName = config.get("projectName");
        
        if (projectId == null) {
            System.out.println("Invalid repository configuration");
            return;
        }
        
        System.out.println("On branch: " + (config.get("branch") != null ? config.get("branch") : "default"));
        System.out.println("Project: " + (projectName != null ? projectName : "unnamed") + " (ID: " + projectId + ")");
        System.out.println("Remote: " + config.getOrDefault("remoteUrl", API_BASE_URL));
        
        // Detect local changes
        try {
            List<String> changedFiles = FileSync.detectLocalChanges(currentDir);
            
            if (changedFiles.isEmpty()) {
                System.out.println("\nWorking tree clean. No changes to commit.");
            } else {
                System.out.println("\nChanges not staged for commit:");
                System.out.println("  (use \"codeshare commit -m <message>\" to commit the changes)");
                System.out.println("  (use \"codeshare push\" to upload your commits to the server)");
                System.out.println();
                
                // Group files by directory for easier viewing
                Map<String, List<String>> filesByDirectory = new HashMap<>();
                for (String filePath : changedFiles) {
                    String directory = "./";
                    if (filePath.contains("/")) {
                        directory = filePath.substring(0, filePath.lastIndexOf('/'));
                    }
                    
                    filesByDirectory.computeIfAbsent(directory, k -> new ArrayList<>()).add(filePath);
                }
                
                // Print files by directory
                for (Map.Entry<String, List<String>> entry : filesByDirectory.entrySet()) {
                    System.out.println("  " + entry.getKey() + "/");
                    for (String file : entry.getValue()) {
                        String fileName = file;
                        if (file.contains("/")) {
                            fileName = file.substring(file.lastIndexOf('/') + 1);
                        }
                        System.out.println("    modified:   " + fileName);
                    }
                }
            }
        } catch (NoSuchAlgorithmException e) {
            System.out.println("\nError detecting file changes: " + e.getMessage());
        }
    }
    
    private static void printHelp() {
        System.out.println("CodeShare CLI - Git-like commands for CodeShare Platform");
        System.out.println("Usage: codeshare <command> [options]");
        System.out.println("Commands:");
        System.out.println("  init <project-name>        Initialize a new repository");
        System.out.println("  clone <project-id>         Clone a repository");
        System.out.println("  commit -m <msg>            Commit changes");
        System.out.println("  push                       Push changes to remote");
        System.out.println("  pull [branch-name]         Pull changes from remote");
        System.out.println("  branch                     List branches");
        System.out.println("  branch <name>              Create or switch branch");
        System.out.println("  branch switch <name>       Switch to a branch");
        System.out.println("  branch create <name>       Create a new branch");
        System.out.println("  status                     Show working tree status");
        System.out.println("  login                      Login to CodeShare Platform");
    }
}