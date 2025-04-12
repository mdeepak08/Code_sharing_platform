package com.codeshare.platform.util;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Handles file synchronization between local and remote repositories
 */
public class FileSync {
    private static final String LAST_STATE_FILE = "last_state";
    private static final List<String> DEFAULT_IGNORE_PATTERNS = List.of(
        ".git/", ".codeshare/", "target/", "build/", "node_modules/", "dist/",
        "bin/", ".idea/", ".gradle/", "*.class", "*.jar", "*.war", "*.ear", "*.log"
    );
    
    /**
     * Detects changes in the local repository compared to the last known state
     */
    public static List<String> detectLocalChanges(File repoDir) throws IOException, NoSuchAlgorithmException {
        List<String> changedFiles = new ArrayList<>();
        Map<String, String> lastState = loadLastState(repoDir);
        List<String> ignorePatterns = loadIgnorePatterns(repoDir);

        // Only scan for files in the current directory, not the entire file system
        scanForChanges(repoDir, repoDir, ignorePatterns, lastState, changedFiles);

        return changedFiles;
    }
    
    /**
     * Recursively scan for changes in a directory
     */
    private static void scanForChanges(File baseDir, File currentDir, List<String> ignorePatterns, 
                                      Map<String, String> lastState, List<String> changedFiles) 
                                      throws IOException, NoSuchAlgorithmException {
        File[] files = currentDir.listFiles();
        if (files == null) return;
        
        for (File file : files) {
            // Skip directories that match ignore patterns
            if (file.isDirectory()) {
                String relativePath = getRelativePath(baseDir, file) + "/";
                boolean shouldSkip = false;
                
                for (String pattern : ignorePatterns) {
                    if (pattern.endsWith("/") && 
                        (relativePath.equals(pattern) || relativePath.startsWith(pattern))) {
                        shouldSkip = true;
                        break;
                    }
                }
                
                if (!shouldSkip) {
                    scanForChanges(baseDir, file, ignorePatterns, lastState, changedFiles);
                }
            } 
            // Process regular files
            else if (file.isFile()) {
                String relativePath = getRelativePath(baseDir, file);
                
                // Skip files that match ignore patterns
                boolean shouldSkip = false;
                for (String pattern : ignorePatterns) {
                    if (matchesPattern(relativePath, pattern)) {
                        shouldSkip = true;
                        break;
                    }
                }
                
                if (shouldSkip) continue;
                
                // Check if file has changed
                String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
                String hash = calculateHash(content);
                
                if (!lastState.containsKey(relativePath) || !lastState.get(relativePath).equals(hash)) {
                    changedFiles.add(relativePath);
                }
            }
        }
    }
    
    /**
     * Check if a file path matches an ignore pattern
     */
    private static boolean matchesPattern(String path, String pattern) {
        if (pattern.startsWith("*") && path.endsWith(pattern.substring(1))) {
            return true;
        } else if (pattern.endsWith("*") && path.startsWith(pattern.substring(0, pattern.length() - 1))) {
            return true;
        } else if (pattern.equals(path)) {
            return true;
        }
        return false;
    }
    
    /**
     * Load ignore patterns from .gitignore or use defaults
     */
    private static List<String> loadIgnorePatterns(File repoDir) {
        List<String> patterns = new ArrayList<>(DEFAULT_IGNORE_PATTERNS);
        
        File gitIgnore = new File(repoDir, ".gitignore");
        if (gitIgnore.exists() && gitIgnore.isFile()) {
            try {
                List<String> userPatterns = Files.readAllLines(gitIgnore.toPath());
                for (String pattern : userPatterns) {
                    pattern = pattern.trim();
                    if (!pattern.isEmpty() && !pattern.startsWith("#")) {
                        patterns.add(pattern);
                    }
                }
            } catch (IOException e) {
                System.err.println("Error reading .gitignore: " + e.getMessage());
            }
        }
        
        return patterns;
    }
    
    /**
     * Saves the current state of files after sync
     */
    public static void saveCurrentState(File repoDir, Map<String, String> files) throws IOException {
        Map<String, String> currentState = new HashMap<>();
        
        // For each file in the map
        for (Map.Entry<String, String> entry : files.entrySet()) {
            String relativePath = entry.getKey();
            String content = entry.getValue();
            String hash = null;
            
            try {
                hash = calculateHash(content);
            } catch (NoSuchAlgorithmException e) {
                System.err.println("Error calculating hash: " + e.getMessage());
                continue;
            }
            
            currentState.put(relativePath, hash);
        }
        
        // If files is empty, try to load from the actual file system
        if (files.isEmpty()) {
            List<String> ignorePatterns = loadIgnorePatterns(repoDir);
            
            Files.walk(repoDir.toPath())
                .filter(Files::isRegularFile)
                .forEach(path -> {
                    File file = path.toFile();
                    String relativePath = getRelativePath(repoDir, file);
                    
                    // Skip files that match ignore patterns
                    for (String pattern : ignorePatterns) {
                        if (matchesPattern(relativePath, pattern)) {
                            return;
                        }
                    }
                    
                    try {
                        String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
                        String hash = calculateHash(content);
                        currentState.put(relativePath, hash);
                    } catch (IOException | NoSuchAlgorithmException e) {
                        System.err.println("Error processing file: " + path + ": " + e.getMessage());
                    }
                });
        }
        
        // Save current state
        File configDir = ConfigManager.getRepoConfigDir(repoDir);
        if (!configDir.exists()) {
            configDir.mkdirs();
        }
        
        File lastStateFile = new File(configDir, LAST_STATE_FILE);
        try (FileWriter writer = new FileWriter(lastStateFile)) {
            for (Map.Entry<String, String> entry : currentState.entrySet()) {
                writer.write(entry.getKey() + ":" + entry.getValue() + "\n");
            }
        }
    }
    
    /**
     * Loads the last known state of files
     */
    private static Map<String, String> loadLastState(File repoDir) throws IOException {
        Map<String, String> lastState = new HashMap<>();
        
        File configDir = ConfigManager.getRepoConfigDir(repoDir);
        File lastStateFile = new File(configDir, LAST_STATE_FILE);
        if (!lastStateFile.exists()) {
            return lastState;
        }
        
        try (FileReader reader = new FileReader(lastStateFile)) {
            Files.readAllLines(lastStateFile.toPath()).forEach(line -> {
                String[] parts = line.split(":", 2);
                if (parts.length == 2) {
                    lastState.put(parts[0], parts[1]);
                }
            });
        }
        
        return lastState;
    }
    
    /**
     * Calculates a hash for file content
     */
    public static String calculateHash(String content) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(hash);
    }
    
    /**
     * Gets the path of a file relative to the repository root
     */
    private static String getRelativePath(File repoDir, File file) {
        String repoPath = repoDir.getAbsolutePath();
        String filePath = file.getAbsolutePath();
        
        if (filePath.startsWith(repoPath)) {
            String relativePath = filePath.substring(repoPath.length() + 1).replace('\\', '/');
            return relativePath;
        }
        
        return file.getName();
    }
}