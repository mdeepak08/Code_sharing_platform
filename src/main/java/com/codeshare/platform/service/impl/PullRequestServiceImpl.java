package com.codeshare.platform.service.impl;

import java.io.File;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeCommand;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.codeshare.platform.model.Branch;
import com.codeshare.platform.model.Comment;
import com.codeshare.platform.model.Commit;
import com.codeshare.platform.model.Project;
import com.codeshare.platform.model.PullRequest;
import com.codeshare.platform.model.PullRequestStatus;
import com.codeshare.platform.model.User;
import com.codeshare.platform.model.UserActivity;
import com.codeshare.platform.repository.CommentRepository;
import com.codeshare.platform.repository.CommitRepository;
import com.codeshare.platform.repository.PullRequestRepository;
import com.codeshare.platform.service.ActivityService;
import com.codeshare.platform.service.PullRequestService;
import com.codeshare.platform.service.VersionControlService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class PullRequestServiceImpl implements PullRequestService {

    private static final Logger logger = LoggerFactory.getLogger(PullRequestServiceImpl.class);
    private final PullRequestRepository pullRequestRepository;
    private final VersionControlService versionControlService;
    private final CommentRepository commentRepository;
    private final CommitRepository commitRepository;
    private final ObjectMapper objectMapper;
    @Autowired
    private ActivityService activityService;
    
    @Value("${git.repositories.base-path:/tmp/git-repositories}")
    private String gitRepositoriesBasePath;

    @Autowired
    public PullRequestServiceImpl(PullRequestRepository pullRequestRepository, 
                                 VersionControlService versionControlService,
                                 CommentRepository commentRepository,
                                 CommitRepository commitRepository,
                                 ObjectMapper objectMapper) {
        this.pullRequestRepository = pullRequestRepository;
        this.versionControlService = versionControlService;
        this.commentRepository = commentRepository;
        this.commitRepository = commitRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public PullRequest createPullRequest(PullRequest pullRequest) {
        pullRequest.setCreatedAt(LocalDateTime.now());
        pullRequest.setStatus(PullRequestStatus.OPEN);
        
        // Check if mergeable on creation
        pullRequest.setMergeable(checkMergeable(pullRequest));
        
        // Save the pull request
        PullRequest savedPR = pullRequestRepository.save(pullRequest);
        
        // Track activity with correct URL format for your existing pull-request.html
        activityService.trackActivity(
            pullRequest.getAuthor(),
            UserActivity.ActivityType.PULL_REQUEST_CREATED,
            "Created pull request: " + pullRequest.getTitle(),
            savedPR.getId(),
            "/pull-request.html?id=" + savedPR.getId() + "&projectId=" + pullRequest.getProject().getId(),
            "project: " + pullRequest.getProject().getName() + 
            ", source: " + pullRequest.getSourceBranch().getName() + 
            ", target: " + pullRequest.getTargetBranch().getName()
        );
        
        return savedPR;
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
    public List<PullRequest> getPullRequestsAssignedToUser(User user) {
        return pullRequestRepository.findByReviewer(user);
    }

    @Override
    public List<PullRequest> getPullRequestsMentioningUser(User user) {
        String mentionPattern = "@" + user.getUsername();
        
        // Get PRs mentioning user in description
        List<PullRequest> mentioningPRs = pullRequestRepository.findByDescriptionContaining(mentionPattern);
        Set<Long> existingPrIds = mentioningPRs.stream()
            .map(PullRequest::getId)
            .collect(Collectors.toSet());
        
        // Find comments mentioning the user
        List<Comment> mentioningComments = commentRepository.findAll().stream()
            .filter(comment -> comment.getContent().contains(mentionPattern))
            .collect(Collectors.toList());
        
        // Add PRs from comments to the result if not already included
        for (Comment comment : mentioningComments) {
            PullRequest pr = comment.getPullRequest();
            if (!existingPrIds.contains(pr.getId())) {
                mentioningPRs.add(pr);
                existingPrIds.add(pr.getId());
            }
        }
        
        return mentioningPRs;
    }

    @Override
    public List<PullRequest> getPullRequestsWithUserParticipation(User user) {
        // Get PRs authored by the user
        List<PullRequest> authoredPRs = getPullRequestsByAuthor(user);
        
        // Get PRs where the user has commented
        List<PullRequest> commentedPRs = pullRequestRepository.findByCommentAuthor(user);
        
        // Combine the lists, avoiding duplicates
        Set<PullRequest> participatedPRs = new HashSet<>(authoredPRs);
        participatedPRs.addAll(commentedPRs);
        
        // Convert back to list and sort by creation date (newest first)
        return participatedPRs.stream()
            .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
            .collect(Collectors.toList());
    }
    
    @Override
    public boolean checkMergeable(PullRequest pullRequest) {
        logger.debug("Checking mergeability for PR ID: {}", pullRequest.getId());
        
        try {
            // 1. Get or create the Git repository for this project
            String repoPath = getRepositoryPath(pullRequest.getProject());
            Repository repository = createOrGetRepository(repoPath, pullRequest.getProject());
            
            if (repository == null) {
                logger.error("Failed to get Git repository for project: {}", pullRequest.getProject().getId());
                return true; // Default to mergeable if can't check
            }
            
            try (Git git = new Git(repository)) {
                // 2. Get the branch names
                String sourceBranchName = pullRequest.getSourceBranch().getName();
                String targetBranchName = pullRequest.getTargetBranch().getName();
                
                // Ensure branches exist
                if (!branchExists(repository, sourceBranchName) || !branchExists(repository, targetBranchName)) {
                    logger.warn("One or both branches do not exist in Git repository");
                    syncBranchesToGit(git, pullRequest);
                }
                
                // 3. Try a test merge
                MergeCommand mergeCommand = git.merge();
                mergeCommand.setCommit(false); // Don't actually commit the merge
                
                // Set the head to merge into (target branch)
                mergeCommand.include(repository.resolve(targetBranchName));
                
                // Start from the source branch
                git.checkout().setName(sourceBranchName).call();
                
                // Perform the merge
                org.eclipse.jgit.api.MergeResult result = mergeCommand.call();
                
                // Check the result
                org.eclipse.jgit.api.MergeResult.MergeStatus status = result.getMergeStatus();
                
                // Clean up - reset any changes from the test merge
                git.reset().setMode(ResetCommand.ResetType.HARD).call();
                
                // Determine mergeability based on status
                boolean mergeable = (status == org.eclipse.jgit.api.MergeResult.MergeStatus.MERGED || 
                                   status == org.eclipse.jgit.api.MergeResult.MergeStatus.FAST_FORWARD ||
                                   status == org.eclipse.jgit.api.MergeResult.MergeStatus.ALREADY_UP_TO_DATE);
                
                logger.debug("JGit merge test result: {}, mergeable: {}", status, mergeable);
                return mergeable;
            }
        } catch (Exception e) {
            logger.error("Error checking PR mergeability using JGit: ", e);
            // In case of error, default to considering it mergeable
            return true;
        }
    }
    
    /**
     * Get the path where Git repositories are stored
     */
    private String getRepositoryPath(Project project) {
        return gitRepositoriesBasePath + File.separator + project.getId().toString() + ".git";
    }
    
    /**
     * Create or get an existing Git repository
     */
    private Repository createOrGetRepository(String repoPath, Project project) {
        try {
            File repositoryDir = new File(repoPath);
            
            if (!repositoryDir.exists()) {
                // Create the repository directory
                if (!repositoryDir.mkdirs()) {
                    logger.error("Failed to create repository directory: {}", repoPath);
                    return null;
                }
                
                // Initialize a new Git repository
                try (Git git = Git.init().setDirectory(repositoryDir).setBare(false).call()) {
                    // Add a dummy initial commit to establish the repository
                    File readmeFile = new File(repositoryDir, "README.md");
                    if (!readmeFile.exists()) {
                        readmeFile.createNewFile();
                        git.add().addFilepattern("README.md").call();
                        git.commit().setMessage("Initial commit").call();
                    }
                    
                    // Sync project branches and commits to the Git repository
                    syncProjectToGit(git, project);
                    
                    return git.getRepository();
                }
            } else {
                // Open the existing repository
                FileRepositoryBuilder builder = new FileRepositoryBuilder();
                Repository repository = builder.setGitDir(new File(repoPath, ".git"))
                        .readEnvironment()
                        .findGitDir()
                        .build();
                
                // Make sure it's up to date
                try (Git git = new Git(repository)) {
                    syncProjectToGit(git, project);
                }
                
                return repository;
            }
        } catch (Exception e) {
            logger.error("Error creating/getting Git repository: ", e);
            return null;
        }
    }
    
    /**
     * Sync the project's branches and commits to the Git repository
     */
    private void syncProjectToGit(Git git, Project project) {
        try {
            // Get all branches from the project
            List<Branch> branches = project.getBranches();
            
            for (Branch branch : branches) {
                String branchName = branch.getName();
                
                // Check if branch exists in Git repository
                boolean branchExists = branchExists(git.getRepository(), branchName);
                
                // If branch doesn't exist, create it
                if (!branchExists) {
                    // Create a new branch
                    git.checkout()
                       .setCreateBranch(true)
                       .setName(branchName)
                       .call();
                    
                    // Add branch's commits
                    List<Commit> commits = versionControlService.getCommitHistory(branch);
                    for (Commit commit : commits) {
                        addCommitToGit(git, commit);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error syncing project to Git: ", e);
        }
    }
    
    /**
     * Sync the branches from a pull request to Git
     */
    private void syncBranchesToGit(Git git, PullRequest pullRequest) {
        try {
            Branch sourceBranch = pullRequest.getSourceBranch();
            Branch targetBranch = pullRequest.getTargetBranch();
            
            // Check if source branch exists in Git repository
            boolean sourceBranchExists = branchExists(git.getRepository(), sourceBranch.getName());
            if (!sourceBranchExists) {
                // Create source branch
                git.checkout()
                   .setCreateBranch(true)
                   .setName(sourceBranch.getName())
                   .call();
                
                // Add source branch's commits
                List<Commit> sourceCommits = versionControlService.getCommitHistory(sourceBranch);
                for (Commit commit : sourceCommits) {
                    addCommitToGit(git, commit);
                }
            }
            
            // Check if target branch exists in Git repository
            boolean targetBranchExists = branchExists(git.getRepository(), targetBranch.getName());
            if (!targetBranchExists) {
                // Create target branch
                git.checkout()
                   .setCreateBranch(true)
                   .setName(targetBranch.getName())
                   .call();
                
                // Add target branch's commits
                List<Commit> targetCommits = versionControlService.getCommitHistory(targetBranch);
                for (Commit commit : targetCommits) {
                    addCommitToGit(git, commit);
                }
            }
        } catch (Exception e) {
            logger.error("Error syncing branches to Git: ", e);
        }
    }
    
    /**
     * Add a commit to the Git repository
     */
    private void addCommitToGit(Git git, Commit commit) {
        try {
            // Parse file changes from JSON
            Map<String, String> fileChanges = new HashMap<>();
            if (commit.getFileChanges() != null && !commit.getFileChanges().isEmpty()) {
                fileChanges = objectMapper.readValue(
                    commit.getFileChanges(), 
                    new TypeReference<HashMap<String, String>>() {}
                );
            }
            
            // Apply changes to working directory
            for (Map.Entry<String, String> entry : fileChanges.entrySet()) {
                String filePath = entry.getKey();
                String content = entry.getValue();
                
                File file = new File(git.getRepository().getWorkTree(), filePath);
                File parentDir = file.getParentFile();
                
                // Create parent directories if they don't exist
                if (!parentDir.exists()) {
                    parentDir.mkdirs();
                }
                
                // Write content to file
                java.nio.file.Files.write(file.toPath(), content.getBytes());
                
                // Add file to staging area
                git.add().addFilepattern(filePath).call();
            }
            
            // Create commit
            git.commit()
               .setMessage(commit.getMessage())
               .setAuthor(commit.getAuthor().getUsername(), commit.getAuthor().getEmail())
               .call();
        } catch (Exception e) {
            logger.error("Error adding commit to Git: ", e);
        }
    }
    
    /**
     * Check if a branch exists in the Git repository
     */
    private boolean branchExists(Repository repository, String branchName) {
        try {
            return repository.findRef(branchName) != null;
        } catch (Exception e) {
            logger.error("Error checking if branch exists: ", e);
            return false;
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
        
        // Track activity with correct URL format
        activityService.trackActivity(
            merger,
            UserActivity.ActivityType.PULL_REQUEST_MERGED,
            "Merged pull request: " + pullRequest.getTitle(),
            pullRequest.getId(),
            "/pull-request.html?id=" + pullRequest.getId() + "&projectId=" + pullRequest.getProject().getId(),
            mergeMessage
        );
    }
    
    @Override
    public void closePullRequest(PullRequest pullRequest, User closer) {
        pullRequest.setStatus(PullRequestStatus.CLOSED);
        pullRequest.setClosedAt(LocalDateTime.now());
        pullRequest.setUpdatedAt(LocalDateTime.now());
        
        pullRequestRepository.save(pullRequest);
        
        // Track activity with correct URL format
        activityService.trackActivity(
            closer,
            UserActivity.ActivityType.PULL_REQUEST_CLOSED,
            "Closed pull request: " + pullRequest.getTitle(),
            pullRequest.getId(),
            "/pull-request.html?id=" + pullRequest.getId() + "&projectId=" + pullRequest.getProject().getId(),
            null
        );
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