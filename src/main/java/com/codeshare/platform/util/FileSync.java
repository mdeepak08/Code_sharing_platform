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
    
    /**
     * Detects changes in the local repository compared to the last known state
     */
    public static List<String> detectLocalChanges(File repoDir) throws IOException, NoSuchAlgorithmException {
        List<String> changedFiles = new ArrayList<>();
        Map<String, String> lastState = loadLastState(repoDir);

        Files.walk(repoDir.toPath())
                .filter(Files::isRegularFile)
                .forEach(path -> {
                    try {
                        File file = path.toFile();
                        if (file.isHidden() || file.getPath().contains("/.codeshare/")) {
                            return;
                        }

                        String relativePath = getRelativePath(repoDir, file);
                        String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
                        String hash = calculateHash(content);

                        if (!lastState.containsKey(relativePath) || !lastState.get(relativePath).equals(hash)) {
                            changedFiles.add(relativePath); // Only store the relative path
                        }
                    } catch (IOException | NoSuchAlgorithmException e) {
                        System.err.println("Error processing file: " + path + ": " + e.getMessage());
                    }
                });

        return changedFiles;
    }
    
    /**
     * Saves the current state of files after sync
     */
    public static void saveCurrentState(File repoDir, Map<String, String> files) throws IOException, NoSuchAlgorithmException {
        Map<String, String> currentState = new HashMap<>();
        
        for (Map.Entry<String, String> entry : files.entrySet()) {
            String relativePath = entry.getKey();
            String content = entry.getValue();
            String hash = calculateHash(content);
            currentState.put(relativePath, hash);
        }
        
        // Save current state
        File lastStateFile = new File(ConfigManager.getRepoConfigDir(repoDir), LAST_STATE_FILE);
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
        
        File lastStateFile = new File(ConfigManager.getRepoConfigDir(repoDir), LAST_STATE_FILE);
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
