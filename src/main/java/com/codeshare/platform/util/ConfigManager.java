package com.codeshare.platform.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Manages configuration files for the CodeShare CLI
 */
public class ConfigManager {
    private static final String CONFIG_DIR = ".codeshare";
    private static final String TOKEN_FILE = "token";
    private static final String CONFIG_FILE = "config";
    
    /**
     * Gets the config directory in the user's home directory
     */
    public static File getConfigDir() {
        File configDir = new File(System.getProperty("user.home"), CONFIG_DIR);
        if (!configDir.exists()) {
            configDir.mkdir();
        }
        return configDir;
    }
    
    /**
     * Gets the repository config directory
     */
    public static File getRepoConfigDir(File repoDir) {
        File configDir = new File(repoDir, CONFIG_DIR);
        if (!configDir.exists()) {
            configDir.mkdir();
        }
        return configDir;
    }
    
    /**
     * Saves the auth token to the global config
     */
    public static void saveToken(String token) throws IOException {
        File tokenFile = new File(getConfigDir(), TOKEN_FILE);
        try (FileWriter writer = new FileWriter(tokenFile)) {
            writer.write(token);
        }
    }
    
    /**
     * Loads the auth token from the global config
     */
    public static String loadToken() throws IOException {
        File tokenFile = new File(getConfigDir(), TOKEN_FILE);
        if (!tokenFile.exists()) {
            return null;
        }
        
        try (BufferedReader reader = new BufferedReader(new FileReader(tokenFile))) {
            return reader.readLine();
        }
    }
    
    /**
     * Saves repository configuration
     */
    public static void saveRepoConfig(File repoDir, Map<String, String> config) throws IOException {
        Properties properties = new Properties();
        properties.putAll(config);
        
        File configFile = new File(getRepoConfigDir(repoDir), CONFIG_FILE);
        try (FileWriter writer = new FileWriter(configFile)) {
            properties.store(writer, "CodeShare Repository Configuration");
        }
    }
    
    /**
     * Loads repository configuration
     */
    public static Map<String, String> loadRepoConfig(File repoDir) throws IOException {
        File configFile = new File(getRepoConfigDir(repoDir), CONFIG_FILE);
        if (!configFile.exists()) {
            return new HashMap<>();
        }
        
        Properties properties = new Properties();
        try (FileReader reader = new FileReader(configFile)) {
            properties.load(reader);
        }
        
        Map<String, String> config = new HashMap<>();
        for (String key : properties.stringPropertyNames()) {
            config.put(key, properties.getProperty(key));
        }
        
        return config;
    }
}