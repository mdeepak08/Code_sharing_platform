package com.codeshare.platform.controller;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.codeshare.platform.dto.ApiResponse;
import com.codeshare.platform.model.Branch;
import com.codeshare.platform.model.Project;
import com.codeshare.platform.service.BranchService;
import com.codeshare.platform.service.ProjectService;


@RestController
@RequestMapping("/api/branches")
public class BranchController {

    private final BranchService branchService;
    private final ProjectService projectService;
    
    @Autowired
    public BranchController(BranchService branchService, ProjectService projectService) {
        this.branchService = branchService;
        this.projectService = projectService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Branch>> createBranch(@RequestBody Branch branch, @RequestParam Long projectId) {
        Optional<Project> projectOpt = projectService.getProjectById(projectId);
        if (projectOpt.isPresent()) {
            branch.setProject(projectOpt.get());
            Branch createdBranch = branchService.createBranch(branch);
            return new ResponseEntity<>(ApiResponse.success("Branch created successfully", createdBranch), HttpStatus.CREATED);
        } else {
            return new ResponseEntity<>(ApiResponse.error("Project not found"), HttpStatus.NOT_FOUND);
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Branch>> getBranchById(@PathVariable Long id) {
        Optional<Branch> branchOpt = branchService.getBranchById(id);
        return branchOpt.map(branch -> new ResponseEntity<>(ApiResponse.success(branch), HttpStatus.OK))
                .orElse(new ResponseEntity<>(ApiResponse.error("Branch not found"), HttpStatus.NOT_FOUND));
    }

    @GetMapping("/project/{projectId}")
    public ResponseEntity<ApiResponse<List<Branch>>> getBranchesByProject(@PathVariable Long projectId) {
        Optional<Project> projectOpt = projectService.getProjectById(projectId);
        if (projectOpt.isPresent()) {
            List<Branch> branches = branchService.getBranchesByProject(projectOpt.get());
            return new ResponseEntity<>(ApiResponse.success(branches), HttpStatus.OK);
        } else {
            return new ResponseEntity<>(ApiResponse.error("Project not found"), HttpStatus.NOT_FOUND);
        }
    }

    @GetMapping("/project/{projectId}/default")
    public ResponseEntity<ApiResponse<Branch>> getDefaultBranch(@PathVariable Long projectId) {
        Optional<Project> projectOpt = projectService.getProjectById(projectId);
        if (projectOpt.isPresent()) {
            Optional<Branch> defaultBranchOpt = branchService.getDefaultBranch(projectOpt.get());
            return defaultBranchOpt.map(branch -> new ResponseEntity<>(ApiResponse.success(branch), HttpStatus.OK))
                    .orElse(new ResponseEntity<>(ApiResponse.error("Default branch not found"), HttpStatus.NOT_FOUND));
        } else {
            return new ResponseEntity<>(ApiResponse.error("Project not found"), HttpStatus.NOT_FOUND);
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Branch>> updateBranch(@PathVariable Long id, @RequestBody Branch branch) {
        Optional<Branch> existingBranchOpt = branchService.getBranchById(id);
        if (existingBranchOpt.isPresent()) {
            branch.setId(id);
            branch.setProject(existingBranchOpt.get().getProject()); // Preserve the original project
            Branch updatedBranch = branchService.updateBranch(branch);
            return new ResponseEntity<>(ApiResponse.success("Branch updated successfully", updatedBranch), HttpStatus.OK);
        } else {
            return new ResponseEntity<>(ApiResponse.error("Branch not found"), HttpStatus.NOT_FOUND);
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteBranch(@PathVariable Long id) {
        Optional<Branch> existingBranchOpt = branchService.getBranchById(id);
        if (existingBranchOpt.isPresent()) {
            // Check if this is the default branch
            Optional<Branch> defaultBranchOpt = branchService.getDefaultBranch(existingBranchOpt.get().getProject());
            if (defaultBranchOpt.isPresent() && defaultBranchOpt.get().getId().equals(id)) {
                return new ResponseEntity<>(ApiResponse.error("Cannot delete the default branch"), HttpStatus.BAD_REQUEST);
            }
            branchService.deleteBranch(id);
            return new ResponseEntity<>(ApiResponse.success("Branch deleted successfully", null), HttpStatus.OK);
        } else {
            return new ResponseEntity<>(ApiResponse.error("Branch not found"), HttpStatus.NOT_FOUND);
        }
    }
    
}