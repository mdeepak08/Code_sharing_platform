package com.codeshare.platform.service;

import java.util.List;
import java.util.Map;

import com.codeshare.platform.model.Branch;
import com.codeshare.platform.model.Commit;
import com.codeshare.platform.model.Project;
import com.codeshare.platform.model.User;

/**
 * Service interface for CLI operations
 */
public interface CliService {
    
    /**
     * Clone a repository (get project files)
     * 
     * @param projectId The project ID
     * @param branchName The branch name (optional)
     * @return Map of file paths to file contents
     */
    Map<String, String> cloneRepository(Long projectId, String branchName);
    
    /**
     * Push changes to a repository
     * 
     * @param projectId The project ID
     * @param branchName The branch name (optional)
     * @param changes Map of file paths to file contents
     * @param commitMessage The commit message
     * @param user The user making the changes
     * @return The created commit
     */
    Commit pushChanges(Long projectId, String branchName, Map<String, String> changes, String commitMessage, User user);
    
    /**
     * Pull changes from a repository (get latest project files)
     * 
     * @param projectId The project ID
     * @param branchName The branch name (optional)
     * @return Map of file paths to file contents
     */
    Map<String, String> pullChanges(Long projectId, String branchName);
    
    /**
     * Get all branches for a project
     * 
     * @param projectId The project ID
     * @return List of branches
     */
    List<Branch> getBranches(Long projectId);
    
    /**
     * Create a new branch
     * 
     * @param projectId The project ID
     * @param branchName The branch name
     * @param creator The user creating the branch
     * @return The created branch
     */
    Branch createBranch(Long projectId, String branchName, User creator);
    
    /**
     * Initialize a new repository (create a new project)
     * 
     * @param projectName The project name
     * @param description The project description (optional)
     * @param owner The project owner
     * @return The created project
     */
    Project initRepository(String projectName, String description, User owner);
    
    /**
     * Get a branch for a project by name or get the default branch
     * 
     * @param project The project
     * @param branchName The branch name (optional)
     * @return The found branch or default branch
     */
    Branch getBranchForProject(Project project, String branchName);
    
}