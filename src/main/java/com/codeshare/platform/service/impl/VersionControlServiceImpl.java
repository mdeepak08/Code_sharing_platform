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
import com.codeshare.platform.repository.BranchRepository;
import com.codeshare.platform.repository.CommitRepository;
import com.codeshare.platform.repository.FileRepository;
import com.codeshare.platform.service.ConcurrencyService;
import com.codeshare.platform.service.VersionControlService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class VersionControlServiceImpl implements VersionControlService {

    private final BranchRepository branchRepository;
    private final CommitRepository commitRepository;
    private final FileRepository fileRepository;
    private final ObjectMapper objectMapper;
    private final ConcurrencyService concurrencyService;

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

            // Update file contents as well
            for (Map.Entry<String, String> entry : fileChanges.entrySet()) {
                String filePath = entry.getKey();
                String content = entry.getValue();
                
                Optional<File> fileOpt = fileRepository.findByProjectAndPath(branch.getProject(), filePath);
                if (fileOpt.isPresent()) {
                    File file = fileOpt.get();
                    file.setContent(content);
                    file.setUpdatedAt(LocalDateTime.now());
                    fileRepository.save(file);
                } else {
                    // Create new file if it doesn't exist
                    File newFile = new File();
                    newFile.setName(filePath.substring(filePath.lastIndexOf('/') + 1));
                    newFile.setPath(filePath);
                    newFile.setProject(branch.getProject());
                    newFile.setContent(content);
                    newFile.setCreatedAt(LocalDateTime.now());
                    fileRepository.save(newFile);
                }
            }

            return commitRepository.save(newCommit);
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

            return branchRepository.save(newBranch);
        });
    }

    @Override
    public void mergeBranches(Branch sourceBranch, Branch targetBranch, User merger) {
        concurrencyService.executeWithWriteLock(sourceBranch.getProject().getId(), () -> {
            // This would involve complex merge logic, handling conflicts, etc.
            // For now, we'll implement a simple version

            // Get latest commits from both branches
            List<Commit> sourceCommits = commitRepository.findByBranchOrderByCreatedAtDesc(sourceBranch);
            if (sourceCommits.isEmpty()) {
                throw new RuntimeException("Source branch has no commits to merge");
            }

            // Create a merge commit
            Commit mergeCommit = new Commit();
            mergeCommit.setBranch(targetBranch);
            mergeCommit.setAuthor(merger);
            mergeCommit.setMessage("Merge branch '" + sourceBranch.getName() + "' into " + targetBranch.getName());
            mergeCommit.setCreatedAt(LocalDateTime.now());

            // In a real implementation, you'd need to handle conflicts
            // and apply changes from the source branch

            commitRepository.save(mergeCommit);
            return null;
        });
    }

    @Override
    public String getFileContent(File file, Branch branch) {
        return concurrencyService.executeWithReadLock(file.getProject().getId(), () -> {
            List<Commit> commits = commitRepository.findByBranchOrderByCreatedAtDesc(branch);
            
            // Use the commits variable by checking if there are any commits
            if (commits.isEmpty()) {
                return file.getContent(); // Default to current content if no commits
            }
            
            // Here you could use the commits to get historical content
            // For example, looking for the file in the most recent commit:
            Commit latestCommit = commits.get(0);
            Map<String, String> changes = parseFileChanges(latestCommit.getFileChanges());
            if (changes.containsKey(file.getPath())) {
                return changes.get(file.getPath());
            }
            
            return file.getContent();
        });
    }

    @Override
    public Map<String, String> getProjectSnapshot(Project project, Branch branch) {
        return concurrencyService.executeWithReadLock(project.getId(), () -> {
            List<File> files = fileRepository.findByProject(project);
            Map<String, String> snapshot = new HashMap<>();

            for (File file : files) {
                snapshot.put(file.getPath(), getFileContent(file, branch));
            }
            return snapshot;
        });
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
        try {
            return objectMapper.readValue(fileChangesJson, Map.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse file changes", e);
        }
    }
}