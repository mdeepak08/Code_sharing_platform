package com.codeshare.platform.controller;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.codeshare.platform.dto.ApiResponse;
import com.codeshare.platform.model.Branch;
import com.codeshare.platform.model.Project;
import com.codeshare.platform.model.PullRequest;
import com.codeshare.platform.model.PullRequestStatus;
import com.codeshare.platform.model.User;
import com.codeshare.platform.service.BranchService;
import com.codeshare.platform.service.ProjectService;
import com.codeshare.platform.service.PullRequestService;
import com.codeshare.platform.service.UserService;

@RestController
@RequestMapping("/api/pull-requests")
public class PullRequestController {

    private final PullRequestService pullRequestService;
    private final ProjectService projectService;
    private final BranchService branchService;
    private final UserService userService;

    @Autowired
    public PullRequestController(PullRequestService pullRequestService,
                                ProjectService projectService,
                                BranchService branchService,
                                UserService userService) {
        this.pullRequestService = pullRequestService;
        this.projectService = projectService;
        this.branchService = branchService;
        this.userService = userService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PullRequest>> createPullRequest(
            @RequestBody Map<String, Object> requestBody,
            Authentication authentication) {
        
        String username = authentication.getName();
        Optional<User> authorOpt = userService.getUserByUsername(username);
        
        if (authorOpt.isEmpty()) {
            return new ResponseEntity<>(ApiResponse.error("User not found"), HttpStatus.NOT_FOUND);
        }
        
        Long projectId = Long.parseLong(requestBody.get("projectId").toString());
        Long sourceBranchId = Long.parseLong(requestBody.get("sourceBranchId").toString());
        Long targetBranchId = Long.parseLong(requestBody.get("targetBranchId").toString());
        String title = (String) requestBody.get("title");
        String description = (String) requestBody.get("description");
        
        Optional<Project> projectOpt = projectService.getProjectById(projectId);
        Optional<Branch> sourceBranchOpt = branchService.getBranchById(sourceBranchId);
        Optional<Branch> targetBranchOpt = branchService.getBranchById(targetBranchId);
        
        if (projectOpt.isEmpty()) {
            return new ResponseEntity<>(ApiResponse.error("Project not found"), HttpStatus.NOT_FOUND);
        }
        
        if (sourceBranchOpt.isEmpty() || targetBranchOpt.isEmpty()) {
            return new ResponseEntity<>(ApiResponse.error("Branch not found"), HttpStatus.NOT_FOUND);
        }
        
        // Create Pull Request
        PullRequest pullRequest = new PullRequest();
        pullRequest.setProject(projectOpt.get());
        pullRequest.setSourceBranch(sourceBranchOpt.get());
        pullRequest.setTargetBranch(targetBranchOpt.get());
        pullRequest.setTitle(title);
        pullRequest.setDescription(description);
        pullRequest.setAuthor(authorOpt.get());
        
        PullRequest createdPR = pullRequestService.createPullRequest(pullRequest);
        
        return new ResponseEntity<>(
            ApiResponse.success("Pull request created successfully", createdPR),
            HttpStatus.CREATED
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PullRequest>> getPullRequestById(@PathVariable Long id) {
        Optional<PullRequest> pullRequestOpt = pullRequestService.getPullRequestById(id);
        
        if (pullRequestOpt.isEmpty()) {
            return new ResponseEntity<>(ApiResponse.error("Pull request not found"), HttpStatus.NOT_FOUND);
        }
        
        return new ResponseEntity<>(
            ApiResponse.success(pullRequestOpt.get()),
            HttpStatus.OK
        );
    }

    @GetMapping("/project/{projectId}")
    public ResponseEntity<ApiResponse<List<PullRequest>>> getPullRequestsByProject(
            @PathVariable Long projectId,
            @RequestParam(required = false) String status) {
        
        Optional<Project> projectOpt = projectService.getProjectById(projectId);
        
        if (projectOpt.isEmpty()) {
            return new ResponseEntity<>(ApiResponse.error("Project not found"), HttpStatus.NOT_FOUND);
        }
        
        List<PullRequest> pullRequests;
        
        if (status != null) {
            try {
                PullRequestStatus requestStatus = PullRequestStatus.valueOf(status.toUpperCase());
                pullRequests = pullRequestService.getPullRequestsByProjectAndStatus(
                    projectOpt.get(), requestStatus);
            } catch (IllegalArgumentException e) {
                return new ResponseEntity<>(ApiResponse.error("Invalid status"), HttpStatus.BAD_REQUEST);
            }
        } else {
            pullRequests = pullRequestService.getPullRequestsByProject(projectOpt.get());
        }
        
        return new ResponseEntity<>(
            ApiResponse.success(pullRequests),
            HttpStatus.OK
        );
    }

    @PostMapping("/{id}/merge")
    public ResponseEntity<ApiResponse<Void>> mergePullRequest(
            @PathVariable Long id,
            @RequestBody Map<String, String> requestBody,
            Authentication authentication) {
        
        String username = authentication.getName();
        Optional<User> mergerOpt = userService.getUserByUsername(username);
        
        if (mergerOpt.isEmpty()) {
            return new ResponseEntity<>(ApiResponse.error("User not found"), HttpStatus.NOT_FOUND);
        }
        
        Optional<PullRequest> pullRequestOpt = pullRequestService.getPullRequestById(id);
        
        if (pullRequestOpt.isEmpty()) {
            return new ResponseEntity<>(ApiResponse.error("Pull request not found"), HttpStatus.NOT_FOUND);
        }
        
        PullRequest pullRequest = pullRequestOpt.get();
        
        // Check if PR is in OPEN state
        if (pullRequest.getStatus() != PullRequestStatus.OPEN) {
            return new ResponseEntity<>(
                ApiResponse.error("Cannot merge: Pull request is not open"),
                HttpStatus.BAD_REQUEST
            );
        }
        
        // Check if PR is mergeable
        if (!pullRequest.isMergeable()) {
            return new ResponseEntity<>(
                ApiResponse.error("Cannot merge: Pull request has conflicts"),
                HttpStatus.CONFLICT
            );
        }
        
        String mergeMessage = requestBody.getOrDefault("message", "Merge pull request #" + id);
        
        try {
            pullRequestService.mergePullRequest(pullRequest, mergerOpt.get(), mergeMessage);
            return new ResponseEntity<>(
                ApiResponse.success("Pull request merged successfully", null),
                HttpStatus.OK
            );
        } catch (Exception e) {
            return new ResponseEntity<>(
                ApiResponse.error("Failed to merge: " + e.getMessage()),
                HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }

    @PostMapping("/{id}/close")
    public ResponseEntity<ApiResponse<Void>> closePullRequest(
            @PathVariable Long id,
            Authentication authentication) {
        
        String username = authentication.getName();
        Optional<User> closerOpt = userService.getUserByUsername(username);
        
        if (closerOpt.isEmpty()) {
            return new ResponseEntity<>(ApiResponse.error("User not found"), HttpStatus.NOT_FOUND);
        }
        
        Optional<PullRequest> pullRequestOpt = pullRequestService.getPullRequestById(id);
        
        if (pullRequestOpt.isEmpty()) {
            return new ResponseEntity<>(ApiResponse.error("Pull request not found"), HttpStatus.NOT_FOUND);
        }
        
        PullRequest pullRequest = pullRequestOpt.get();
        
        // Check if PR is in OPEN state
        if (pullRequest.getStatus() != PullRequestStatus.OPEN) {
            return new ResponseEntity<>(
                ApiResponse.error("Cannot close: Pull request is not open"),
                HttpStatus.BAD_REQUEST
            );
        }
        
        pullRequestService.closePullRequest(pullRequest, closerOpt.get());
        
        return new ResponseEntity<>(
            ApiResponse.success("Pull request closed successfully", null),
            HttpStatus.OK
        );
    }

    @GetMapping("/{id}/diff")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getDiffStats(@PathVariable Long id) {
        Optional<PullRequest> pullRequestOpt = pullRequestService.getPullRequestById(id);
        
        if (pullRequestOpt.isEmpty()) {
            return new ResponseEntity<>(ApiResponse.error("Pull request not found"), HttpStatus.NOT_FOUND);
        }
        
        Map<String, Object> diffStats = pullRequestService.getDiffStats(pullRequestOpt.get());
        
        return new ResponseEntity<>(
            ApiResponse.success(diffStats),
            HttpStatus.OK
        );
    }

    @GetMapping("/{id}/files")
    public ResponseEntity<ApiResponse<List<String>>> getChangedFiles(@PathVariable Long id) {
        Optional<PullRequest> pullRequestOpt = pullRequestService.getPullRequestById(id);
        
        if (pullRequestOpt.isEmpty()) {
            return new ResponseEntity<>(ApiResponse.error("Pull request not found"), HttpStatus.NOT_FOUND);
        }
        
        List<String> changedFiles = pullRequestService.getChangedFiles(pullRequestOpt.get());
        
        return new ResponseEntity<>(
            ApiResponse.success(changedFiles),
            HttpStatus.OK
        );
    }

    @GetMapping("/user/count")
public ResponseEntity<ApiResponse<Integer>> getUserPullRequestCount(Authentication authentication) {
    String username = authentication.getName();
    Optional<User> userOpt = userService.getUserByUsername(username);
    
    if (userOpt.isEmpty()) {
        return new ResponseEntity<>(ApiResponse.error("User not found"), HttpStatus.NOT_FOUND);
    }
    
    List<PullRequest> pullRequests = pullRequestService.getPullRequestsByAuthor(userOpt.get());
    int openPRsCount = (int) pullRequests.stream()
        .filter(pr -> pr.getStatus() == PullRequestStatus.OPEN)
        .count();
    
    return new ResponseEntity<>(ApiResponse.success(openPRsCount), HttpStatus.OK);
}

@GetMapping("/user")
public ResponseEntity<ApiResponse<List<PullRequest>>> getUserPullRequests(
        @RequestParam(required = false, defaultValue = "created-by-me") String filter,
        @RequestParam(required = false, defaultValue = "open") String status,
        @RequestParam(required = false, defaultValue = "newest") String sort,
        @RequestParam(required = false, defaultValue = "") String search,
        Authentication authentication) {
    
    String username = authentication.getName();
    Optional<User> userOpt = userService.getUserByUsername(username);
    
    if (userOpt.isEmpty()) {
        return new ResponseEntity<>(ApiResponse.error("User not found"), HttpStatus.NOT_FOUND);
    }
    
    User user = userOpt.get();
    List<PullRequest> pullRequests;
    
    // Get pull requests based on filter
    if (filter.equals("created-by-me")) {
        pullRequests = pullRequestService.getPullRequestsByAuthor(user);
    } else {
        // For future: implement getting PRs assigned to the user or mentioning the user
        // For now, return same results for all filters
        pullRequests = pullRequestService.getPullRequestsByAuthor(user);
    }
    
    // Filter by status
    if (!status.equals("all")) {
        PullRequestStatus prStatus;
        try {
            prStatus = PullRequestStatus.valueOf(status.toUpperCase());
            pullRequests = pullRequests.stream()
                .filter(pr -> pr.getStatus() == prStatus)
                .collect(Collectors.toList());
        } catch (IllegalArgumentException e) {
            // Invalid status, ignore filter
        }
    }
    
    // Filter by search term
    if (!search.isEmpty()) {
        String searchLower = search.toLowerCase();
        pullRequests = pullRequests.stream()
            .filter(pr -> 
                pr.getTitle().toLowerCase().contains(searchLower) || 
                (pr.getDescription() != null && pr.getDescription().toLowerCase().contains(searchLower))
            )
            .collect(Collectors.toList());
    }
    
    // Sort results
    switch (sort) {
        case "oldest":
            pullRequests.sort(Comparator.comparing(PullRequest::getCreatedAt));
            break;
        case "recently-updated":
            pullRequests.sort((pr1, pr2) -> {
                LocalDateTime date1 = pr1.getUpdatedAt() != null ? pr1.getUpdatedAt() : pr1.getCreatedAt();
                LocalDateTime date2 = pr2.getUpdatedAt() != null ? pr2.getUpdatedAt() : pr2.getCreatedAt();
                return date2.compareTo(date1);
            });
            break;
        case "least-updated":
            pullRequests.sort((pr1, pr2) -> {
                LocalDateTime date1 = pr1.getUpdatedAt() != null ? pr1.getUpdatedAt() : pr1.getCreatedAt();
                LocalDateTime date2 = pr2.getUpdatedAt() != null ? pr2.getUpdatedAt() : pr2.getCreatedAt();
                return date1.compareTo(date2);
            });
            break;
        case "newest":
        default:
            pullRequests.sort(Comparator.comparing(PullRequest::getCreatedAt).reversed());
            break;
    }
    
    return new ResponseEntity<>(ApiResponse.success(pullRequests), HttpStatus.OK);
}

@GetMapping("/trending")
public ResponseEntity<ApiResponse<List<PullRequest>>> getTrendingPullRequests() {
    // In a real implementation, you might define "trending" based on 
    // recent activity, number of comments, or other metrics
    
    // For now, just get recent open pull requests
    List<PullRequest> allPRs = pullRequestService.getAllOpenPullRequests();
    
    // Sort by most recently created and take top 5
    List<PullRequest> trending = allPRs.stream()
        .sorted(Comparator.comparing(PullRequest::getCreatedAt).reversed())
        .limit(5)
        .collect(Collectors.toList());
    
    return new ResponseEntity<>(ApiResponse.success(trending), HttpStatus.OK);
}
}