package com.codeshare.platform.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "pull_requests")
public class PullRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String title;
    
    @Column(columnDefinition = "TEXT")
    private String description;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_branch_id", nullable = false)
    private Branch sourceBranch;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_branch_id", nullable = false)
    private Branch targetBranch;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    private User author;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PullRequestStatus status = PullRequestStatus.OPEN;
    
    @OneToMany(mappedBy = "pullRequest", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Comment> comments = new ArrayList<>();
    
    @OneToMany(mappedBy = "pullRequest", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PullRequestReview> reviews = new ArrayList<>();
    
    private boolean mergeable;
    
    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
    
    private LocalDateTime updatedAt;
    private LocalDateTime mergedAt;
    private LocalDateTime closedAt;
}