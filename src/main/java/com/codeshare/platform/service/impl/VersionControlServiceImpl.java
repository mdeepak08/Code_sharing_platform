package com.codeshare.platform.service.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.codeshare.platform.model.Branch;
import com.codeshare.platform.model.Commit;
import com.codeshare.platform.model.File;
import com.codeshare.platform.model.Project;
import com.codeshare.platform.model.User;
import com.codeshare.platform.model.UserActivity;
import com.codeshare.platform.repository.BranchRepository;
import com.codeshare.platform.repository.CommitRepository;
import com.codeshare.platform.repository.FileRepository;
import com.codeshare.platform.service.ActivityService;
import com.codeshare.platform.service.ConcurrencyService;
import com.codeshare.platform.service.VersionControlService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
@Service
public class VersionControlServiceImpl implements VersionControlService {

    private final BranchRepository branchRepository;
    private final CommitRepository commitRepository;
    private final FileRepository fileRepository;
    private final ObjectMapper objectMapper;
    private final ConcurrencyService concurrencyService;
    @Autowired
    private ActivityService activityService;

    @Autowired
    public VersionControlServiceImpl(
            BranchRepository branchRepository,
            CommitRepository commitRepository,
            FileRepository fileRepository,
            ObjectMapper objectMapper,
            ConcurrencyService concurrencyService) {
        this.branchRepository = branchRepository;
        this.commitRepository = commitRepository;
        this.fileRepository = fileRepository;
        this.objectMapper = objectMapper;
        this.concurrencyService = concurrencyService;
    }

    @Override
    public Commit commitChanges(Branch branch, User author, String message, Map<String, String> fileChanges) {
        return concurrencyService.executeWithWriteLock(branch.getProject().getId(), () -> {
            // Find the latest commit in the branch
            List<Commit> commits = commitRepository.findByBranchOrderByCreatedAtDesc(branch);
            Commit parentCommit = commits.isEmpty() ? null : commits.get(0);
    
            // Create a new commit
            Commit newCommit = new Commit();
            newCommit.setBranch(branch);
            newCommit.setAuthor(author);
            newCommit.setMessage(message);
            newCommit.setCreatedAt(LocalDateTime.now());
            newCommit.setParentCommit(parentCommit);
    
            // Serialize file changes to JSON
            try {
                newCommit.setFileChanges(objectMapper.writeValueAsString(fileChanges));
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to serialize file changes", e);
            }
    
            // IMPORTANT FIX: Don't update file entities directly here
            // Instead, just ensure the file entries exist in the database
            for (Map.Entry<String, String> entry : fileChanges.entrySet()) {
                String filePath = entry.getKey();
                
                // Check if file exists, create it if it doesn't
                Optional<File> fileOpt = fileRepository.findByProjectAndPath(branch.getProject(), filePath);
                if (!fileOpt.isPresent()) {
                    // Create a file record but don't set content
                    File newFile = new File();
                    newFile.setName(filePath.substring(filePath.lastIndexOf('/') + 1));
                    newFile.setPath(filePath);
                    newFile.setProject(branch.getProject());
                    newFile.setCreatedAt(LocalDateTime.now());
                    // Don't set content here - just store a reference to the file
                    fileRepository.save(newFile);
                }
            }
    
            // Save the commit
            Commit savedCommit = commitRepository.save(newCommit);
            
            // Track activity with the correct URL format compatible with your existing commit-details.html
            activityService.trackActivity(
                author,
                UserActivity.ActivityType.COMMIT_PUSHED,
                "Pushed commit: " + message,
                savedCommit.getId(),
                "/commit-details.html?id=" + savedCommit.getId() + "&projectId=" + branch.getProject().getId(),
                "branch: " + branch.getName() + ", files: " + fileChanges.size()
            );
            
            return savedCommit;
        });
    }
    @Override
    public Branch createBranch(Project project, String branchName, User creator) {
        return concurrencyService.executeWithWriteLock(project.getId(), () -> {
            // Check if branch already exists
            Optional<Branch> existingBranch = branchRepository.findByProjectAndName(project, branchName);
            if (existingBranch.isPresent()) {
                throw new RuntimeException("Branch with name " + branchName + " already exists");
            }
    
            // Create new branch
            Branch newBranch = new Branch();
            newBranch.setName(branchName);
            newBranch.setProject(project);
            newBranch.setCreatedAt(LocalDateTime.now());
    
            // If this is the first branch, set it as default
            if (branchRepository.findByProject(project).isEmpty()) {
                newBranch.setDefault(true);
            }
    
            // Save the branch
            Branch savedBranch = branchRepository.save(newBranch);
            
            // Track activity with correct URL format
            activityService.trackActivity(
                creator,
                UserActivity.ActivityType.BRANCH_CREATED,
                "Created branch: " + branchName,
                savedBranch.getId(),
                "/project.html?id=" + project.getId() + "&branch=" + branchName,
                null
            );
            
            return savedBranch;
        });
    }

    @Override
    public void mergeBranches(Branch sourceBranch, Branch targetBranch, User merger) {
        concurrencyService.executeWithWriteLock(sourceBranch.getProject().getId(), () -> {
            // Get latest commits from source branch
            List<Commit> sourceCommits = commitRepository.findByBranchOrderByCreatedAtDesc(sourceBranch);
            if (sourceCommits.isEmpty()) {
                throw new RuntimeException("Source branch has no commits to merge");
            }

            // Get the latest source branch state
            Map<String, String> sourceSnapshot = getSnapshotFromBranch(sourceBranch);
            
            // Create a merge commit on the target branch
            Commit mergeCommit = new Commit();
            mergeCommit.setBranch(targetBranch);
            mergeCommit.setAuthor(merger);
            mergeCommit.setMessage("Merge branch '" + sourceBranch.getName() + "' into " + targetBranch.getName());
            mergeCommit.setCreatedAt(LocalDateTime.now());
            
            // Get parent commit from target branch
            Optional<Commit> latestTargetCommit = commitRepository.findFirstByBranchOrderByCreatedAtDesc(targetBranch);
            if (latestTargetCommit.isPresent()) {
                mergeCommit.setParentCommit(latestTargetCommit.get());
            }

            try {
                // Store the source branch snapshot as the merge commit's file changes
                mergeCommit.setFileChanges(objectMapper.writeValueAsString(sourceSnapshot));
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to serialize file changes for merge", e);
            }

            commitRepository.save(mergeCommit);
            return null;
        });
                // Track activity
                activityService.trackActivity(
                    merger,
                    UserActivity.ActivityType.PULL_REQUEST_MERGED,
                    "Merged branch " + sourceBranch.getName() + " into " + targetBranch.getName(),
                    sourceBranch.getProject().getId(),
                    "/project.html?id=" + sourceBranch.getProject().getId(),
                    "source: " + sourceBranch.getName() + ", target: " + targetBranch.getName()
                );
    }

    @Override
    public String getFileContent(File file, Branch branch) {
        return concurrencyService.executeWithReadLock(file.getProject().getId(), () -> {
            // Get the latest commit for this branch
            Optional<Commit> latestCommit = commitRepository.findFirstByBranchOrderByCreatedAtDesc(branch);
            if (!latestCommit.isPresent()) {
                return file.getContent(); // Return base content if no commits
            }
            
            // Traverse commit history to find the latest version of this file
            return findLatestFileContent(file.getPath(), latestCommit.get());
        });
    }
    
    /**
     * Helper method to find the latest content of a file by traversing commit history
     */
    private String findLatestFileContent(String filePath, Commit commit) {
        Map<String, String> changes = parseFileChanges(commit.getFileChanges());
        
        // Check if this commit contains the file
        if (changes.containsKey(filePath)) {
            return changes.get(filePath);
        }
        
        // Check parent commit if this commit doesn't have the file
        if (commit.getParentCommit() != null) {
            return findLatestFileContent(filePath, commit.getParentCommit());
        }
        
        // File not found in commit history
        return null;
    }

    @Override
    public Map<String, String> getProjectSnapshot(Project project, Branch branch) {
        return concurrencyService.executeWithReadLock(project.getId(), () -> {
            return getSnapshotFromBranch(branch);
        });
    }
    
    /**
     * Helper method to get the complete snapshot of all files in a branch
     */
    private Map<String, String> getSnapshotFromBranch(Branch branch) {
        // Start with an empty snapshot
        Map<String, String> snapshot = new HashMap<>();
        
        // Find the latest commit for this branch
        Optional<Commit> latestCommitOpt = commitRepository.findFirstByBranchOrderByCreatedAtDesc(branch);
        if (!latestCommitOpt.isPresent()) {
            return snapshot; // Empty snapshot if no commits
        }
        
        // Traverse the commit history to build the snapshot
        buildSnapshotFromCommitHistory(snapshot, latestCommitOpt.get());
        
        return snapshot;
    }
    
    /**
     * Recursively build a snapshot by traversing commit history
     */
    private void buildSnapshotFromCommitHistory(Map<String, String> snapshot, Commit commit) {
        // First apply parent commit changes (older changes first)
        if (commit.getParentCommit() != null) {
            buildSnapshotFromCommitHistory(snapshot, commit.getParentCommit());
        }
        
        // Then apply this commit's changes (newer changes override older ones)
        Map<String, String> commitChanges = parseFileChanges(commit.getFileChanges());
        snapshot.putAll(commitChanges);
    }

    @Override
    public List<Commit> getCommitHistory(Branch branch) {
        return concurrencyService.executeWithReadLock(branch.getProject().getId(), () -> {
            return commitRepository.findByBranchOrderByCreatedAtDesc(branch);
        });
    }

    @Override
    public List<String> getFileHistory(File file, Branch branch) {
        return concurrencyService.executeWithReadLock(file.getProject().getId(), () -> {
            List<Commit> commits = commitRepository.findByBranchOrderByCreatedAtDesc(branch);
            List<String> fileHistory = new ArrayList<>();

            for (Commit commit : commits) {
                Map<String, String> changes = parseFileChanges(commit.getFileChanges());
                if (changes.containsKey(file.getPath())) {
                    fileHistory.add(commit.getMessage());
                }
            }
            return fileHistory;
        });
    }

    @Override
    public Map<String, Object> getFileDiff(File file, Commit oldCommit, Commit newCommit) {
        return concurrencyService.executeWithReadLock(file.getProject().getId(), () -> {
            Map<String, String> oldChanges = parseFileChanges(oldCommit.getFileChanges());
            Map<String, String> newChanges = parseFileChanges(newCommit.getFileChanges());

            Map<String, Object> diff = new HashMap<>();
            diff.put("old", oldChanges.get(file.getPath()));
            diff.put("new", newChanges.get(file.getPath()));

            return diff;
        });
    }

    private Map<String, String> parseFileChanges(String fileChangesJson) {
        if (fileChangesJson == null || fileChangesJson.isEmpty()) {
            return new HashMap<>();
        }
        
        try {
            return objectMapper.readValue(fileChangesJson, new TypeReference<HashMap<String, String>>() {});
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse file changes", e);
        }
    }
}