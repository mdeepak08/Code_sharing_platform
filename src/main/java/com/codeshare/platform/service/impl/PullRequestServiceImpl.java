package com.codeshare.platform.service.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.codeshare.platform.model.Commit;
import com.codeshare.platform.model.Project;
import com.codeshare.platform.model.PullRequest;
import com.codeshare.platform.model.PullRequestStatus;
import com.codeshare.platform.model.User;
import com.codeshare.platform.repository.PullRequestRepository;
import com.codeshare.platform.service.PullRequestService;
import com.codeshare.platform.service.VersionControlService;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class PullRequestServiceImpl implements PullRequestService {

    private final PullRequestRepository pullRequestRepository;
    private final VersionControlService versionControlService;

    @Autowired
    public PullRequestServiceImpl(PullRequestRepository pullRequestRepository, 
                                 VersionControlService versionControlService) {
        this.pullRequestRepository = pullRequestRepository;
        this.versionControlService = versionControlService;
    }

    @Override
    public PullRequest createPullRequest(PullRequest pullRequest) {
        pullRequest.setCreatedAt(LocalDateTime.now());
        pullRequest.setStatus(PullRequestStatus.OPEN);
        
        // Check if mergeable on creation
        pullRequest.setMergeable(checkMergeable(pullRequest));
        
        return pullRequestRepository.save(pullRequest);
    }

    @Override
    public Optional<PullRequest> getPullRequestById(Long id) {
        return pullRequestRepository.findById(id);
    }
    
    @Override
    public List<PullRequest> getPullRequestsByProject(Project project) {
        return pullRequestRepository.findByProject(project);
    }
    
    @Override
    public List<PullRequest> getPullRequestsByProjectAndStatus(Project project, PullRequestStatus status) {
        return pullRequestRepository.findByProjectAndStatus(project, status);
    }
    
    @Override
    public List<PullRequest> getPullRequestsByAuthor(User author) {
        return pullRequestRepository.findByAuthor(author);
    }
    
    @Override
    public PullRequest updatePullRequest(PullRequest pullRequest) {
        pullRequest.setUpdatedAt(LocalDateTime.now());
        return pullRequestRepository.save(pullRequest);
    }
    
    @Override
    public void deletePullRequest(Long id) {
        pullRequestRepository.deleteById(id);
    }

    @Override
    public List<PullRequest> getAllOpenPullRequests() {
        return pullRequestRepository.findByStatus(PullRequestStatus.OPEN);
    }
    
@Override
public boolean checkMergeable(PullRequest pullRequest) {
    try {
        // Get latest commits from source and target branches
        List<Commit> sourceCommits = versionControlService.getCommitHistory(pullRequest.getSourceBranch());
        List<Commit> targetCommits = versionControlService.getCommitHistory(pullRequest.getTargetBranch());
        
        if (sourceCommits.isEmpty() || targetCommits.isEmpty()) {
            return true; // No commits in one branch, trivially mergeable
        }
        
        // Get snapshots of files from both branches
        Map<String, String> sourceFiles = versionControlService.getProjectSnapshot(
            pullRequest.getProject(), pullRequest.getSourceBranch());
        Map<String, String> targetFiles = versionControlService.getProjectSnapshot(
            pullRequest.getProject(), pullRequest.getTargetBranch());
        
        // Find the common ancestor commit (if any)
        Commit commonAncestor = findCommonAncestor(sourceCommits, targetCommits);
        
        // If no common ancestor, check for direct conflicts in the files
        if (commonAncestor == null) {
            return checkDirectFileConflicts(sourceFiles, targetFiles);
        }
        
        // Get snapshot of files at common ancestor
        Map<String, String> ancestorFiles = getFilesAtCommit(commonAncestor);
        
        // Check for conflicts by comparing changes from ancestor to each branch
        return checkThreeWayMergeConflicts(ancestorFiles, sourceFiles, targetFiles);
    } catch (Exception e) {
        // Log the error
        System.err.println("Error checking if PR is mergeable: " + e.getMessage());
        e.printStackTrace();
        return false;
    }
}
/**
 * Find the most recent common ancestor commit between two branches
 */
private Commit findCommonAncestor(List<Commit> sourceCommits, List<Commit> targetCommits) {
    // Build a set of commit IDs from target branch for faster lookup
    Set<Long> targetCommitIds = targetCommits.stream()
        .map(Commit::getId)
        .collect(Collectors.toSet());
    
    // Find the first commit in source branch that's also in target branch
    for (Commit sourceCommit : sourceCommits) {
        if (targetCommitIds.contains(sourceCommit.getId())) {
            return sourceCommit;
        }
        
        // Also check parent commits if they exist
        Commit current = sourceCommit;
        while (current.getParentCommit() != null) {
            if (targetCommitIds.contains(current.getParentCommit().getId())) {
                return current.getParentCommit();
            }
            current = current.getParentCommit();
        }
    }
    
    return null; // No common ancestor found
}
/**
 * Check for direct conflicts between files when there's no common ancestor
 */
private boolean checkDirectFileConflicts(Map<String, String> sourceFiles, Map<String, String> targetFiles) {
    // Find files that exist in both branches
    Set<String> filesInBoth = new HashSet<>(sourceFiles.keySet());
    filesInBoth.retainAll(targetFiles.keySet());
    
    // Check each file for conflicts
    for (String filePath : filesInBoth) {
        String sourceContent = sourceFiles.get(filePath);
        String targetContent = targetFiles.get(filePath);
        
        // If the same file has different content in both branches, it's a potential conflict
        if (!sourceContent.equals(targetContent)) {
            // Here we could implement more sophisticated line-by-line conflict detection
            
            // For simplicity, let's consider a conflict if the same lines were changed
            List<String> sourceLines = Arrays.asList(sourceContent.split("\n"));
            List<String> targetLines = Arrays.asList(targetContent.split("\n"));
            
            // If the files have different line counts, they're conflicting
            if (sourceLines.size() != targetLines.size()) {
                return false; // Not mergeable
            }
            
            // Check if there are conflicting changes line by line
            for (int i = 0; i < sourceLines.size(); i++) {
                if (!sourceLines.get(i).equals(targetLines.get(i))) {
                    // Different content found, potential conflict
                    // In a real implementation, you might use a diff algorithm here
                    return false; // Not mergeable
                }
            }
        }
    }
    
    return true; // No conflicts found
}

/**
 * Check for conflicts using three-way merge when there's a common ancestor
 */
private boolean checkThreeWayMergeConflicts(
        Map<String, String> ancestorFiles, 
        Map<String, String> sourceFiles, 
        Map<String, String> targetFiles) {
    
    // Collect all file paths from all three snapshots
    Set<String> allFilePaths = new HashSet<>();
    allFilePaths.addAll(ancestorFiles.keySet());
    allFilePaths.addAll(sourceFiles.keySet());
    allFilePaths.addAll(targetFiles.keySet());
    
    for (String filePath : allFilePaths) {
        // Get file contents, empty string if the file doesn't exist in a particular snapshot
        String ancestorContent = ancestorFiles.getOrDefault(filePath, "");
        String sourceContent = sourceFiles.getOrDefault(filePath, "");
        String targetContent = targetFiles.getOrDefault(filePath, "");
        
        // If source and target are identical, no conflict
        if (sourceContent.equals(targetContent)) {
            continue;
        }
        
        // If the file wasn't modified in one branch, no conflict
        if (sourceContent.equals(ancestorContent) || targetContent.equals(ancestorContent)) {
            continue;
        }
        
        // Both branches modified the same file, check for line-level conflicts
        List<String> ancestorLines = ancestorContent.isEmpty() ? 
            new ArrayList<>() : Arrays.asList(ancestorContent.split("\n"));
        List<String> sourceLines = sourceContent.isEmpty() ? 
            new ArrayList<>() : Arrays.asList(sourceContent.split("\n"));
        List<String> targetLines = targetContent.isEmpty() ? 
            new ArrayList<>() : Arrays.asList(targetContent.split("\n"));
        
        // Find line-level conflicts using a simple algorithm
        if (hasLineConflicts(ancestorLines, sourceLines, targetLines)) {
            return false; // Not mergeable
        }
    }
    
    return true; // No conflicts detected
}

/**
 * Check if there are line-level conflicts between branches
 */
private boolean hasLineConflicts(
        List<String> ancestorLines,
        List<String> sourceLines, 
        List<String> targetLines) {
    
    // This is a simplified line-by-line conflict detection algorithm
    // In a real-world scenario, you'd use a more sophisticated diff algorithm
    
    int i = 0, j = 0, k = 0; // Indices for ancestor, source, and target
    
    while (i < ancestorLines.size() && j < sourceLines.size() && k < targetLines.size()) {
        if (!sourceLines.get(j).equals(ancestorLines.get(i)) && 
            !targetLines.get(k).equals(ancestorLines.get(i)) &&
            !sourceLines.get(j).equals(targetLines.get(k))) {
            // Both branches changed the same line differently
            return true; // Conflict detected
        }
        
        i++; j++; k++;
    }
    
    // Handle differing lengths (if one branch added/removed lines)
    // This is simplified and could be improved for real-world usage
    if (sourceLines.size() != ancestorLines.size() && 
        targetLines.size() != ancestorLines.size() &&
        sourceLines.size() != targetLines.size()) {
        // Both branches changed the file structure differently
        return true; // Conflict detected
    }
    
    return false; // No conflicts detected
}

/**
 * Get the files at a specific commit
 */
private Map<String, String> getFilesAtCommit(Commit commit) {
    try {
        // Parse the file changes stored in the commit
        Map<String, String> files = new HashMap<>();
        if (commit.getFileChanges() != null && !commit.getFileChanges().isEmpty()) {
            ObjectMapper objectMapper = new ObjectMapper();
            files = objectMapper.readValue(commit.getFileChanges(), Map.class);
        }
        return files;
    } catch (Exception e) {
        System.err.println("Error getting files at commit: " + e.getMessage());
        return new HashMap<>();
    }
}

    
    @Override
    public void mergePullRequest(PullRequest pullRequest, User merger, String mergeMessage) {
        // Check if PR is mergeable
        if (!checkMergeable(pullRequest)) {
            throw new RuntimeException("Cannot merge: Pull request has conflicts");
        }
        
        // Use version control service to merge branches
        versionControlService.mergeBranches(
            pullRequest.getSourceBranch(), 
            pullRequest.getTargetBranch(), 
            merger
        );
        
        // Update PR status
        pullRequest.setStatus(PullRequestStatus.MERGED);
        pullRequest.setMergedAt(LocalDateTime.now());
        pullRequest.setUpdatedAt(LocalDateTime.now());
        
        pullRequestRepository.save(pullRequest);
    }
    
    @Override
    public void closePullRequest(PullRequest pullRequest, User closer) {
        pullRequest.setStatus(PullRequestStatus.CLOSED);
        pullRequest.setClosedAt(LocalDateTime.now());
        pullRequest.setUpdatedAt(LocalDateTime.now());
        
        pullRequestRepository.save(pullRequest);
    }
    
    @Override
    public Map<String, Object> getDiffStats(PullRequest pullRequest) {
        // Calculate diff statistics between source and target branches
        // For example: lines added, lines removed, files changed
        
        Map<String, Object> stats = new HashMap<>();
        stats.put("filesChanged", getChangedFiles(pullRequest).size());
        stats.put("additions", 0); // In a real implementation, calculate actual additions
        stats.put("deletions", 0); // In a real implementation, calculate actual deletions
        
        return stats;
    }
    
    @Override
    public List<String> getChangedFiles(PullRequest pullRequest) {
        // Get list of files that differ between source and target branches
        // This is a simplified implementation
        
        Map<String, String> sourceFiles = versionControlService.getProjectSnapshot(
            pullRequest.getProject(), pullRequest.getSourceBranch());
        Map<String, String> targetFiles = versionControlService.getProjectSnapshot(
            pullRequest.getProject(), pullRequest.getTargetBranch());
        
        Set<String> changedFiles = new HashSet<>();
        
        // Find files that exist in source but not in target or have different content
        for (Map.Entry<String, String> entry : sourceFiles.entrySet()) {
            String filePath = entry.getKey();
            String sourceContent = entry.getValue();
            
            if (!targetFiles.containsKey(filePath) || 
                !targetFiles.get(filePath).equals(sourceContent)) {
                changedFiles.add(filePath);
            }
        }
        
        // Find files that exist in target but not in source
        for (String filePath : targetFiles.keySet()) {
            if (!sourceFiles.containsKey(filePath)) {
                changedFiles.add(filePath);
            }
        }
        
        return new ArrayList<>(changedFiles);
    }
}