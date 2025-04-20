package com.codeshare.platform.dto;

import java.time.LocalDateTime;

import com.codeshare.platform.model.Commit;

public class CommitDTO {
    private Long id;
    private String message;
    private LocalDateTime createdAt;
    private String branchName;
    private Long branchId;
    private UserDto author;
    private String fileChanges;
    
    // Default constructor
    public CommitDTO() {
    }
    
    // Constructor from Commit entity
    public CommitDTO(Commit commit) {
        this.id = commit.getId();
        this.message = commit.getMessage();
        this.createdAt = commit.getCreatedAt();
        
        // Only include the necessary branch information
        if (commit.getBranch() != null) {
            this.branchId = commit.getBranch().getId();
            this.branchName = commit.getBranch().getName();
        }
        
        // Only include necessary author information
        if (commit.getAuthor() != null) {
            this.author = new UserDto();
            this.author.setId(commit.getAuthor().getId());
            this.author.setUsername(commit.getAuthor().getUsername());
            this.author.setEmail(commit.getAuthor().getEmail());
            this.author.setFullName(commit.getAuthor().getFullName());
        }
        
        // Include file changes
        this.fileChanges = commit.getFileChanges();
    }
    
    // Getters and setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public String getBranchName() {
        return branchName;
    }
    
    public void setBranchName(String branchName) {
        this.branchName = branchName;
    }
    
    public Long getBranchId() {
        return branchId;
    }
    
    public void setBranchId(Long branchId) {
        this.branchId = branchId;
    }
    
    public UserDto getAuthor() {
        return author;
    }
    
    public void setAuthor(UserDto author) {
        this.author = author;
    }
    
    public String getFileChanges() {
        return fileChanges;
    }
    
    public void setFileChanges(String fileChanges) {
        this.fileChanges = fileChanges;
    }
}