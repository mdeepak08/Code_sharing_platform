package com.codeshare.platform.model;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "user_activities")
public class UserActivity {
    
    public enum ActivityType {
        PROJECT_CREATED,
        PROJECT_UPDATED,
        COMMIT_PUSHED,
        BRANCH_CREATED,
        PULL_REQUEST_CREATED,
        PULL_REQUEST_MERGED,
        PULL_REQUEST_CLOSED,
        COMMENT_ADDED,
        FILE_EDITED
    }
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ActivityType activityType;
    
    @Column(nullable = false)
    private String description;
    
    // Target entity IDs (can be project, PR, file, etc.)
    private Long targetId;
    
    // Additional details in JSON format if needed
    @Column(columnDefinition = "TEXT")
    private String details;
    
    // Resource URL for linking to the activity
    private String resourceUrl;
    
    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}