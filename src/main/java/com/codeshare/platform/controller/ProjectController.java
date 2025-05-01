package com.codeshare.platform.controller;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
import com.codeshare.platform.model.Branch;
import com.codeshare.platform.model.File;
import com.codeshare.platform.model.Project;
import com.codeshare.platform.model.User;
import com.codeshare.platform.repository.ProjectRepository;
import com.codeshare.platform.service.BranchService;
import com.codeshare.platform.service.FileService;
import com.codeshare.platform.service.ProjectService;
import com.codeshare.platform.service.UserService;
import com.codeshare.platform.service.VersionControlService;


@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;
    private final UserService userService;
    private final ProjectRepository projectRepository;

    @Autowired
    public ProjectController(ProjectService projectService, UserService userService, ProjectRepository projectRepository) {
        this.projectService = projectService;
        this.userService = userService;
        this.projectRepository = projectRepository;
    }
    @Autowired
    private FileService fileService;

    @Autowired
    private BranchService branchService;

    @Autowired
    private VersionControlService versionControlService;
    
    @PostMapping
    public ResponseEntity<ApiResponse<ProjectDto>> createProject(@RequestBody Map<String, Object> projectData, Authentication authentication) {
        String username = authentication.getName();
        Optional<User> ownerOpt = userService.getUserByUsername(username);
        
        if (ownerOpt.isEmpty()) {
            return new ResponseEntity<>(ApiResponse.error("Owner not found"), HttpStatus.NOT_FOUND);
        }
        
        // Create project
        Project project = new Project();
        project.setName((String) projectData.get("name"));
        project.setDescription((String) projectData.get("description"));
        project.setPublic((Boolean) projectData.getOrDefault("isPublic", true));
        project.setOwner(ownerOpt.get());
        project.setCreatedAt(LocalDateTime.now());
        
        // Save project
        Project createdProject = projectService.createProject(project);
        
        // Create default branch if it doesn't exist
        Branch defaultBranch;
        Optional<Branch> existingDefaultBranch = branchService.getDefaultBranch(createdProject);
        
        if (existingDefaultBranch.isPresent()) {
            defaultBranch = existingDefaultBranch.get();
        } else {
            // Create a new main branch
            Branch newBranch = new Branch();
            newBranch.setName("main");
            newBranch.setProject(createdProject);
            newBranch.setCreatedAt(LocalDateTime.now());
            newBranch.setDefault(true);
            
            defaultBranch = branchService.createBranch(newBranch);
        }
        
        // Handle README initialization if requested
        Boolean initializeWithReadme = (Boolean) projectData.getOrDefault("initializeWithReadme", false);
        if (initializeWithReadme) {
            // Create README.md file
            File readmeFile = new File();
            readmeFile.setName("README.md");
            readmeFile.setPath("README.md");
            readmeFile.setProject(createdProject);
            readmeFile.setContent(generateDefaultReadme(createdProject));
            readmeFile.setCreatedAt(LocalDateTime.now());
            
            fileService.createFile(readmeFile);
            
            // Create initial commit with README using the defaultBranch we just ensured exists
            Map<String, String> fileChanges = new HashMap<>();
            fileChanges.put("README.md", readmeFile.getContent());
            
            versionControlService.commitChanges(
                defaultBranch, 
                ownerOpt.get(), 
                "Initial commit: Add README.md", 
                fileChanges
            );
        }
        
        return new ResponseEntity<>(
            ApiResponse.success("Project created successfully", convertToDto(createdProject)), 
            HttpStatus.CREATED
        );
    }
// Helper method to generate default README content
private String generateDefaultReadme(Project project) {
    StringBuilder content = new StringBuilder();
    content.append("# ").append(project.getName()).append("\n\n");
    
    if (project.getDescription() != null && !project.getDescription().isEmpty()) {
        content.append(project.getDescription()).append("\n\n");
    }
    
    content.append("## About\n\n");
    content.append("This repository was created with Code Sharing Platform.\n\n");
    content.append("## Getting Started\n\n");
    content.append("To get started with this project, follow these steps:\n\n");
    content.append("1. Clone the repository\n");
    content.append("2. [Add your instructions here]\n");
    content.append("3. [Add more steps as needed]\n\n");
    content.append("## License\n\n");
    content.append("This project is licensed under the MIT License - see the LICENSE file for details.\n");
    
    return content.toString();
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
        /**
     * Get all public projects
     * New endpoint for explore page
     */
    @GetMapping("/public")
    public ResponseEntity<ApiResponse<List<ProjectDto>>> getPublicProjects() {
        try {
            List<Project> publicProjects = projectService.getPublicProjects();
            List<ProjectDto> projectDtos = publicProjects.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
            
            return new ResponseEntity<>(ApiResponse.success(projectDtos), HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(ApiResponse.error("Error loading public projects: " + e.getMessage()), 
                                        HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }



    @PostMapping("/{id}/fork")
    public ResponseEntity<ApiResponse<ProjectDto>> forkProject(@PathVariable Long id, Authentication authentication) {
        try {
            // Verify the source project exists
            Optional<Project> sourceProjectOpt = projectService.getProjectById(id);
            if (sourceProjectOpt.isEmpty()) {
                return new ResponseEntity<>(ApiResponse.error("Project not found"), HttpStatus.NOT_FOUND);
            }
            
            Project sourceProject = sourceProjectOpt.get();
            
            // Check if the project is public or the user has access
            if (!sourceProject.isPublic()) {
                // Get current user
                String username = authentication.getName();
                Optional<User> userOpt = userService.getUserByUsername(username);
                
                if (userOpt.isEmpty()) {
                    return new ResponseEntity<>(ApiResponse.error("User not found"), HttpStatus.UNAUTHORIZED);
                }
                
                User user = userOpt.get();
                
                // Check if user is owner or collaborator
                boolean hasAccess = sourceProject.getOwner().getId().equals(user.getId()) || 
                    sourceProject.getUserProjects().stream()
                        .anyMatch(up -> up.getUser().getId().equals(user.getId()));
                
                if (!hasAccess) {
                    return new ResponseEntity<>(
                        ApiResponse.error("You don't have access to fork this private project"), 
                        HttpStatus.FORBIDDEN
                    );
                }
            }
            
            // Get the current user as new owner
            String username = authentication.getName();
            Optional<User> newOwnerOpt = userService.getUserByUsername(username);
            
            if (newOwnerOpt.isEmpty()) {
                return new ResponseEntity<>(ApiResponse.error("User not found"), HttpStatus.UNAUTHORIZED);
            }
            
            // Create the fork
            Project forkedProject = projectService.forkProject(sourceProject, newOwnerOpt.get());
            
            // Convert to DTO and return response
            ProjectDto forkedProjectDto = convertToDto(forkedProject);
            
            return new ResponseEntity<>(
                ApiResponse.success("Project forked successfully", forkedProjectDto),
                HttpStatus.CREATED
            );
            
        } catch (Exception e) {
            return new ResponseEntity<>(
                ApiResponse.error("Failed to fork project: " + e.getMessage()),
                HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }

    @GetMapping("/{id}/forks/count")
    public ResponseEntity<ApiResponse<Integer>> getForksCount(@PathVariable Long id) {
        try {
            Optional<Project> projectOpt = projectService.getProjectById(id);
            if (projectOpt.isEmpty()) {
                return new ResponseEntity<>(ApiResponse.error("Project not found"), HttpStatus.NOT_FOUND);
            }
            
            // Use the repository to count forks directly
            int forkCount = projectRepository.countByForkedFrom(projectOpt.get());
            
            return new ResponseEntity<>(ApiResponse.success(forkCount), HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(
                ApiResponse.error("Error retrieving fork count: " + e.getMessage()),
                HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }
    
    // Add this method to get all forks of a repository
    @GetMapping("/{id}/forks")
    public ResponseEntity<ApiResponse<List<ProjectDto>>> getProjectForks(@PathVariable Long id) {
        try {
            Optional<Project> projectOpt = projectService.getProjectById(id);
            if (projectOpt.isEmpty()) {
                return new ResponseEntity<>(ApiResponse.error("Project not found"), HttpStatus.NOT_FOUND);
            }
            
            // Get all forks of this project
            List<Project> forks = projectRepository.findByForkedFrom(projectOpt.get());
            
            // Convert to DTOs
            List<ProjectDto> forkDtos = forks.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
            
            return new ResponseEntity<>(ApiResponse.success(forkDtos), HttpStatus.OK);
        } catch (Exception e) {
            return new ResponseEntity<>(
                ApiResponse.error("Error retrieving forks: " + e.getMessage()),
                HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }

    /**
 * Get language statistics for a project
 */
@GetMapping("/{projectId}/languages")
public ResponseEntity<ApiResponse<Map<String, Double>>> getLanguageStatistics(@PathVariable Long projectId) {
    try {
        Optional<Project> projectOpt = projectService.getProjectById(projectId);
        if (projectOpt.isEmpty()) {
            return new ResponseEntity<>(ApiResponse.error("Project not found"), HttpStatus.NOT_FOUND);
        }
        
        Project project = projectOpt.get();
        
        // Get the default branch for this project
        Optional<Branch> defaultBranchOpt = branchService.getDefaultBranch(project);
        if (defaultBranchOpt.isEmpty()) {
            return new ResponseEntity<>(ApiResponse.error("No default branch found"), HttpStatus.NOT_FOUND);
        }
        
        // Get files using the version control service
        Map<String, String> fileContents = versionControlService.getProjectSnapshot(project, defaultBranchOpt.get());
        
        // Calculate language statistics from these files
        Map<String, Long> languageBytes = new HashMap<>();
        long totalBytes = 0;
        
        // Set of files to ignore (similar to .gitattributes linguist-vendored)
        Set<String> ignoredPaths = new HashSet<>(Arrays.asList(
            "gradlew", "gradlew.bat", "build.gradle",  // Build files
            ".gitignore", ".gitattributes",           // Git files
            "README.md", "LICENSE", "CHANGELOG.md",   // Documentation
            "package-lock.json", "yarn.lock"          // Package lock files
        ));
        
        // Set of directories to ignore
        Set<String> ignoredDirs = new HashSet<>(Arrays.asList(
            "node_modules/", "vendor/", "build/", "dist/", 
            "target/", "bin/", "obj/", ".git/"
        ));
        
        for (Map.Entry<String, String> entry : fileContents.entrySet()) {
            String filePath = entry.getKey();
            String content = entry.getValue();
            
            if (content == null) continue;
            
            // Skip files that should be ignored
            if (shouldIgnoreFile(filePath, ignoredPaths, ignoredDirs)) {
                continue;
            }
            
            // Improved language detection - more like GitHub's Linguist
            String language = improvedLanguageDetection(filePath);
            if (language != null) {
                // Count bytes of content for this language
                long bytes = content.getBytes().length;
                languageBytes.put(language, languageBytes.getOrDefault(language, 0L) + bytes);
                totalBytes += bytes;
            }
        }
        
        // Combine small categories into "Other"
        final double THRESHOLD = 0.5; // Percentage threshold to be shown individually
        Map<String, Long> combinedLanguageBytes = new HashMap<>();
        long otherBytes = 0;
        
        for (Map.Entry<String, Long> entry : languageBytes.entrySet()) {
            double percentage = (entry.getValue() * 100.0) / totalBytes;
            if (percentage >= THRESHOLD) {
                combinedLanguageBytes.put(entry.getKey(), entry.getValue());
            } else {
                otherBytes += entry.getValue();
            }
        }
        
        // Add "Other" category if it's not empty
        if (otherBytes > 0) {
            combinedLanguageBytes.put("Other", otherBytes);
        }
        
        // Convert byte counts to percentages
        Map<String, Double> languagePercentages = new HashMap<>();
        if (totalBytes > 0) {
            for (Map.Entry<String, Long> entry : combinedLanguageBytes.entrySet()) {
                double percentage = (entry.getValue() * 100.0) / totalBytes;
                // Round to 1 decimal place
                percentage = Math.round(percentage * 10.0) / 10.0;
                languagePercentages.put(entry.getKey(), percentage);
            }
        }
        
        return new ResponseEntity<>(ApiResponse.success(languagePercentages), HttpStatus.OK);
    } catch (Exception e) {
        return new ResponseEntity<>(ApiResponse.error("Error calculating language statistics: " + e.getMessage()),
                HttpStatus.INTERNAL_SERVER_ERROR);
    }
}

private boolean shouldIgnoreFile(String filePath, Set<String> ignoredPaths, Set<String> ignoredDirs) {
    // Check exact path matches
    if (ignoredPaths.contains(filePath)) {
        return true;
    }
    
    // Check if file is in an ignored directory
    for (String dir : ignoredDirs) {
        if (filePath.startsWith(dir)) {
            return true;
        }
    }
    
    // Skip hidden files (dotfiles)
    if (filePath.startsWith(".") || filePath.contains("/.")) {
        return true;
    }
    
    // Skip binary files (this is a simple heuristic)
    String lowercasePath = filePath.toLowerCase();
    if (lowercasePath.endsWith(".png") || lowercasePath.endsWith(".jpg") || 
        lowercasePath.endsWith(".jpeg") || lowercasePath.endsWith(".gif") ||
        lowercasePath.endsWith(".ico") || lowercasePath.endsWith(".pdf") ||
        lowercasePath.endsWith(".zip") || lowercasePath.endsWith(".jar") ||
        lowercasePath.endsWith(".class")) {
        return true;
    }
    
    return false;
}

private String improvedLanguageDetection(String filename) {
    if (!filename.contains(".")) {
        // No extension, try to detect by shebang or other means
        return detectLanguageByName(filename);
    }

    String ext = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    String basename = filename.toLowerCase();
    
    // Special case handling
    if (basename.equals("dockerfile")) return "Dockerfile";
    if (basename.equals("makefile")) return "Makefile";
    if (basename.equals(".gitignore")) return null; // Skip git files
    if (basename.equals("readme.md") || basename.equals("readme.txt")) return "Markdown";
    
    // Extension mapping that more closely aligns with GitHub
    switch (ext) {
        // Java files
        case "java": return "Java";
        
        // Web files - more specific than before
        case "html": case "htm": case "xhtml": return "HTML";
        case "css": case "scss": case "sass": case "less": return "CSS";
        case "js": case "jsx": case "mjs": case "cjs": return "JavaScript";
        case "ts": case "tsx": return "TypeScript";
        
        // Config files - Map these to their actual languages instead of generic "Config"
        case "xml": case "xsd": case "xsl": return "XML";
        case "json": return "JSON";
        case "yml": case "yaml": return "YAML";
        case "properties": case "conf": case "config": case "ini": return "Properties";
        
        // Documentation files
        case "md": case "markdown": return "Markdown";
        case "txt": return null; // Skip plain text files or count as "Other"
        
        // Script files
        case "sh": case "bash": return "Shell";
        case "bat": case "cmd": return "Batch";
        case "ps1": return "PowerShell";
        case "py": return "Python";
        case "rb": return "Ruby";
        case "php": return "PHP";
        
        // Build files - More accurately labeled
        case "gradle": case "groovy": return "Groovy";
        case "kt": case "kts": return "Kotlin";
        
        // Other common languages
        case "c": return "C";
        case "cpp": case "cc": case "cxx": case "h": case "hpp": return "C++";
        case "cs": return "C#";
        case "go": return "Go";
        case "rs": return "Rust";
        case "swift": return "Swift";
        
        // Default case - return null to skip or categorize as Other
        default: return null;
    }
}

private String detectLanguageByName(String filename) {
    // Common files without extensions
    switch (filename.toLowerCase()) {
        case "makefile": return "Makefile";
        case "dockerfile": return "Dockerfile";
        case "jenkinsfile": return "Groovy";
        case "rakefile": return "Ruby";
        default: return null; // Skip or categorize as Other
    }
}

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
    
    // Add fork information
    if (project.getForkedFrom() != null) {
        dto.setFork(true);  // Changed from setIsFork to setFork
        dto.setForkedFromId(project.getForkedFrom().getId());
        dto.setForkedFromName(project.getForkedFrom().getName());
    } else {
        dto.setFork(false);  // Changed from setIsFork to setFork
    }
    
    return dto;
}
}