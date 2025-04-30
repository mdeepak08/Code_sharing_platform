package com.codeshare.platform.controller;

import java.time.LocalDateTime;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.codeshare.platform.dto.ApiResponse;
import com.codeshare.platform.dto.CommitDTO;
import com.codeshare.platform.dto.UserDto;
import com.codeshare.platform.model.Branch;
import com.codeshare.platform.model.Comment;
import com.codeshare.platform.model.Commit;
import com.codeshare.platform.model.Project;
import com.codeshare.platform.model.PullRequest;
import com.codeshare.platform.model.PullRequestStatus;
import com.codeshare.platform.model.User;
import com.codeshare.platform.repository.CommentRepository;
import com.codeshare.platform.repository.CommitRepository;
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
    private CommentRepository commentRepository;
    @Autowired
    private CommitRepository commitRepository;

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

    @GetMapping("/user")
    public ResponseEntity<ApiResponse<List<PullRequest>>> getUserPullRequests(
            @RequestParam(required = false, defaultValue = "created-by-me") String filter,
            @RequestParam(required = false, defaultValue = "open") String status,
            @RequestParam(required = false, defaultValue = "newest") String sort,
            @RequestParam(required = false, defaultValue = "") String search,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "10") int size,
            Authentication authentication) {
        
        try {
            String username = authentication.getName();
            Optional<User> userOpt = userService.getUserByUsername(username);
            
            if (userOpt.isEmpty()) {
                return new ResponseEntity<>(ApiResponse.error("User not found"), HttpStatus.NOT_FOUND);
            }
            
            User user = userOpt.get();
            List<PullRequest> pullRequests;
            
            // Get pull requests based on filter
            switch (filter) {
                case "created-by-me":
                case "me":
                    pullRequests = pullRequestService.getPullRequestsByAuthor(user);
                    break;
                case "assigned-to-me":
                case "assigned":
                    pullRequests = pullRequestService.getPullRequestsAssignedToUser(user);
                    break;
                case "mentioned-me":
                case "mentioned":
                    pullRequests = pullRequestService.getPullRequestsMentioningUser(user);
                    break;
                case "participated":
                    pullRequests = pullRequestService.getPullRequestsWithUserParticipation(user);
                    break;
                case "all":
                    // For all pull requests, we need to get all projects the user has access to
                    pullRequests = new ArrayList<>();
                    List<Project> userProjects = projectService.getProjectsByOwner(user);
                    
                    for (Project project : userProjects) {
                        pullRequests.addAll(pullRequestService.getPullRequestsByProject(project));
                    }
                    
                    // Remove duplicates (in case a user is both author and reviewer of a PR)
                    pullRequests = pullRequests.stream()
                        .distinct()
                        .collect(Collectors.toList());
                    break;
                default:
                    pullRequests = pullRequestService.getPullRequestsByAuthor(user);
                    break;
            }
            // Filter by project if specified
            if (projectId != null) {
                Optional<Project> projectOpt = projectService.getProjectById(projectId);
                if (projectOpt.isPresent()) {
                    Project project = projectOpt.get();
                    pullRequests = pullRequests.stream()
                        .filter(pr -> pr.getProject().getId().equals(project.getId()))
                        .collect(Collectors.toList());
                }
            }
            
            // Filter by status
            if (!status.equals("all")) {
                try {
                    PullRequestStatus prStatus = PullRequestStatus.valueOf(status.toUpperCase());
                    pullRequests = pullRequests.stream()
                        .filter(pr -> pr.getStatus() == prStatus)
                        .collect(Collectors.toList());
                } catch (IllegalArgumentException e) {
                    System.err.println("Invalid status filter: " + status);
                    // Continue with unfiltered results
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
                case "most-commented":
                    // This requires counting comments per PR
                    pullRequests.sort((pr1, pr2) -> {
                        int commentCount1 = commentRepository.findByPullRequest(pr1).size();
                        int commentCount2 = commentRepository.findByPullRequest(pr2).size();
                        return Integer.compare(commentCount2, commentCount1); // Descending
                    });
                    break;
                case "newest":
                default:
                    pullRequests.sort(Comparator.comparing(PullRequest::getCreatedAt).reversed());
                    break;
            }
            
            // Calculate total count for pagination
            int totalCount = pullRequests.size();
            
            // Apply pagination
            int startIndex = page * size;
            int endIndex = Math.min(startIndex + size, pullRequests.size());
            
            List<PullRequest> pagedResults;
            if (startIndex < pullRequests.size()) {
                pagedResults = pullRequests.subList(startIndex, endIndex);
            } else {
                pagedResults = new ArrayList<>(); // Empty list for out-of-bounds pages
            }
            
            // Enhance the response with additional metadata
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("pullRequests", pagedResults);
            responseData.put("totalCount", totalCount);
            responseData.put("hasMorePages", totalCount > (page * size + size));
            
            return new ResponseEntity<>(ApiResponse.success(pagedResults), HttpStatus.OK);
        } catch (Exception e) {
            System.err.println("Error fetching user pull requests: " + e.getMessage());
            return new ResponseEntity<>(ApiResponse.error("Error fetching pull requests: " + e.getMessage()),
                                HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }


    @GetMapping("/user/count")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> getUserPullRequestCounts(Authentication authentication) {
        try {
            String username = authentication.getName();
            Optional<User> userOpt = userService.getUserByUsername(username);
            
            if (userOpt.isEmpty()) {
                return new ResponseEntity<>(ApiResponse.error("User not found"), HttpStatus.NOT_FOUND);
            }
            
            User user = userOpt.get();
            
            // Get all pull requests authored by the user
            List<PullRequest> userPullRequests = pullRequestService.getPullRequestsByAuthor(user);
            
            // Count by status
            int openCount = (int) userPullRequests.stream()
                .filter(pr -> pr.getStatus() == PullRequestStatus.OPEN)
                .count();
                
            int closedCount = (int) userPullRequests.stream()
                .filter(pr -> pr.getStatus() == PullRequestStatus.CLOSED)
                .count();
                
            int mergedCount = (int) userPullRequests.stream()
                .filter(pr -> pr.getStatus() == PullRequestStatus.MERGED)
                .count();
            
            // Create result map
            Map<String, Integer> counts = new HashMap<>();
            counts.put("open", openCount);
            counts.put("closed", closedCount);
            counts.put("merged", mergedCount);
            counts.put("total", userPullRequests.size());
            
            return new ResponseEntity<>(ApiResponse.success(counts), HttpStatus.OK);
        } catch (Exception e) {
            System.err.println("Error getting user pull request counts: " + e.getMessage());
            return new ResponseEntity<>(ApiResponse.error("Error getting pull request counts: " + e.getMessage()),
                                HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/trending")
    public ResponseEntity<ApiResponse<List<PullRequest>>> getTrendingPullRequests() {
        try {
            List<PullRequest> allPRs = pullRequestService.getAllOpenPullRequests();
            
            // If we have comment information, we can use it for trending calculation
            // This requires CommentRepository to be available
            
            // Calculate a "trending score" for each PR
            // Formula: (# of comments in last 7 days) × 5 + (# of commits in last 7 days) × 3 + recency factor
            LocalDateTime oneWeekAgo = LocalDateTime.now().minusDays(7);
            
            // Get comments for all PRs
            Map<Long, Integer> recentCommentCounts = new HashMap<>();
            for (PullRequest pr : allPRs) {
                int commentCount = commentRepository.findByPullRequest(pr).stream()
                    .filter(comment -> comment.getCreatedAt().isAfter(oneWeekAgo))
                    .collect(Collectors.toList())
                    .size();
                recentCommentCounts.put(pr.getId(), commentCount);
            }
            
            // Calculate trending score for each PR
            List<Map.Entry<PullRequest, Double>> scoredPRs = allPRs.stream()
                .map(pr -> {
                    // Comments factor
                    int commentCount = recentCommentCounts.getOrDefault(pr.getId(), 0);
                    
                    // Recency factor - newer PRs get higher scores
                    // Scale from 0 to 10, where 0 = very old, 10 = just created
                    double recencyFactor = 0;
                    long daysOld = java.time.temporal.ChronoUnit.DAYS.between(pr.getCreatedAt(), LocalDateTime.now());
                    if (daysOld <= 30) { // Limit to PRs within last 30 days
                        recencyFactor = 10.0 * Math.max(0, (30 - daysOld)) / 30;
                    }
                    
                    // Calculate final score
                    double score = (commentCount * 5) + recencyFactor;
                    
                    return new AbstractMap.SimpleEntry<>(pr, score);
                })
                .sorted(Map.Entry.<PullRequest, Double>comparingByValue().reversed())
                .collect(Collectors.toList());
            
            // Take top 10 trending PRs
            List<PullRequest> trending = scoredPRs.stream()
                .limit(10)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
            
            return new ResponseEntity<>(ApiResponse.success(trending), HttpStatus.OK);
        } catch (Exception e) {
            System.err.println("Error fetching trending PRs: " + e.getMessage());
            return new ResponseEntity<>(ApiResponse.error("Error fetching trending pull requests: " + e.getMessage()),
                                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
    @GetMapping("/{id}/timeline")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getPullRequestTimeline(@PathVariable Long id) {
        Optional<PullRequest> pullRequestOpt = pullRequestService.getPullRequestById(id);
        
        if (pullRequestOpt.isEmpty()) {
            return new ResponseEntity<>(ApiResponse.error("Pull request not found"), HttpStatus.NOT_FOUND);
        }
        
        List<Map<String, Object>> timeline = new ArrayList<>();
        PullRequest pr = pullRequestOpt.get();
        
        // 1. Add PR creation event
        Map<String, Object> creationEvent = new HashMap<>();
        creationEvent.put("type", "CREATION");
        creationEvent.put("user", pr.getAuthor());
        creationEvent.put("createdAt", pr.getCreatedAt());
        creationEvent.put("content", "Created pull request");
        timeline.add(creationEvent);
        
        // 2. Add status change events
        if (pr.getStatus() == PullRequestStatus.MERGED && pr.getMergedAt() != null) {
            Map<String, Object> mergeEvent = new HashMap<>();
            mergeEvent.put("type", "STATUS_CHANGE");
            mergeEvent.put("user", pr.getAuthor()); // You might want to store who merged it
            mergeEvent.put("createdAt", pr.getMergedAt());
            mergeEvent.put("oldStatus", PullRequestStatus.OPEN);
            mergeEvent.put("newStatus", PullRequestStatus.MERGED);
            mergeEvent.put("content", "Merged pull request");
            timeline.add(mergeEvent);
        } else if (pr.getStatus() == PullRequestStatus.CLOSED && pr.getClosedAt() != null) {
            Map<String, Object> closeEvent = new HashMap<>();
            closeEvent.put("type", "STATUS_CHANGE");
            closeEvent.put("user", pr.getAuthor()); // You might want to store who closed it
            closeEvent.put("createdAt", pr.getClosedAt());
            closeEvent.put("oldStatus", PullRequestStatus.OPEN);
            closeEvent.put("newStatus", PullRequestStatus.CLOSED);
            closeEvent.put("content", "Closed pull request");
            timeline.add(closeEvent);
        }
        
        // 3. Add comments to timeline
        try {
            List<Comment> comments = commentRepository.findByPullRequest(pr);
            for (Comment comment : comments) {
                Map<String, Object> commentEvent = new HashMap<>();
                commentEvent.put("type", "COMMENT");
                commentEvent.put("user", comment.getUser());
                commentEvent.put("createdAt", comment.getCreatedAt());
                commentEvent.put("content", comment.getContent());
                commentEvent.put("id", comment.getId());
                
                // Include line-specific info if available
                if (comment.getFilePath() != null) {
                    commentEvent.put("filePath", comment.getFilePath());
                }
                if (comment.getLineNumber() != null) {
                    commentEvent.put("lineNumber", comment.getLineNumber());
                }
                
                timeline.add(commentEvent);
            }
        } catch (Exception e) {
            // Log the error but continue - we don't want to fail the whole timeline
            System.err.println("Error loading comments for timeline: " + e.getMessage());
        }
        
        // 4. Sort timeline by creation time (oldest first to show chronological order)
        timeline.sort(Comparator.comparing(event -> ((LocalDateTime)event.get("createdAt"))));
        
        return new ResponseEntity<>(ApiResponse.success(timeline), HttpStatus.OK);
    }

    @GetMapping("/{id}/commits")
    public ResponseEntity<ApiResponse<List<CommitDTO>>> getPullRequestCommits(@PathVariable Long id) {
        Optional<PullRequest> pullRequestOpt = pullRequestService.getPullRequestById(id);
        
        if (pullRequestOpt.isEmpty()) {
            return new ResponseEntity<>(ApiResponse.error("Pull request not found"), HttpStatus.NOT_FOUND);
        }
        
        PullRequest pullRequest = pullRequestOpt.get();
        List<CommitDTO> commits = new ArrayList<>();
        
        try {
            // Get the source and target branches
            Branch sourceBranch = pullRequest.getSourceBranch();
            Branch targetBranch = pullRequest.getTargetBranch();
            
            // Get commits from source branch that are not in target branch
            List<Commit> branchCommits = commitRepository.findByBranchOrderByCreatedAtDesc(sourceBranch);
            
            // Get the base commit (latest common commit between source and target)
            Optional<Commit> baseCommitOpt = findBaseCommit(sourceBranch, targetBranch);
            
            // If base commit exists, only include commits after it
            if (baseCommitOpt.isPresent()) {
                Commit baseCommit = baseCommitOpt.get();
                // Filter out commits that came before the base commit
                branchCommits = branchCommits.stream()
                    .filter(commit -> commit.getCreatedAt().isAfter(baseCommit.getCreatedAt()))
                    .collect(Collectors.toList());
            }
            
            // Convert Commit entities to CommitDTOs
            commits = branchCommits.stream()
                .map(commit -> {
                    CommitDTO dto = new CommitDTO();
                    dto.setId(commit.getId());
                    dto.setMessage(commit.getMessage());
                    dto.setCreatedAt(commit.getCreatedAt());
                    dto.setBranchName(sourceBranch.getName());
                    dto.setBranchId(sourceBranch.getId());
                    
                    // Add author info
                    if (commit.getAuthor() != null) {
                        UserDto authorDto = new UserDto();
                        authorDto.setId(commit.getAuthor().getId());
                        authorDto.setUsername(commit.getAuthor().getUsername());
                        authorDto.setEmail(commit.getAuthor().getEmail());
                        dto.setAuthor(authorDto);
                    }
                    
                    return dto;
                })
                .collect(Collectors.toList());
        } catch (Exception e) {
            System.err.println("Error fetching commits for PR #" + id + ": " + e.getMessage());
            return new ResponseEntity<>(ApiResponse.error("Error fetching commits: " + e.getMessage()), 
                                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
        
        return new ResponseEntity<>(ApiResponse.success(commits), HttpStatus.OK);
    }

    /**
     * Find the base commit (common ancestor) between two branches
     */
    private Optional<Commit> findBaseCommit(Branch sourceBranch, Branch targetBranch) {
        List<Commit> sourceCommits = commitRepository.findByBranchOrderByCreatedAtDesc(sourceBranch);
        List<Commit> targetCommits = commitRepository.findByBranchOrderByCreatedAtDesc(targetBranch);
        
        // Create a set of target commit IDs for faster lookup
        Set<Long> targetCommitIds = targetCommits.stream()
            .map(Commit::getId)
            .collect(Collectors.toSet());
        
        // Find the first commit in source branch that's also in target branch
        for (Commit commit : sourceCommits) {
            if (targetCommitIds.contains(commit.getId())) {
                return Optional.of(commit);
            }
        }
        
        return Optional.empty();
    }

    @PostMapping("/{id}/reviewers")
    public ResponseEntity<ApiResponse<PullRequest>> assignReviewers(
            @PathVariable Long id, 
            @RequestBody Map<String, List<String>> requestBody,
            Authentication authentication) {
        
        Optional<PullRequest> pullRequestOpt = pullRequestService.getPullRequestById(id);
        if (pullRequestOpt.isEmpty()) {
            return new ResponseEntity<>(ApiResponse.error("Pull request not found"), HttpStatus.NOT_FOUND);
        }
        
        PullRequest pullRequest = pullRequestOpt.get();
        List<String> reviewerUsernames = requestBody.get("reviewers");
        
        if (reviewerUsernames == null || reviewerUsernames.isEmpty()) {
            return new ResponseEntity<>(ApiResponse.error("No reviewers specified"), HttpStatus.BAD_REQUEST);
        }
        
        // Get current user (for permission check)
        String username = authentication.getName();
        Optional<User> currentUserOpt = userService.getUserByUsername(username);
        if (currentUserOpt.isEmpty()) {
            return new ResponseEntity<>(ApiResponse.error("Authentication error"), HttpStatus.UNAUTHORIZED);
        }
        
        // Check if current user is author or has admin rights (simplified)
        User currentUser = currentUserOpt.get();
        if (!pullRequest.getAuthor().getId().equals(currentUser.getId())) {
            // In production: also check if user has admin rights on the project
            return new ResponseEntity<>(ApiResponse.error("Permission denied"), HttpStatus.FORBIDDEN);
        }
        
        List<User> reviewersToAdd = new ArrayList<>();
        List<String> notFoundUsernames = new ArrayList<>();
        
        // Find all users by username
        for (String reviewerUsername : reviewerUsernames) {
            Optional<User> reviewerOpt = userService.getUserByUsername(reviewerUsername);
            if (reviewerOpt.isPresent()) {
                reviewersToAdd.add(reviewerOpt.get());
            } else {
                notFoundUsernames.add(reviewerUsername);
            }
        }
        
        // Add reviewers to pull request
        for (User reviewer : reviewersToAdd) {
            pullRequest.addReviewer(reviewer);
        }
        
        PullRequest updatedPR = pullRequestService.updatePullRequest(pullRequest);
        
        // Prepare response message
        String message = "Reviewers added successfully";
        if (!notFoundUsernames.isEmpty()) {
            message += ", but the following usernames were not found: " + String.join(", ", notFoundUsernames);
        }
        
        return new ResponseEntity<>(ApiResponse.success(message, updatedPR), HttpStatus.OK);
    }

    @DeleteMapping("/{id}/reviewers/{username}")
    public ResponseEntity<ApiResponse<PullRequest>> removeReviewer(
            @PathVariable Long id,
            @PathVariable String username,
            Authentication authentication) {
        
        Optional<PullRequest> pullRequestOpt = pullRequestService.getPullRequestById(id);
        if (pullRequestOpt.isEmpty()) {
            return new ResponseEntity<>(ApiResponse.error("Pull request not found"), HttpStatus.NOT_FOUND);
        }
        
        PullRequest pullRequest = pullRequestOpt.get();
        
        // Check if current user has permission
        String currentUsername = authentication.getName();
        Optional<User> currentUserOpt = userService.getUserByUsername(currentUsername);
        if (currentUserOpt.isEmpty()) {
            return new ResponseEntity<>(ApiResponse.error("Authentication error"), HttpStatus.UNAUTHORIZED);
        }
        
        User currentUser = currentUserOpt.get();
        // Allow: 1) PR author, 2) the reviewer themself, 3) project admin (simplified)
        boolean isAuthor = pullRequest.getAuthor().getId().equals(currentUser.getId());
        boolean isSelfRemoval = username.equals(currentUsername);
        
        if (!isAuthor && !isSelfRemoval) {
            return new ResponseEntity<>(ApiResponse.error("Permission denied"), HttpStatus.FORBIDDEN);
        }
        
        // Find the reviewer to remove
        Optional<User> reviewerOpt = userService.getUserByUsername(username);
        if (reviewerOpt.isEmpty()) {
            return new ResponseEntity<>(ApiResponse.error("User not found: " + username), HttpStatus.NOT_FOUND);
        }
        
        User reviewer = reviewerOpt.get();
        
        // Check if user is actually a reviewer
        if (!pullRequest.getReviewers().contains(reviewer)) {
            return new ResponseEntity<>(
                ApiResponse.error("User is not a reviewer of this pull request"), 
                HttpStatus.BAD_REQUEST
            );
        }
        
        // Remove reviewer
        pullRequest.removeReviewer(reviewer);
        PullRequest updatedPR = pullRequestService.updatePullRequest(pullRequest);
        
        return new ResponseEntity<>(
            ApiResponse.success("Reviewer removed successfully", updatedPR),
            HttpStatus.OK
        );
    }

    @GetMapping("/{id}/reviewers")
    public ResponseEntity<ApiResponse<List<UserDto>>> getPullRequestReviewers(@PathVariable Long id) {
        Optional<PullRequest> pullRequestOpt = pullRequestService.getPullRequestById(id);
        if (pullRequestOpt.isEmpty()) {
            return new ResponseEntity<>(ApiResponse.error("Pull request not found"), HttpStatus.NOT_FOUND);
        }
        
        PullRequest pullRequest = pullRequestOpt.get();
        List<UserDto> reviewers = pullRequest.getReviewers().stream()
            .map(user -> {
                UserDto dto = new UserDto();
                dto.setId(user.getId());
                dto.setUsername(user.getUsername());
                dto.setEmail(user.getEmail());
                dto.setFullName(user.getFullName());
                return dto;
            })
            .collect(Collectors.toList());
        
        return new ResponseEntity<>(ApiResponse.success(reviewers), HttpStatus.OK);
    }
    @PostMapping("/{id}/comments")
    public ResponseEntity<ApiResponse<Comment>> addComment(
            @PathVariable Long id,
            @RequestBody Map<String, String> commentRequest,
            Authentication authentication) {
        
        Optional<PullRequest> pullRequestOpt = pullRequestService.getPullRequestById(id);
        if (pullRequestOpt.isEmpty()) {
            return new ResponseEntity<>(ApiResponse.error("Pull request not found"), HttpStatus.NOT_FOUND);
        }
        
        String username = authentication.getName();
        Optional<User> userOpt = userService.getUserByUsername(username);
        if (userOpt.isEmpty()) {
            return new ResponseEntity<>(ApiResponse.error("User not found"), HttpStatus.UNAUTHORIZED);
        }
        
        String content = commentRequest.get("content");
        if (content == null || content.trim().isEmpty()) {
            return new ResponseEntity<>(ApiResponse.error("Comment content cannot be empty"), HttpStatus.BAD_REQUEST);
        }
        
        // Create and save the comment
        Comment comment = new Comment();
        comment.setPullRequest(pullRequestOpt.get());
        comment.setUser(userOpt.get());
        comment.setContent(content);
        
        // Optional: Handle file path and line number for line comments
        if (commentRequest.containsKey("filePath")) {
            comment.setFilePath(commentRequest.get("filePath"));
        }
        
        if (commentRequest.containsKey("lineNumber")) {
            try {
                comment.setLineNumber(Integer.parseInt(commentRequest.get("lineNumber")));
            } catch (NumberFormatException e) {
                // Ignore invalid line number
            }
        }
        
        comment.setCreatedAt(LocalDateTime.now());
        Comment savedComment = commentRepository.save(comment);
        
        return new ResponseEntity<>(ApiResponse.success("Comment added successfully", savedComment), HttpStatus.CREATED);
    }

}