package com.codeshare.platform.util;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
    
    /**
     * Detects changes in the local repository compared to the last known state
     */
    public static List<String> detectLocalChanges(File repoDir) throws IOException, NoSuchAlgorithmException {
        List<String> changedFiles = new ArrayList<>();
        Map<String, String> lastState = loadLastState(repoDir);
        
        // Create a .gitignore style filter
        List<String> ignorePatterns = getIgnorePatterns(repoDir);
        
        // Walk the directory tree and detect changes
        Files.walk(repoDir.toPath())
            .filter(path -> Files.isRegularFile(path) && !shouldIgnore(repoDir, path, ignorePatterns))
            .forEach(path -> {
                try {
                    String relativePath = getRelativePath(repoDir, path.toFile());
                    String content = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
                    String hash = calculateHash(content);
                    
                    if (!lastState.containsKey(relativePath) || !lastState.get(relativePath).equals(hash)) {
                        changedFiles.add(relativePath);
                    }
                } catch (IOException | NoSuchAlgorithmException e) {
                    System.err.println("Error processing file: " + path + ": " + e.getMessage());
                }
            });
        
        return changedFiles;
    }
    
    /**
     * Check if a file should be ignored based on .gitignore patterns
     */
    private static boolean shouldIgnore(File repoDir, Path path, List<String> ignorePatterns) {
        // Skip .codeshare directory
        if (path.toString().contains("/.codeshare/") || path.toString().contains("\\.codeshare\\")) {
            return true;
        }
        
        // Get the relative path
        String relativePath = getRelativePath(repoDir, path.toFile());
        
        // Check against ignore patterns
        for (String pattern : ignorePatterns) {
            // Simplify: just do basic matching
            if (pattern.endsWith("/")) {
                // Directory pattern
                if (relativePath.startsWith(pattern) || 
                    relativePath.startsWith(pattern.substring(0, pattern.length() - 1))) {
                    return true;
                }
            } else if (pattern.startsWith("*")) {
                // Wildcard at beginning
                if (relativePath.endsWith(pattern.substring(1))) {
                    return true;
                }
            } else if (pattern.endsWith("*")) {
                // Wildcard at end
                if (relativePath.startsWith(pattern.substring(0, pattern.length() - 1))) {
                    return true;
                }
            } else if (relativePath.equals(pattern) || relativePath.startsWith(pattern + "/")) {
                // Exact match or directory prefix
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Get standard ignore patterns + .gitignore content
     */
    private static List<String> getIgnorePatterns(File repoDir) {
        List<String> patterns = new ArrayList<>();
        
        // Standard patterns to ignore
        patterns.add(".git/");
        patterns.add(".codeshare/");
        patterns.add("target/");
        patterns.add("build/");
        patterns.add("bin/");
        patterns.add("out/");
        patterns.add(".idea/");
        patterns.add(".gradle/");
        patterns.add("node_modules/");
        
        // Add patterns from .gitignore if it exists
        File gitignore = new File(repoDir, ".gitignore");
        if (gitignore.exists() && gitignore.isFile()) {
            try {
                List<String> gitignoreLines = Files.readAllLines(gitignore.toPath());
                for (String line : gitignoreLines) {
                    line = line.trim();
                    if (!line.isEmpty() && !line.startsWith("#")) {
                        patterns.add(line);
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
    public static void saveCurrentState(File repoDir, Map<String, String> files) throws IOException, NoSuchAlgorithmException {
        Map<String, String> currentState = new HashMap<>();
        
        // For each file in the map, calculate its hash
        for (Map.Entry<String, String> entry : files.entrySet()) {
            String relativePath = entry.getKey();
            String content = entry.getValue();
            String hash = calculateHash(content);
            currentState.put(relativePath, hash);
        }
        
        // If no specific files were provided, use the current file system state
        if (files.isEmpty()) {
            List<String> ignorePatterns = getIgnorePatterns(repoDir);
            
            Files.walk(repoDir.toPath())
                .filter(path -> Files.isRegularFile(path) && !shouldIgnore(repoDir, path, ignorePatterns))
                .forEach(path -> {
                    try {
                        String relativePath = getRelativePath(repoDir, path.toFile());
                        String content = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
                        String hash = calculateHash(content);
                        currentState.put(relativePath, hash);
                    } catch (IOException | NoSuchAlgorithmException e) {
                        System.err.println("Error processing file: " + path + ": " + e.getMessage());
                    }
                });
        }
        
        // Save the current state
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
            return filePath.substring(repoPath.length() + 1).replace('\\', '/');
        }
        
        return file.getName();
    }
}