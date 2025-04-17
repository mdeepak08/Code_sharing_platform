package com.codeshare.platform.controller;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.codeshare.platform.dto.ApiResponse;
import com.codeshare.platform.dto.ProjectDto;
import com.codeshare.platform.dto.UserDto;
import com.codeshare.platform.model.Project;
import com.codeshare.platform.model.User;
import com.codeshare.platform.service.ProjectService;
import com.codeshare.platform.service.UserService;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;
    private final UserService userService;

    @Autowired
    public ProjectController(ProjectService projectService, UserService userService) {
        this.projectService = projectService;
        this.userService = userService;
    }
    
    @PostMapping
    public ResponseEntity<ApiResponse<ProjectDto>> createProject(@RequestBody Project project, Authentication authentication) {
        String username = authentication.getName();
        Optional<User> ownerOpt = userService.getUserByUsername(username);
        
        if (ownerOpt.isPresent()) {
            project.setOwner(ownerOpt.get());
            Project createdProject = projectService.createProject(project);
            return new ResponseEntity<>(ApiResponse.success("Project created successfully", convertToDto(createdProject)), HttpStatus.CREATED);
        } else {
            return new ResponseEntity<>(ApiResponse.error("Owner not found"), HttpStatus.NOT_FOUND);
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProjectDto>> getProjectById(@PathVariable Long id, Authentication authentication) {
        Optional<Project> projectOpt = projectService.getProjectById(id);
        
        if (projectOpt.isEmpty()) {
            return new ResponseEntity<>(ApiResponse.error("Project not found"), HttpStatus.NOT_FOUND);
        }
        
        Project project = projectOpt.get();
        
        // Check if user has access to this project
        if (!project.isPublic()) {
            // If not public, check if user is owner or collaborator
            String username = authentication != null ? authentication.getName() : null;
            
            if (username == null || 
                (!project.getOwner().getUsername().equals(username) && 
                 !project.getUserProjects().stream()
                    .anyMatch(up -> up.getUser().getUsername().equals(username)))) {
                return new ResponseEntity<>(ApiResponse.error("You don't have access to this project"), 
                                          HttpStatus.FORBIDDEN);
            }
        }
        
        return new ResponseEntity<>(ApiResponse.success(convertToDto(project)), HttpStatus.OK);
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<ApiResponse<List<ProjectDto>>> getProjectsByUser(@PathVariable Long userId) {
        Optional<User> userOpt = userService.getUserById(userId);
        if (userOpt.isPresent()) {
            List<Project> projects = projectService.getProjectsByOwner(userOpt.get());
            List<ProjectDto> projectDtos = projects.stream().map(this::convertToDto).collect(Collectors.toList());
            return new ResponseEntity<>(ApiResponse.success(projectDtos), HttpStatus.OK);
        } else {
            return new ResponseEntity<>(ApiResponse.error("User not found"), HttpStatus.NOT_FOUND);
        }
    }

    /**
     * Updated method to handle partial updates for project properties
     */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ProjectDto>> updateProject(@PathVariable Long id, @RequestBody Map<String, Object> updates, Authentication authentication) {
        // Check if project exists
        Optional<Project> projectOpt = projectService.getProjectById(id);
        if (projectOpt.isEmpty()) {
            return new ResponseEntity<>(ApiResponse.error("Project not found"), HttpStatus.NOT_FOUND);
        }
        
        Project project = projectOpt.get();
        
        // Security check - only owner can update the project
        String username = authentication.getName();
        if (!project.getOwner().getUsername().equals(username)) {
            return new ResponseEntity<>(ApiResponse.error("You don't have permission to update this project"), 
                                      HttpStatus.FORBIDDEN);
        }
        
        // Apply updates if provided
        boolean updated = false;
        
        // Update visibility (public/private)
        if (updates.containsKey("isPublic")) {
            boolean isPublic = (Boolean) updates.get("isPublic");
            project.setPublic(isPublic);
            updated = true;
        }
        
        // Update name if provided
        if (updates.containsKey("name")) {
            String name = (String) updates.get("name");
            if (name != null && !name.trim().isEmpty()) {
                project.setName(name);
                updated = true;
            }
        }
        
        // Update description if provided
        if (updates.containsKey("description")) {
            String description = (String) updates.get("description");
            project.setDescription(description);
            updated = true;
        }
        
        // Only save if any updates were applied
        if (updated) {
            Project updatedProject = projectService.updateProject(project);
            return new ResponseEntity<>(
                ApiResponse.success("Project updated successfully", convertToDto(updatedProject)), 
                HttpStatus.OK
            );
        } else {
            return new ResponseEntity<>(
                ApiResponse.success("No changes to apply", convertToDto(project)), 
                HttpStatus.OK
            );
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteProject(@PathVariable Long id, Authentication authentication) {
        Optional<Project> projectOpt = projectService.getProjectById(id);
        if (projectOpt.isEmpty()) {
            return new ResponseEntity<>(ApiResponse.error("Project not found"), HttpStatus.NOT_FOUND);
        }
        
        Project project = projectOpt.get();
        
        // Security check - only owner can delete the project
        String username = authentication.getName();
        if (!project.getOwner().getUsername().equals(username)) {
            return new ResponseEntity<>(ApiResponse.error("You don't have permission to delete this project"), 
                                       HttpStatus.FORBIDDEN);
        }
        
        projectService.deleteProject(id);
        return new ResponseEntity<>(ApiResponse.success("Project deleted successfully", null), HttpStatus.OK);
    }

    @PostMapping("/{projectId}/collaborators/{userId}")
    public ResponseEntity<ApiResponse<Void>> addCollaborator(@PathVariable Long projectId, @PathVariable Long userId) {
        Optional<Project> projectOpt = projectService.getProjectById(projectId);
        Optional<User> userOpt = userService.getUserById(userId);
        
        if (projectOpt.isPresent() && userOpt.isPresent()) {
            projectService.addCollaborator(projectOpt.get(), userOpt.get());
            return new ResponseEntity<>(ApiResponse.success("Collaborator added successfully", null), HttpStatus.OK);
        } else {
            return new ResponseEntity<>(ApiResponse.error("Project or User not found"), HttpStatus.NOT_FOUND);
        }
    }

    @DeleteMapping("/{projectId}/collaborators/{userId}")
    public ResponseEntity<ApiResponse<Void>> removeCollaborator(@PathVariable Long projectId, @PathVariable Long userId) {
        Optional<Project> projectOpt = projectService.getProjectById(projectId);
        Optional<User> userOpt = userService.getUserById(userId);
        
        if (projectOpt.isPresent() && userOpt.isPresent()) {
            projectService.removeCollaborator(projectOpt.get(), userOpt.get());
            return new ResponseEntity<>(ApiResponse.success("Collaborator removed successfully", null), HttpStatus.OK);
        } else {
            return new ResponseEntity<>(ApiResponse.error("Project or User not found"), HttpStatus.NOT_FOUND);
        }
    }


    @GetMapping
    public ResponseEntity<ApiResponse<List<ProjectDto>>> getAllProjects(Authentication authentication) {
        try {
            if (authentication == null) {
                return new ResponseEntity<>(ApiResponse.error("Authentication required"), HttpStatus.UNAUTHORIZED);
            }
            
            String username = authentication.getName();
            Optional<User> userOpt = userService.getUserByUsername(username);
            
            if (userOpt.isPresent()) {
                User currentUser = userOpt.get();
                // Get only projects owned by this user
                List<Project> projects = projectService.getProjectsByOwner(currentUser);
                List<ProjectDto> projectDtos = projects.stream().map(this::convertToDto).collect(Collectors.toList());
                return new ResponseEntity<>(ApiResponse.success(projectDtos), HttpStatus.OK);
            } else {
                return new ResponseEntity<>(ApiResponse.error("User not found"), HttpStatus.NOT_FOUND);
            }
        } catch (Exception e) {
            return new ResponseEntity<>(ApiResponse.error("Error loading projects: " + e.getMessage()), 
                                        HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // Helper method to convert Project to ProjectDto
    private ProjectDto convertToDto(Project project) {
        ProjectDto dto = new ProjectDto();
        dto.setId(project.getId());
        dto.setName(project.getName());
        dto.setDescription(project.getDescription());
        dto.setPublic(project.isPublic());
        dto.setCreatedAt(project.getCreatedAt());
        
        UserDto ownerDto = new UserDto();
        ownerDto.setId(project.getOwner().getId());
        ownerDto.setUsername(project.getOwner().getUsername());
        ownerDto.setEmail(project.getOwner().getEmail());
        ownerDto.setFullName(project.getOwner().getFullName());
        
        dto.setOwner(ownerDto);
        return dto;
    }
}