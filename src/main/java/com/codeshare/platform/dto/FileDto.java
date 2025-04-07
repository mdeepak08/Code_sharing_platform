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
    
    public FileDto(File file) {
        this.id = file.getId();
        this.name = file.getName();
        this.path = file.getPath();
        this.content = file.getContent();
        if (file.getProject() != null) {
            this.projectId = file.getProject().getId();
            this.projectName = file.getProject().getName();
        }
    }
}