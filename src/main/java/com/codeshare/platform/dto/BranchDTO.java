package com.codeshare.platform.dto;

import java.time.LocalDateTime;

import com.codeshare.platform.model.Branch;

public class BranchDTO {
    private Long id;
    private String name;
    private boolean isDefault;
    private LocalDateTime createdAt;
    private Long projectId;
    private String projectName;
    
    // Default constructor
    public BranchDTO() {
    }
    
    // Constructor that maps from the entity
    public BranchDTO(Branch branch) {
        this.id = branch.getId();
        this.name = branch.getName();
        this.isDefault = branch.isDefault();
        this.createdAt = branch.getCreatedAt();
        
        // Only access project if it's not null
        if (branch.getProject() != null) {
            this.projectId = branch.getProject().getId();
            this.projectName = branch.getProject().getName();
        }
    }
    
    // Getters and setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public boolean isDefault() {
        return isDefault;
    }
    
    public void setDefault(boolean isDefault) {
        this.isDefault = isDefault;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public Long getProjectId() {
        return projectId;
    }
    
    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }
    
    public String getProjectName() {
        return projectName;
    }
    
    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }
}