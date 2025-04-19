package com.codeshare.platform.dto;

import java.time.LocalDateTime;

import com.codeshare.platform.model.PullRequestStatus;

import lombok.Data;

@Data
public class PullRequestDto {
    private Long id;
    private String title;
    private String description;
    private Long projectId;
    private String projectName;
    private Long sourceBranchId;
    private String sourceBranchName;
    private Long targetBranchId;
    private String targetBranchName;
    private UserDto author;
    private PullRequestStatus status;
    private boolean mergeable;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime mergedAt;
    private LocalDateTime closedAt;
    private int commentsCount;
    private int reviewsCount;
}