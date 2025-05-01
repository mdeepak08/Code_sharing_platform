package com.codeshare.platform.service.impl;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.codeshare.platform.model.Branch;
import com.codeshare.platform.model.Project;
import com.codeshare.platform.model.ProjectRole;
import com.codeshare.platform.model.User;
import com.codeshare.platform.model.UserActivity;
import com.codeshare.platform.model.UserProject;
import com.codeshare.platform.repository.BranchRepository;
import com.codeshare.platform.repository.ProjectRepository;
import com.codeshare.platform.repository.UserProjectRepository;
import com.codeshare.platform.service.ActivityService;
import com.codeshare.platform.service.BranchService;
import com.codeshare.platform.service.ProjectService;
import com.codeshare.platform.service.VersionControlService;

@Service
public class ProjectServiceImpl implements ProjectService {
    
    private final ProjectRepository projectRepository;
    private final UserProjectRepository userProjectRepository;
    
    @Autowired
    private ActivityService activityService;
    @Autowired
    private BranchRepository branchRepository;
    
    @Autowired
    private VersionControlService versionControlService;
    
    @Autowired
    private BranchService branchService;

    @Autowired
    public ProjectServiceImpl(ProjectRepository projectRepository, UserProjectRepository userProjectRepository) {
        this.projectRepository = projectRepository;
        this.userProjectRepository = userProjectRepository;
    }

    @Override
    public Project createProject(Project project) {
        Project savedProject = projectRepository.save(project);
        
        // Track activity
        activityService.trackActivity(
            project.getOwner(),
            UserActivity.ActivityType.PROJECT_CREATED,
            "Created project " + project.getName(),
            savedProject.getId(),
            "/project.html?id=" + savedProject.getId(),
            null
        );
        
        return savedProject;
    }

    @Override
    public Optional<Project> getProjectById(Long id) {
        return projectRepository.findById(id);
    }

    @Override
    public List<Project> getAllProjects() {
        return projectRepository.findAll();
    }

    @Override
    public List<Project> getProjectsByOwner(User owner) {
        return projectRepository.findByOwner(owner);
    }

    @Override
    public List<Project> getProjectsByCollaborator(User collaborator) {
        List<UserProject> userProjects = userProjectRepository.findByUser(collaborator);
        return userProjects.stream()
                .map(UserProject::getProject)
                .collect(Collectors.toList());
    }

    @Override
    public List<Project> getPublicProjects() {
        return projectRepository.findByIsPublic(true);
    }

    @Override
    public Project updateProject(Project project) {
        Project updatedProject = projectRepository.save(project);
        
        // Track activity
        activityService.trackActivity(
            project.getOwner(),
            UserActivity.ActivityType.PROJECT_UPDATED,
            "Updated project " + project.getName(),
            updatedProject.getId(),
            "/project.html?id=" + updatedProject.getId(),
            null
        );
        
        return updatedProject;
    }

    @Override
    public void deleteProject(Long id) {
        projectRepository.deleteById(id);
    }

    @Override
    public void addCollaborator(Project project, User user) {
        project.addUser(user, ProjectRole.CONTRIBUTOR);
        projectRepository.save(project);
        
        // Track activity
        activityService.trackActivity(
            user,
            UserActivity.ActivityType.PROJECT_UPDATED,
            "Added as collaborator to " + project.getName(),
            project.getId(),
            "/project.html?id=" + project.getId(),
            null
        );
    }

    @Override
    public void removeCollaborator(Project project, User user) {
        project.removeUser(user);
        projectRepository.save(project);
    }
    @Override
    @Transactional
    public Project forkProject(Project sourceProject, User newOwner) {
        // 1. Create a new project with most properties from the source project
        Project forkedProject = new Project();
        forkedProject.setName(sourceProject.getName() + "-fork");
        forkedProject.setDescription(sourceProject.getDescription() != null ? 
                "Fork of: " + sourceProject.getDescription() : "Fork of " + sourceProject.getName());
        forkedProject.setPublic(sourceProject.isPublic());
        forkedProject.setCreatedAt(LocalDateTime.now());
        forkedProject.setOwner(newOwner);
        
        // Set the forked from relationship
        forkedProject.setForkedFrom(sourceProject);
        
        // 2. Save the new project first to get an ID
        Project savedForkedProject = projectRepository.save(forkedProject);
        
        // 3. Copy branches from the original project
        List<Branch> sourceBranches = branchRepository.findByProject(sourceProject);
        Map<String, Branch> newBranches = new HashMap<>();
        
        // Create all branches first
        for (Branch sourceBranch : sourceBranches) {
            Branch newBranch = new Branch();
            newBranch.setName(sourceBranch.getName());
            newBranch.setProject(savedForkedProject);
            newBranch.setCreatedAt(LocalDateTime.now());
            newBranch.setDefault(sourceBranch.isDefault());
            
            Branch savedBranch = branchRepository.save(newBranch);
            newBranches.put(sourceBranch.getName(), savedBranch);
        }
        
        // 4. For each branch, copy its content using the version control service
        for (Branch sourceBranch : sourceBranches) {
            // Get project snapshot for this branch
            Map<String, String> snapshot = versionControlService.getProjectSnapshot(sourceProject, sourceBranch);
            
            // If we have files, commit them to the new branch
            if (!snapshot.isEmpty()) {
                Branch targetBranch = newBranches.get(sourceBranch.getName());
                versionControlService.commitChanges(
                    targetBranch, 
                    newOwner, 
                    "Initial commit from fork", 
                    snapshot
                );
            }
        }
        
        // 5. Track fork activity
        activityService.trackActivity(
            newOwner,
            UserActivity.ActivityType.PROJECT_FORKED, // Use the specific fork activity type
            "Forked project " + sourceProject.getName(),
            savedForkedProject.getId(),
            "/project.html?id=" + savedForkedProject.getId(),
            "forked from: " + sourceProject.getId()
        );
        
        return savedForkedProject;
    }
}