package com.codeshare.platform.cli;

import java.io.BufferedReader;
import java.io.Console;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import com.codeshare.platform.util.ConfigManager;
import com.codeshare.platform.util.FileSync;

public class CodeShareCLI {
    private static final String API_BASE_URL = "http://localhost:8080/api";
    private static String authToken;
    
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
    
    private static void login(String[] args) throws IOException {
        Console console = System.console();
        if (console == null) {
            System.out.println("No console available");
            return;
        }
        
        String username = console.readLine("Username: ");
        char[] passwordChars = console.readPassword("Password: ");
        String password = new String(passwordChars);
        
        // Clear password from memory
        Arrays.fill(passwordChars, ' ');
        
        // Perform login request
        URL url = new URL(API_BASE_URL + "/auth/login");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        
        String jsonInputString = "{\"username\": \"" + username + "\", \"password\": \"" + password + "\"}";
        
        try (OutputStream os = conn.getOutputStream()) {
            byte[] input = jsonInputString.getBytes("utf-8");
            os.write(input, 0, input.length);
        }
        
        // Read response and save token
        try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "utf-8"))) {
            StringBuilder response = new StringBuilder();
            String responseLine = null;
            while ((responseLine = br.readLine()) != null) {
                response.append(responseLine.trim());
            }
            
            // Parse JSON response to get token
            // Simple parsing - in real app use proper JSON parser
            String responseStr = response.toString();
            int tokenStart = responseStr.indexOf("\"token\":\"") + 9;
            int tokenEnd = responseStr.indexOf("\"", tokenStart);
            authToken = responseStr.substring(tokenStart, tokenEnd);
            
            // Save token to local config
            ConfigManager.saveToken(authToken);
            
            System.out.println("Login successful");
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
        
        // Create API request to init repository
        URL url = new URL(API_BASE_URL + "/cli/init?projectName=" + projectName + "&description=" + description);
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
                
                // Simple JSON parsing - in real app use proper JSON parser
                String responseStr = response.toString();
                int idStart = responseStr.indexOf("\"id\":") + 5;
                int idEnd = responseStr.indexOf(",", idStart);
                String projectId = responseStr.substring(idStart, idEnd).trim();
                
                // Initialize local config
                Map<String, String> config = new HashMap<>();
                config.put("projectId", projectId);
                config.put("projectName", projectName);
                config.put("remoteUrl", API_BASE_URL);
                
                // Save config
                ConfigManager.saveRepoConfig(currentDir, config);
                
                System.out.println("Repository initialized successfully");
            }
        } else {
            System.out.println("Failed to initialize repository: " + responseCode);
            try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getErrorStream()))) {
                String line;
                while ((line = br.readLine()) != null) {
                    System.out.println(line);
                }
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
                
                // Simple JSON parsing - in real app use proper JSON parser
                // This is very simplified - in a real app use a proper JSON parser
                Map<String, String> files = parseFilesFromJson(response.toString());
                
                // Write files to target directory
                for (Map.Entry<String, String> entry : files.entrySet()) {
                    String filePath = entry.getKey();
                    String content = entry.getValue();
                    
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
    
    // Helper method for JSON parsing (simplified)
    private static Map<String, String> parseFilesFromJson(String json) {
        Map<String, String> files = new HashMap<>();
        
        // Very simplified JSON parsing - in a real app use a proper JSON parser
        int filesStart = json.indexOf("\"data\":{") + 7;
        int filesEnd = json.lastIndexOf("}");
        String filesJson = json.substring(filesStart, filesEnd);
        
        // Parse each file entry
        int currentPos = 0;
        while (currentPos < filesJson.length()) {
            // Find the file path (key)
            int keyStart = filesJson.indexOf("\"", currentPos) + 1;
            if (keyStart <= 0 || keyStart >= filesJson.length()) break;
            
            int keyEnd = filesJson.indexOf("\"", keyStart);
            if (keyEnd <= 0) break;
            
            String key = filesJson.substring(keyStart, keyEnd);
            
            // Find the file content (value)
            int valueStart = filesJson.indexOf("\"", keyEnd + 1) + 1;
            if (valueStart <= 0) break;
            
            int valueEnd = findMatchingQuote(filesJson, valueStart);
            if (valueEnd <= 0) break;
            
            String value = filesJson.substring(valueStart, valueEnd)
                    .replace("\\\"", "\"")
                    .replace("\\n", "\n")
                    .replace("\\t", "\t")
                    .replace("\\\\", "\\");
            
            files.put(key, value);
            
            currentPos = valueEnd + 1;
        }
        
        return files;
    }
    
    // Find matching quote in a JSON string, handling escaped quotes
    private static int findMatchingQuote(String json, int start) {
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '\\') {
                // Skip the escaped character
                i++;
            } else if (c == '"') {
                return i;
            }
        }
        return -1;
    }
    private static void pushChanges(String[] args) throws IOException, NoSuchAlgorithmException {
    // Check command arguments
    if (args.length < 2) {
        System.out.println("Usage: codeshare push [branch-name] -m \"commit message\"");
        return;
    }
    
    // Check if token exists
    if (authToken == null) {
        System.out.println("Please login first: codeshare login");
        return;
    }
    
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
    
    // Detect local changes
    System.out.println("Detecting changes...");
    Map<String, String> changes = FileSync.detectLocalChanges(currentDir);
    
    if (changes.isEmpty()) {
        System.out.println("No changes detected");
        return;
    }
    
    System.out.println("Found " + changes.size() + " changed files");
    
    // Push changes in smaller chunks to avoid "input too long" error
    System.out.println("Pushing changes...");
    
    // Split changes into smaller chunks (max 5 files per request)
    final int MAX_FILES_PER_CHUNK = 5;
    Map<String, String> chunk = new HashMap<>();
    int fileCount = 0;
    int chunkCount = 0;
    
    for (Map.Entry<String, String> entry : changes.entrySet()) {
        chunk.put(entry.getKey(), entry.getValue());
        fileCount++;
        
        // If chunk is full or this is the last file, send the chunk
        if (fileCount >= MAX_FILES_PER_CHUNK || fileCount >= changes.size()) {
            pushChunk(projectId, branchName, chunk, commitMessage + (chunkCount > 0 ? " (part " + (chunkCount + 1) + ")" : ""));
            chunk.clear();
            fileCount = 0;
            chunkCount++;
        }
    }
    
    // Save current state after successful push
    FileSync.saveCurrentState(currentDir, changes);
    
    System.out.println("Push completed successfully");
}

private static void pushChunk(String projectId, String branchName, Map<String, String> changes, String commitMessage) throws IOException {
    // Create API request
    URL url = new URL(API_BASE_URL + "/cli/push?projectId=" + projectId + 
            (branchName != null ? "&branchName=" + branchName : "") +
            "&commitMessage=" + URLEncoder.encode(commitMessage, "UTF-8"));
    
    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
    conn.setRequestMethod("POST");
    conn.setRequestProperty("Content-Type", "application/json");
    conn.setRequestProperty("Authorization", "Bearer " + authToken);
    conn.setDoOutput(true);
    
    // Convert changes map to JSON
    String jsonChanges = convertMapToJson(changes);
    
    // Send request
    try (OutputStream os = conn.getOutputStream()) {
        byte[] input = jsonChanges.getBytes(StandardCharsets.UTF_8);
        os.write(input, 0, input.length);
    }
    
    // Check response
    int responseCode = conn.getResponseCode();
    if (responseCode >= 200 && responseCode < 300) {
        System.out.println("Chunk pushed successfully");
    } else {
        System.out.println("Failed to push chunk: " + responseCode);
        try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getErrorStream()))) {
            String line;
            while ((line = br.readLine()) != null) {
                System.out.println(line);
            }
        }
        throw new IOException("Failed to push changes, server returned: " + responseCode);
    }
}

// Simple method to convert Map to JSON string
private static String convertMapToJson(Map<String, String> map) {
    StringBuilder json = new StringBuilder("{");
    boolean first = true;
    for (Map.Entry<String, String> entry : map.entrySet()) {
        if (!first) {
            json.append(",");
        }
        first = false;
        json.append("\"").append(escapeJson(entry.getKey())).append("\":\"")
            .append(escapeJson(entry.getValue())).append("\"");
    }
    json.append("}");
    return json.toString();
}

// Simple method to escape JSON string values
private static String escapeJson(String input) {
    return input.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
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
    Map<String, String> changes = FileSync.detectLocalChanges(currentDir);
    
    if (changes.isEmpty()) {
        System.out.println("No changes detected");
        return;
    }
    
    System.out.println("Found " + changes.size() + " changed files");
    
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
    
    // Save changes to a temporary file
    File changesFile = new File(pendingDir, "changes.json");
    try (FileWriter writer = new FileWriter(changesFile)) {
        writer.write(convertMapToJson(changes));
    }
    
    // Update the local state
    FileSync.saveCurrentState(currentDir, changes);
    
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
    Map<String, String> localChanges = FileSync.detectLocalChanges(currentDir);
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
            
            Map<String, String> remoteFiles = parseFilesFromJson(response.toString());
            
            // Write files to local repository
            int updatedFiles = 0;
            for (Map.Entry<String, String> entry : remoteFiles.entrySet()) {
                String filePath = entry.getKey();
                String content = entry.getValue();
                
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
                    shouldUpdate = !currentContent.equals(content);
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

private static void manageBranches(String[] args) throws IOException {
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
    
    // Determine operation: list or create
    if (args.length == 1) {
        // List branches
        listBranches(projectId);
    } else {
        // Create branch
        String branchName = args[1];
        createBranch(projectId, branchName);
    }
}

private static void listBranches(String projectId) throws IOException {
    System.out.println("Fetching branches...");
    
    // Create API request
    URL url = new URL(API_BASE_URL + "/cli/branches?projectId=" + projectId);
    
    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
    conn.setRequestMethod("GET");
    conn.setRequestProperty("Authorization", "Bearer " + authToken);
    
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
            
            // This is a simplified parsing - in a real app use a proper JSON parser
            String responseStr = response.toString();
            int dataStart = responseStr.indexOf("\"data\":[") + 7;
            int dataEnd = responseStr.lastIndexOf("]");
            
            if (dataStart > 7 && dataEnd > dataStart) {
                String branchesJson = responseStr.substring(dataStart, dataEnd + 1);
                
                // Very simple parsing of branches array
                // In a real app, use a JSON parser library
                String[] branches = branchesJson.split("\\},\\{");
                System.out.println("Available branches:");
                
                for (String branch : branches) {
                    // Extract branch name and default status
                    int nameStart = branch.indexOf("\"name\":\"") + 8;
                    int nameEnd = branch.indexOf("\"", nameStart);
                    int defaultStart = branch.indexOf("\"default\":") + 10;
                    int defaultEnd = defaultStart + 5; // true or false
                    
                    if (nameStart > 8 && nameEnd > nameStart) {
                        String name = branch.substring(nameStart, nameEnd);
                        
                        boolean isDefault = false;
                        if (defaultStart > 10 && defaultEnd > defaultStart) {
                            String defaultStr = branch.substring(defaultStart, defaultEnd);
                            isDefault = defaultStr.contains("true");
                        }
                        
                        System.out.println("  " + name + (isDefault ? " (default)" : ""));
                    }
                }
            } else {
                System.out.println("No branches found");
            }
        }
    } else {
        System.out.println("Failed to fetch branches: " + responseCode);
        try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getErrorStream()))) {
            String line;
            while ((line = br.readLine()) != null) {
                System.out.println(line);
            }
        }
    }
}

private static void createBranch(String projectId, String branchName) throws IOException {
    System.out.println("Creating branch: " + branchName);
    
    // Create API request
    URL url = new URL(API_BASE_URL + "/cli/branch?projectId=" + projectId + "&branchName=" + branchName);
    
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
    private static void printHelp() {
        System.out.println("CodeShare CLI - Git-like commands for CodeShare Platform");
        System.out.println("Usage: codeshare <command> [options]");
        System.out.println("Commands:");
        System.out.println("  init              Initialize a new repository");
        System.out.println("  clone <url>       Clone a repository");
        System.out.println("  commit -m <msg>   Commit changes");
        System.out.println("  push              Push changes");
        System.out.println("  pull              Pull changes");
        System.out.println("  branch [name]     List or create branches");
        System.out.println("  login             Login to CodeShare Platform");
    }
}