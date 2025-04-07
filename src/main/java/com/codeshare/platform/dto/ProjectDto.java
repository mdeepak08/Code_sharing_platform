package com.codeshare.platform.dto;

import java.time.LocalDateTime;

import lombok.Data;

@Data
public class ProjectDto {
    private Long id;
    private String name;
    private String description;
    private boolean isPublic;
    private LocalDateTime createdAt;
    private UserDto owner;
}