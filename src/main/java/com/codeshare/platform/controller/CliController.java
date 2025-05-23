package com.codeshare.platform.controller;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.codeshare.platform.dto.ApiResponse;
import com.codeshare.platform.dto.BranchDTO;
import com.codeshare.platform.model.Branch;
import com.codeshare.platform.model.Commit;
import com.codeshare.platform.model.Project;
import com.codeshare.platform.model.User;
import com.codeshare.platform.service.BranchService;
import com.codeshare.platform.service.CliService;
import com.codeshare.platform.service.FileService;
import com.codeshare.platform.service.ProjectService;
import com.codeshare.platform.service.UserService;


@RestController
@RequestMapping("/api/cli")
public class CliController {

    @Autowired
    private CliService cliService;
    
    @Autowired
    private UserService userService;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private BranchService branchService;

    @Autowired
    private FileService fileService;
    
    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        return userService.getUserByUsername(username).orElse(null);
    }
    
    @PostMapping("/clone")
    public ResponseEntity<ApiResponse<Map<String, String>>> cloneRepository(
            @RequestParam Long projectId,
            @RequestParam(required = false) String branchName) {
        try {
            Map<String, String> files = cliService.cloneRepository(projectId, branchName);
            return ResponseEntity.ok(new ApiResponse<>(true, "Repository cloned successfully", files));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(
                new ApiResponse<>(false, "Failed to clone repository: " + e.getMessage(), null));
        }
    }
    
    @PostMapping("/push")
    public ResponseEntity<ApiResponse<Commit>> pushChanges(
            @RequestParam Long projectId,
            @RequestParam(required = false) String branchName,
            @RequestBody Map<String, String> changes,
            @RequestParam String commitMessage) {
        try {
            // Get the user
            User currentUser = getCurrentUser();
            if (currentUser == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                    new ApiResponse<>(false, "User not authenticated", null));
            }
            
            // Validate project exists
            Optional<Project> projectOpt = projectService.getProjectById(projectId);
            if (projectOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    new ApiResponse<>(false, "Project not found: " + projectId, null));
            }
            Project project = projectOpt.get();
            
            // Use the existing service method to get the branch
            Branch branch;
            try {
                branch = cliService.getBranchForProject(project, branchName);
            } catch (Exception e) {
                return ResponseEntity.badRequest().body(
                    new ApiResponse<>(false, "Branch error: " + e.getMessage(), null));
            }
            
            // Create a single commit with all changes
            Commit commit = cliService.pushChanges(projectId, branch.getName(), changes, commitMessage, currentUser);
            return ResponseEntity.ok(new ApiResponse<>(true, "Changes pushed successfully", commit));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(
                new ApiResponse<>(false, "Failed to push changes: " + e.getMessage(), null));
        }
    }
    
    @GetMapping("/pull")
    public ResponseEntity<ApiResponse<Map<String, String>>> pullChanges(
            @RequestParam Long projectId,
            @RequestParam(required = false) String branchName) {
        try {
            Map<String, String> files = cliService.pullChanges(projectId, branchName);
            return ResponseEntity.ok(new ApiResponse<>(true, "Changes pulled successfully", files));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(
                new ApiResponse<>(false, "Failed to pull changes: " + e.getMessage(), null));
        }
    }
    
    @GetMapping("/branches")
    public ResponseEntity<ApiResponse<List<BranchDTO>>> getBranches(@RequestParam Long projectId) {
        Optional<Project> projectOpt = projectService.getProjectById(projectId);
        if (projectOpt.isPresent()) {
            List<Branch> branches = branchService.getBranchesByProject(projectOpt.get());
            List<BranchDTO> branchDTOs = branches.stream()
            .map(BranchDTO::new)
            .collect(Collectors.toList());
            
            return ResponseEntity.ok(ApiResponse.success(branchDTOs));
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("Project not found"));
        }
    }
    
    @PostMapping("/branch")
    public ResponseEntity<ApiResponse<Branch>> createBranch(
            @RequestParam Long projectId,
            @RequestParam String branchName) {
        try {
            // Get the user
            User currentUser = getCurrentUser();
            if (currentUser == null) {
                return ResponseEntity.badRequest().body(
                    new ApiResponse<>(false, "User not authenticated", null));
            }
            
            Branch branch = cliService.createBranch(projectId, branchName, currentUser);
            return ResponseEntity.ok(new ApiResponse<>(true, "Branch created successfully", branch));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(
                new ApiResponse<>(false, "Failed to create branch: " + e.getMessage(), null));
        }
    }
    
    @PostMapping("/init")
    public ResponseEntity<ApiResponse<Project>> initRepository(
            @RequestParam String projectName,
            @RequestParam(required = false) String description) {
        try {
            // Get the user
            User currentUser = getCurrentUser();
            if (currentUser == null) {
                return ResponseEntity.badRequest().body(
                    new ApiResponse<>(false, "User not authenticated", null));
            }
            
            Project createdProject = cliService.initRepository(projectName, description, currentUser);
            return ResponseEntity.ok(new ApiResponse<>(true, "Repository initialized successfully", createdProject));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(
                new ApiResponse<>(false, "Failed to initialize repository: " + e.getMessage(), null));
        }
    }
}