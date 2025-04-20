package com.codeshare.platform.service.impl;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.codeshare.platform.exception.ResourceNotFoundException;
import com.codeshare.platform.model.Branch;
import com.codeshare.platform.model.Commit;
import com.codeshare.platform.model.Project;
import com.codeshare.platform.model.User;
import com.codeshare.platform.service.CliService;
import com.codeshare.platform.service.ProjectService;
import com.codeshare.platform.service.VersionControlService;

/**
 * Implementation of the CliService interface
 */
@Service
public class CliServiceImpl implements CliService {

    @Autowired
    private ProjectService projectService;
    
    @Autowired
    private VersionControlService versionControlService;
    
    @Override
    public Map<String, String> cloneRepository(Long projectId, String branchName) {
        Project project = getProjectById(projectId);
        Branch branch = getBranchForProject(project, branchName);
        
        return versionControlService.getProjectSnapshot(project, branch);
    }
    
    @Override
    public Commit pushChanges(Long projectId, String branchName, Map<String, String> changes, String commitMessage, User user) {
        Project project = getProjectById(projectId);
        Branch branch = getBranchForProject(project, branchName);
        
        // Use the version control service to create a single commit with all changes
        return versionControlService.commitChanges(branch, user, commitMessage, changes);
    }
    
    @Override
    public Map<String, String> pullChanges(Long projectId, String branchName) {
        Project project = getProjectById(projectId);
        Branch branch = getBranchForProject(project, branchName);
        
        return versionControlService.getProjectSnapshot(project, branch);
    }
    
    @Override
    public List<Branch> getBranches(Long projectId) {
        Project project = getProjectById(projectId);
        return project.getBranches();
    }
    
    @Override
    public Branch createBranch(Long projectId, String branchName, User creator) {
        Project project = getProjectById(projectId);
        return versionControlService.createBranch(project, branchName, creator);
    }
    
    @Override
    public Project initRepository(String projectName, String description, User owner) {
        Project project = new Project();
        project.setName(projectName);
        project.setDescription(description);
        project.setOwner(owner);
        
        return projectService.createProject(project);
    }
    
    @Override
    public Branch getBranchForProject(Project project, String branchName) {
        List<Branch> branches = project.getBranches();
        
        if (branchName != null && !branchName.isEmpty()) {
            // Find the specified branch
            for (Branch branch : branches) {
                if (branch.getName().equals(branchName)) {
                    return branch;
                }
            }
            throw new ResourceNotFoundException("Branch not found: " + branchName);
        } else if (!branches.isEmpty()) {
            // Return the default branch (usually main/master)
            for (Branch branch : branches) {
                if (branch.getName().equals("main") || branch.getName().equals("master")) {
                    return branch;
                }
            }
            // If no main/master, return the first branch
            return branches.get(0);
        }
        
        throw new ResourceNotFoundException("No branches found for project: " + project.getName());
    }
    
    /**
     * Helper method to get a project by ID
     */
    private Project getProjectById(Long projectId) {
        Optional<Project> projectOpt = projectService.getProjectById(projectId);
        if (!projectOpt.isPresent()) {
            throw new ResourceNotFoundException("Project not found with ID: " + projectId);
        }
        return projectOpt.get();
    }
}