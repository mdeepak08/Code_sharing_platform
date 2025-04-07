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
    
    private static void commitChanges(String[] args) {
        System.out.println("Not implemented yet");
        // TODO: Implement commit functionality
    }
    
    private static void pushChanges(String[] args) {
        System.out.println("Not implemented yet");
        // TODO: Implement push functionality
    }
    
    private static void pullChanges(String[] args) {
        System.out.println("Not implemented yet");
        // TODO: Implement pull functionality
    }
    
    private static void manageBranches(String[] args) {
        System.out.println("Not implemented yet");
        // TODO: Implement branch management functionality
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