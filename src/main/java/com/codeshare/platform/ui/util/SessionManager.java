package com.codeshare.platform.ui.util;

import org.springframework.stereotype.Component;

@Component
public class SessionManager {
    
    private String authToken;
    private String username;
    private Long userId;
    
    public String getAuthToken() {
        return authToken;
    }
    
    public void setAuthToken(String authToken) {
        this.authToken = authToken;
    }
    
    public String getUsername() {
        return username;
    }
    
    public void setUsername(String username) {
        this.username = username;
    }
    
    public Long getUserId() {
        return userId;
    }
    
    public void setUserId(Long userId) {
        this.userId = userId;
    }
    
    public boolean isAuthenticated() {
        return authToken != null && !authToken.isEmpty();
    }
    
    public void clearSession() {
        authToken = null;
        username = null;
        userId = null;
    }
}