package com.codeshare.platform.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.codeshare.platform.dto.ApiResponse;
import com.codeshare.platform.dto.CommitDTO;
import com.codeshare.platform.model.Branch;
import com.codeshare.platform.model.Commit;
import com.codeshare.platform.model.File;
import com.codeshare.platform.model.Project;
import com.codeshare.platform.model.User;
import com.codeshare.platform.service.BranchService;
import com.codeshare.platform.service.FileService;
import com.codeshare.platform.service.ProjectService;
import com.codeshare.platform.service.UserService;
import com.codeshare.platform.service.VersionControlService;
import com.fasterxml.jackson.databind.ObjectMapper;


@RestController
@RequestMapping("/api/version-control")
public class VersionControlController {

    private final VersionControlService versionControlService;
    private final ProjectService projectService;
    private final BranchService branchService;
    private final UserService userService;
    private final FileService fileService;
    private final com.codeshare.platform.repository.CommitRepository commitRepository;

    @Autowired
    public VersionControlController(VersionControlService versionControlService,
                                   ProjectService projectService,
                                   BranchService branchService,
                                   UserService userService,
                                   FileService fileService,
                                   com.codeshare.platform.repository.CommitRepository commitRepository) {
        this.versionControlService = versionControlService;
        this.projectService = projectService;
        this.branchService = branchService;
        this.userService = userService;
        this.fileService = fileService;
        this.commitRepository = commitRepository;
    }

    @PostMapping("/commit")
    public ResponseEntity<ApiResponse<Commit>> commit(@RequestParam Long branchId,
                                                     @RequestParam Long authorId,
                                                     @RequestParam String message,
                                                     @RequestBody Map<String, String> fileChanges) {
        Optional<Branch> branchOpt = branchService.getBranchById(branchId);
        Optional<User> authorOpt = userService.getUserById(authorId);

        if (branchOpt.isPresent() && authorOpt.isPresent()) {
            Commit commit = versionControlService.commitChanges(branchOpt.get(), authorOpt.get(), message, fileChanges);
            return new ResponseEntity<>(ApiResponse.success("Changes committed successfully", commit), HttpStatus.CREATED);
        } else {
            return new ResponseEntity<>(ApiResponse.error("Branch or Author not found"), HttpStatus.NOT_FOUND);
        }
    }

    @PostMapping("/branch")
    public ResponseEntity<ApiResponse<Branch>> createBranch(@RequestParam Long projectId,
                                                          @RequestParam String branchName,
                                                          @RequestParam Long creatorId) {
        Optional<Project> projectOpt = projectService.getProjectById(projectId);
        Optional<User> creatorOpt = userService.getUserById(creatorId);

        if (projectOpt.isPresent() && creatorOpt.isPresent()) {
            Branch branch = versionControlService.createBranch(projectOpt.get(), branchName, creatorOpt.get());
            return new ResponseEntity<>(ApiResponse.success("Branch created successfully", branch), HttpStatus.CREATED);
        } else {
            return new ResponseEntity<>(ApiResponse.error("Project or Creator not found"), HttpStatus.NOT_FOUND);
        }
    }

    @GetMapping("/debug/branches")
    public ResponseEntity<ApiResponse<List<Branch>>> debugBranches(@RequestParam Long projectId) {
        Optional<Project> projectOpt = projectService.getProjectById(projectId);
        if (projectOpt.isPresent()) {
            List<Branch> branches = branchService.getBranchesByProject(projectOpt.get());
            return new ResponseEntity<>(ApiResponse.success(branches), HttpStatus.OK);
        } else {
            return new ResponseEntity<>(ApiResponse.error("Project not found"), HttpStatus.NOT_FOUND);
        }
    }

    @PostMapping("/merge")
    public ResponseEntity<ApiResponse<Void>> mergeBranches(@RequestParam Long sourceBranchId,
                                                         @RequestParam Long targetBranchId,
                                                         @RequestParam Long mergerId) {
        Optional<Branch> sourceBranchOpt = branchService.getBranchById(sourceBranchId);
        Optional<Branch> targetBranchOpt = branchService.getBranchById(targetBranchId);
        Optional<User> mergerOpt = userService.getUserById(mergerId);

        if (sourceBranchOpt.isPresent() && targetBranchOpt.isPresent() && mergerOpt.isPresent()) {
            versionControlService.mergeBranches(sourceBranchOpt.get(), targetBranchOpt.get(), mergerOpt.get());
            return new ResponseEntity<>(ApiResponse.success("Branches merged successfully", null), HttpStatus.OK);
        } else {
            return new ResponseEntity<>(ApiResponse.error("Source Branch, Target Branch, or Merger not found"), HttpStatus.NOT_FOUND);
        }
    }

    @GetMapping("/file-content")
    public ResponseEntity<ApiResponse<String>> getFileContent(@RequestParam Long fileId, @RequestParam Long branchId) {
        Optional<File> fileOpt = fileService.getFileById(fileId);
        Optional<Branch> branchOpt = branchService.getBranchById(branchId);

        if (fileOpt.isPresent() && branchOpt.isPresent()) {
            String content = versionControlService.getFileContent(fileOpt.get(), branchOpt.get());
            return new ResponseEntity<>(ApiResponse.success(content), HttpStatus.OK);
        } else {
            return new ResponseEntity<>(ApiResponse.error("File or Branch not found"), HttpStatus.NOT_FOUND);
        }
    }

    @GetMapping("/project-snapshot")
    public ResponseEntity<ApiResponse<Map<String, String>>> getProjectSnapshot(@RequestParam Long projectId, @RequestParam Long branchId) {
        Optional<Project> projectOpt = projectService.getProjectById(projectId);
        Optional<Branch> branchOpt = branchService.getBranchById(branchId);

        if (projectOpt.isPresent() && branchOpt.isPresent()) {
            Map<String, String> snapshot = versionControlService.getProjectSnapshot(projectOpt.get(), branchOpt.get());
            return new ResponseEntity<>(ApiResponse.success(snapshot), HttpStatus.OK);
        } else {
            return new ResponseEntity<>(ApiResponse.error("Project or Branch not found"), HttpStatus.NOT_FOUND);
        }
    }

    /**
     * Get all commits for a project (regardless of branch)
     */
    @GetMapping("/project-commits")
    public ResponseEntity<ApiResponse<List<CommitDTO>>> getProjectCommits(@RequestParam Long projectId) {
        try {
            Optional<Project> projectOpt = projectService.getProjectById(projectId);
            if (projectOpt.isEmpty()) {
                return new ResponseEntity<>(ApiResponse.error("Project not found"), HttpStatus.NOT_FOUND);
            }
            
            List<Commit> commits = new ArrayList<>();
            List<Branch> branches = branchService.getBranchesByProject(projectOpt.get());
            
            // Collect commits from all branches
            for (Branch branch : branches) {
                commits.addAll(commitRepository.findByBranch(branch));
            }
            
            // Sort commits by creation date (newest first)
            commits.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));
            
            // Convert to DTOs
            List<CommitDTO> commitDTOs = commits.stream()
                .map(CommitDTO::new)
                .collect(Collectors.toList());
            
            return new ResponseEntity<>(ApiResponse.success(commitDTOs), HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(ApiResponse.error("Error retrieving project commits: " + e.getMessage()), 
                                        HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Get commit history for a branch
     */
    @GetMapping("/commit-history")
    public ResponseEntity<ApiResponse<List<CommitDTO>>> getCommitHistory(@RequestParam Long branchId) {
        try {
            Optional<Branch> branchOpt = branchService.getBranchById(branchId);
            if (branchOpt.isEmpty()) {
                return new ResponseEntity<>(ApiResponse.error("Branch not found"), HttpStatus.NOT_FOUND);
            }
            
            List<Commit> commits = versionControlService.getCommitHistory(branchOpt.get());
            
            // Convert to DTOs
            List<CommitDTO> commitDTOs = commits.stream()
                .map(CommitDTO::new)
                .collect(Collectors.toList());
                
            return new ResponseEntity<>(ApiResponse.success(commitDTOs), HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(ApiResponse.error("Error retrieving commit history: " + e.getMessage()), 
                                        HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/file-history")
    public ResponseEntity<ApiResponse<List<String>>> getFileHistory(@RequestParam Long fileId, @RequestParam Long branchId) {
        Optional<File> fileOpt = fileService.getFileById(fileId);
        Optional<Branch> branchOpt = branchService.getBranchById(branchId);

        if (fileOpt.isPresent() && branchOpt.isPresent()) {
            List<String> history = versionControlService.getFileHistory(fileOpt.get(), branchOpt.get());
            return new ResponseEntity<>(ApiResponse.success(history), HttpStatus.OK);
        } else {
            return new ResponseEntity<>(ApiResponse.error("File or Branch not found"), HttpStatus.NOT_FOUND);
        }
    }

    @GetMapping("/file-diff")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getFileDiff(@RequestParam Long fileId,
                                                                    @RequestParam Long oldCommitId,
                                                                    @RequestParam Long newCommitId) {
        Optional<File> fileOpt = fileService.getFileById(fileId);
        Optional<Commit> oldCommitOpt = commitRepository.findById(oldCommitId);
        Optional<Commit> newCommitOpt = commitRepository.findById(newCommitId);

        if (fileOpt.isPresent() && oldCommitOpt.isPresent() && newCommitOpt.isPresent()) {
            Map<String, Object> diff = versionControlService.getFileDiff(
                fileOpt.get(), 
                oldCommitOpt.get(), 
                newCommitOpt.get()
            );
            return new ResponseEntity<>(ApiResponse.success(diff), HttpStatus.OK);
        } else {
            String error = "Not found: ";
            if (!fileOpt.isPresent()) error += "File ";
            if (!oldCommitOpt.isPresent()) error += "Old commit ";
            if (!newCommitOpt.isPresent()) error += "New commit ";
            
            return new ResponseEntity<>(ApiResponse.error(error.trim()), HttpStatus.NOT_FOUND);
        }
    }

    /**
     * Get detailed information about a specific commit
     */
    @GetMapping("/commit-details")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCommitDetails(@RequestParam Long commitId) {
        try {
            Optional<Commit> commitOpt = commitRepository.findById(commitId);
            if (commitOpt.isEmpty()) {
                return new ResponseEntity<>(ApiResponse.error("Commit not found"), HttpStatus.NOT_FOUND);
            }
            
            Commit commit = commitOpt.get();
            
            // Create response with all relevant details
            Map<String, Object> details = new HashMap<>();
            details.put("commit", new CommitDTO(commit));
            
            // Parse file changes from the JSON string
            if (commit.getFileChanges() != null && !commit.getFileChanges().isEmpty()) {
                try {
                    ObjectMapper objectMapper = new ObjectMapper();
                    @SuppressWarnings("unchecked")
                    Map<String, String> fileChanges = objectMapper.readValue(commit.getFileChanges(), Map.class);
                    details.put("fileChanges", fileChanges);
                } catch (Exception e) {
                    details.put("fileChanges", new HashMap<>());
                    details.put("parseError", "Could not parse file changes: " + e.getMessage());
                }
            } else {
                details.put("fileChanges", new HashMap<>());
            }
            
            // If this commit has a parent, include information about what changed
            if (commit.getParentCommit() != null) {
                details.put("hasParent", true);
                details.put("parentId", commit.getParentCommit().getId());
            } else {
                details.put("hasParent", false);
            }
            
            return new ResponseEntity<>(ApiResponse.success(details), HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(ApiResponse.error("Error retrieving commit details: " + e.getMessage()), 
                                      HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}