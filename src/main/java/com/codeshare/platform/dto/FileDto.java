package com.codeshare.platform.dto;

import com.codeshare.platform.model.File;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class FileDto {
    private Long id;
    private String name;
    private String path;
    private String content;
    private Long projectId;
    private String projectName;
    private String directory; // Add this field
    private Long branchId;
    
    public FileDto(File file) {
        this.id = file.getId();
        this.name = file.getName();
        this.path = file.getPath();
        this.content = file.getContent();
        
        // Extract directory from path
        if (file.getPath() != null && file.getPath().contains("/")) {
            this.directory = file.getPath().substring(0, file.getPath().lastIndexOf('/'));
        } else {
            this.directory = "";
        }
        
        if (file.getProject() != null) {
            this.projectId = file.getProject().getId();
            this.projectName = file.getProject().getName();
        }
    }
    public Long getBranchId() {
        return branchId;
    }
    public void setBranchId(Long branchId) {
        this.branchId = branchId;
    }
}