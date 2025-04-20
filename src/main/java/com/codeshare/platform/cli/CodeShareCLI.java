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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codeshare.platform.util.ConfigManager;
import com.codeshare.platform.util.FileSync;

public class CodeShareCLI {
    private static final String API_BASE_URL = "http://localhost:8080/api";
    private static String authToken;
    private static final Logger logger = LoggerFactory.getLogger(CodeShareCLI.class);

    
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
    
    private static void login(String[] args) { // Removed throws IOException, handle inside
        Console console = System.console();
        if (console == null) {
            System.err.println("Error: No console available. Cannot read credentials.");
            return;
        }

        String username = console.readLine("Username: ");
        char[] passwordChars = console.readPassword("Password: ");
        String password = new String(passwordChars);

        // --- Best Practice: Clear password from memory ASAP ---
        Arrays.fill(passwordChars, ' ');
        // ---

        HttpURLConnection conn = null; // Declare outside try for finally block

        try {
            URL url = new URL(API_BASE_URL + "/auth/login");
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; utf-8"); // Specify charset
            conn.setRequestProperty("Accept", "application/json"); // Still good practice to set Accept
            conn.setDoOutput(true); // Indicate we are sending data
            conn.setConnectTimeout(10000); // 10 seconds connection timeout
            conn.setReadTimeout(10000);    // 10 seconds read timeout

            // --- Prepare JSON payload ---
            String jsonInputString = String.format("{\"username\": \"%s\", \"password\": \"%s\"}", username, password);
            password = null; // Clear the String version of the password

            // --- Send request payload (try-with-resources) ---
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonInputString.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            // --- Check server response code ---
            int responseCode = conn.getResponseCode();
            System.out.println("DEBUG: Server responded with HTTP code: " + responseCode);

            if (responseCode == HttpURLConnection.HTTP_OK) { // Success (200)
                // --- Read response body (try-with-resources) ---
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {

                    StringBuilder response = new StringBuilder();
                    String responseLine;
                    while ((responseLine = br.readLine()) != null) {
                        response.append(responseLine.trim());
                    }
                    String responseStr = response.toString();
                    System.out.println("DEBUG: Raw server response: " + responseStr);

                    // --- Parse response using Regex ---
                    try {
                        // Regex explained in previous answer: finds "accessToken":"<value>"
                        // Slightly improved to be less sensitive to whitespace around colons/braces
                         Pattern pattern = Pattern.compile("\"data\"\\s*:\\s*\\{\\s*.*?\"accessToken\"\\s*:\\s*\"([^\"]+)\"");
                         Matcher matcher = pattern.matcher(responseStr);

                        if (matcher.find()) {
                            // Group 1 contains the captured token value
                            String extractedToken = matcher.group(1);

                            if (extractedToken != null && !extractedToken.isEmpty()) {
                                authToken = extractedToken; // Store the token

                                System.out.println("===========================================");
                                System.out.println("Token extracted successfully using Regex.");
                                // Avoid printing token unless necessary for debugging
                                // System.out.println("Token starts with: " + authToken.substring(0, Math.min(10, authToken.length())));

                                ConfigManager.saveToken(authToken); // Save token

                                System.out.println("Login successful!");
                                // Note: Cannot easily extract server message with this simple Regex
                                System.out.println("=============================================");
                            } else {
                                System.err.println("Login Error: Regex matched, but the extracted 'accessToken' was empty.");
                            }
                        } else {
                             System.err.println("Login Error: Could not find the 'accessToken' pattern in the server response using Regex.");
                             System.err.println("Response was: " + responseStr);
                        }

                    } catch (PatternSyntaxException e) {
                        // This catches errors in your Regex pattern itself
                        System.err.println("Internal Error: Invalid Regex pattern syntax: " + e.getMessage());
                    } catch (Exception e) {
                         // Catch other potential runtime errors during matching
                        System.err.println("Error during Regex processing: " + e.getMessage());
                    }
                    // --- End Regex Parsing ---

                } // End try-with-resources for BufferedReader

            } else { // Handle non-200 responses
                 System.err.println("Login failed. Server responded with HTTP code: " + responseCode + " " + conn.getResponseMessage());
                // --- Attempt to read error response body ---
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
                } catch (IOException | NullPointerException e) { // Catch if getErrorStream() is null or fails
                     System.err.println("Could not read detailed error message from server.");
                }
            }

        } catch (MalformedURLException e) {
            System.err.println("Internal Error: Invalid API URL configured: " + e.getMessage());
        } catch (IOException e) {
            System.err.println("Network Error: Could not connect to server or timed out during login.");
            System.err.println("Details: " + e.getMessage());
             // Consider logging the stack trace for detailed network debugging if needed
             // e.printStackTrace();
        } finally {
            if (conn != null) {
                conn.disconnect(); // Always disconnect
            }
             // Explicitly nullify potentially sensitive data
             password = null;
             username = null;
        }
    } // End login method

    
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
                // Simple JSON parsing - in real app use proper JSON parser
                // This is very simplified - in a real app use a proper JSON parser
                Map<String, String> files = parseFilesFromJson(response.toString());
                
                // Write files to target directory
                for (Map.Entry<String, String> entry : files.entrySet()) {
                    String filePath = entry.getKey();
                    String content = entry.getValue();
                    
                    // Normalize line endings to match the local system
                    content = content.replaceAll("\r\n|\r|\n", System.lineSeparator());
                    
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
                    .replace("\\\\", "\\")
                    .replace("\\r", "\r");
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

    // Limit number of files per push to avoid overwhelming the server
    final int MAX_FILES_PER_CHUNK = 10;
    Map<String, String> chunk = new HashMap<>();
    int fileCount = 0;
    int chunkCount = 0;
    
    // Process files in chunks
    for (String filePath : changedFiles) {
        // Read file content
        File file = new File(currentDir, filePath);
        if (!file.exists()) {
            System.out.println("Skipping non-existent file: " + filePath);
            continue;
        }

        try {
            // Read file content
            String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            
            // Normalize to Unix line endings (LF) when sending to server
            content = content.replaceAll("\r\n|\r", "\n");
            
            // Filter out null bytes
            content = content.replace("\u0000", "");
            
            chunk.put(filePath, content);
            fileCount++;

            // Push when chunk is full or this is the last file
            if (fileCount >= MAX_FILES_PER_CHUNK || fileCount >= changedFiles.size()) {
                System.out.println("Pushing chunk " + (chunkCount + 1) + " of " +
                        (int)Math.ceil((double)changedFiles.size() / MAX_FILES_PER_CHUNK) +
                        " (" + chunk.size() + " files)");

                pushChunk(projectId, branchName, chunk,
                        chunkCount > 0 ? commitMessage + " (part " + (chunkCount + 1) + ")" : commitMessage);

                // Update state after successful push
                FileSync.saveCurrentState(currentDir, chunk);

                chunk.clear();
                fileCount = 0;
                chunkCount++;
            }
        } catch (IOException e) {
            System.err.println("Error reading file: " + filePath + ": " + e.getMessage());
        }
    }

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

// Simple method to escape JSON string values (Improved)
private static String escapeJson(String input) {
    if (input == null) {
        return null; // Or return "" or throw exception depending on desired behavior for null
    }
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < input.length(); i++) {
        char c = input.charAt(i);
        switch (c) {
            case '"':
                sb.append("\\\"");
                break;
            case '\\':
                sb.append("\\\\");
                break;
            case '\b': // Backspace
                sb.append("\\b");
                break;
            case '\f': // Form feed
                sb.append("\\f");
                break;
            case '\n': // Newline
                sb.append("\\n");
                break;
            case '\r': // Carriage return
                sb.append("\\r");
                break;
            case '\t': // Tab
                sb.append("\\t");
                break;
            default:
                // Escape control characters (U+0000 to U+001F)
                if (c >= '\u0000' && c <= '\u001F') {
                    String hex = Integer.toHexString(c);
                    sb.append("\\u");
                    for (int k = 0; k < 4 - hex.length(); k++) {
                        sb.append('0');
                    }
                    sb.append(hex);
                } else {
                    // Normal character
                    sb.append(c);
                }
                break;
        }
    }
    return sb.toString();
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
        List<String> changedFiles = FileSync.detectLocalChanges(currentDir); // Changed the variable name to changedFiles and type to List<String>

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

    // Update the local state - now you need to pass the *list of changed files*
    // so that saveCurrentState can calculate and store the new hashes.
    Map<String, String> currentHashes = new HashMap<>();
    for (String relativePath : changedFiles) {
        File file = new File(currentDir, relativePath);
        if (file.isFile()) {
            String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            currentHashes.put(relativePath, FileSync.calculateHash(content));
        }
    }
    FileSync.saveCurrentState(currentDir, currentHashes);

    System.out.println("Changes committed locally (list of changed files and their hashes saved). Use 'codeshare push' to push to remote repository.");
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

        // Detect local changes first to warn user if there are uncommitted changes
        List<String> localChanges = FileSync.detectLocalChanges(currentDir); // Change to List<String>

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
            // Write files to local repository
            for (Map.Entry<String, String> entry : remoteFiles.entrySet()) {
                String filePath = entry.getKey();
                String content = entry.getValue();
                
                // Normalize line endings to match the local system
                content = content.replaceAll("\r\n|\r|\n", System.lineSeparator());
                
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
                    currentContent = currentContent.replaceAll("\r\n|\r|\n", "\n");
                    String normalizedNewContent = content.replaceAll("\r\n|\r|\n", "\n");
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
    } else { // Handle non-200 responses
            System.out.println("Failed to fetch branches: " + responseCode);
             // --- ADD NULL CHECK HERE ---
            try {
            InputStream errorStream = conn.getErrorStream(); // Get the stream first
             if (errorStream != null) { // Check if the stream is NOT null
            try (BufferedReader br = new BufferedReader(new InputStreamReader(errorStream))) {
            String line;
            while ((line = br.readLine()) != null) {
            System.out.println(line); // Print server error details
            }
            }
            } else {
             // Handle case where getErrorStream is null
            System.out.println("No detailed error message body received from server.");
            }
            } catch (IOException e) {
             // Catch potential IO errors during stream reading itself
            System.out.println("Error reading error stream: " + e.getMessage());
            }
            return;
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

