package com.codeshare.platform.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.codeshare.platform.dto.ApiResponse;
import com.codeshare.platform.model.Branch;
import com.codeshare.platform.model.Commit;
import com.codeshare.platform.model.File;
import com.codeshare.platform.model.PullRequest;
import com.codeshare.platform.model.User;
import com.codeshare.platform.model.UserActivity;
import com.codeshare.platform.repository.BranchRepository;
import com.codeshare.platform.repository.CommitRepository;
import com.codeshare.platform.repository.FileRepository;
import com.codeshare.platform.repository.ProjectRepository;
import com.codeshare.platform.repository.PullRequestRepository;
import com.codeshare.platform.service.ActivityService;
import com.codeshare.platform.service.UserService;

@RestController
@RequestMapping("/api/activities")
public class ActivityController {

    @Autowired
    private ActivityService activityService;
    
    @Autowired
    private UserService userService;
    
    @Autowired
    private CommitRepository commitRepository;
    
    @Autowired
    private BranchRepository branchRepository;
    
    @Autowired
    private PullRequestRepository pullRequestRepository;
    
    @Autowired
    private FileRepository fileRepository;
    
    @Autowired
    private ProjectRepository projectRepository;
    
    @GetMapping("/recent")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getRecentActivities(
            @RequestParam(defaultValue = "10") int limit,
            Authentication authentication) {
        
        try {
            // Get the current user
            String username = authentication.getName();
            Optional<User> userOpt = userService.getUserByUsername(username);
            
            if (userOpt.isEmpty()) {
                return new ResponseEntity<>(ApiResponse.error("User not found"), HttpStatus.NOT_FOUND);
            }
            
            User user = userOpt.get();
            
            // Get recent activities for the user
            List<UserActivity> activities = activityService.getUserRecentActivities(user, limit);
            
            // Convert to a simplified format for the frontend
            List<Map<String, Object>> simplifiedActivities = new ArrayList<>();
            
            for (UserActivity activity : activities) {
                Map<String, Object> simplified = new HashMap<>();
                simplified.put("id", activity.getId());
                simplified.put("type", activity.getActivityType().toString());
                simplified.put("description", activity.getDescription());
                simplified.put("createdAt", activity.getCreatedAt());
                
                // Process URL to ensure it has all needed parameters
                String resourceUrl = activity.getResourceUrl();
                
                // Add projectId to URL if missing and we can determine it
                if (resourceUrl != null) {
                    // For COMMIT_PUSHED activities
                    if (activity.getActivityType() == UserActivity.ActivityType.COMMIT_PUSHED) {
                        Long commitId = activity.getTargetId();
                        if (commitId != null && !resourceUrl.contains("projectId=")) {
                            // Try to get project ID from the commit
                            Optional<Commit> commitOpt = commitRepository.findById(commitId);
                            if (commitOpt.isPresent()) {
                                Commit commit = commitOpt.get();
                                Long projectId = commit.getBranch().getProject().getId();
                                
                                // Update URL to include projectId
                                if (resourceUrl.contains("?")) {
                                    resourceUrl += "&projectId=" + projectId;
                                } else {
                                    resourceUrl += "?projectId=" + projectId;
                                }
                                
                                // Store projectId separately for frontend use
                                simplified.put("projectId", projectId);
                            }
                        }
                    }
                    
                    // For PULL_REQUEST activities
                    else if (activity.getActivityType().toString().contains("PULL_REQUEST")) {
                        Long prId = activity.getTargetId();
                        if (prId != null && !resourceUrl.contains("projectId=")) {
                            // Try to get project ID from the pull request
                            Optional<PullRequest> prOpt = pullRequestRepository.findById(prId);
                            if (prOpt.isPresent()) {
                                PullRequest pr = prOpt.get();
                                Long projectId = pr.getProject().getId();
                                
                                // Update URL to include projectId
                                if (resourceUrl.contains("?")) {
                                    resourceUrl += "&projectId=" + projectId;
                                } else {
                                    resourceUrl += "?projectId=" + projectId;
                                }
                                
                                // Store projectId separately for frontend use
                                simplified.put("projectId", projectId);
                            }
                        }
                    }
                    
                    // For FILE_EDITED activities
                    else if (activity.getActivityType() == UserActivity.ActivityType.FILE_EDITED) {
                        Long fileId = activity.getTargetId();
                        if (fileId != null && !resourceUrl.contains("projectId=")) {
                            // Try to get project ID from the file
                            Optional<File> fileOpt = fileRepository.findById(fileId);
                            if (fileOpt.isPresent()) {
                                File file = fileOpt.get();
                                Long projectId = file.getProject().getId();
                                
                                // Store projectId separately for frontend use
                                simplified.put("projectId", projectId);
                            }
                        }
                    }
                    
                    // For BRANCH_CREATED activities
                    else if (activity.getActivityType() == UserActivity.ActivityType.BRANCH_CREATED) {
                        Long branchId = activity.getTargetId();
                        if (branchId != null && !resourceUrl.contains("projectId=")) {
                            // Try to get project ID from the branch
                            Optional<Branch> branchOpt = branchRepository.findById(branchId);
                            if (branchOpt.isPresent()) {
                                Branch branch = branchOpt.get();
                                Long projectId = branch.getProject().getId();
                                
                                // Store projectId separately for frontend use
                                simplified.put("projectId", projectId);
                            }
                        }
                    }
                }
                
                simplified.put("resourceUrl", resourceUrl);
                simplified.put("icon", getIconForActivityType(activity.getActivityType()));
                simplified.put("targetId", activity.getTargetId());
                
                simplifiedActivities.add(simplified);
            }
            
            return new ResponseEntity<>(ApiResponse.success(simplifiedActivities), HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(
                ApiResponse.error("Error fetching activities: " + e.getMessage()),
                HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }
    
    // Helper method to determine appropriate icon for each activity type
    private String getIconForActivityType(UserActivity.ActivityType type) {
        switch (type) {
            case PROJECT_CREATED:
            case PROJECT_UPDATED:
                return "fa-folder";
                
            case COMMIT_PUSHED:
                return "fa-code-commit";
                
            case BRANCH_CREATED:
                return "fa-code-branch";
                
            case PULL_REQUEST_CREATED:
            case PULL_REQUEST_MERGED:
            case PULL_REQUEST_CLOSED:
                return "fa-code-pull-request";
                
            case COMMENT_ADDED:
                return "fa-comment";
                
            case FILE_EDITED:
                return "fa-file-code";
                
            default:
                return "fa-history";
        }
    }
}