package com.codeshare.platform.controller;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.codeshare.platform.dto.ApiResponse;
import com.codeshare.platform.dto.FileDto;
import com.codeshare.platform.model.File;
import com.codeshare.platform.model.Project;
import com.codeshare.platform.model.User;
import com.codeshare.platform.service.ConcurrencyService;
import com.codeshare.platform.service.FileLockManager;
import com.codeshare.platform.service.FileService;
import com.codeshare.platform.service.ProjectService;
import com.codeshare.platform.service.UserService;

@RestController
@RequestMapping("/api/files")
public class FileController {

    private final FileService fileService;
    private final ProjectService projectService;
    private final UserService userService;
    private final ConcurrencyService concurrencyService;
    private final FileLockManager fileLockManager;

    @Autowired
    public FileController(
            FileService fileService, 
            ProjectService projectService,
            UserService userService,
            ConcurrencyService concurrencyService,
            FileLockManager fileLockManager) {
        this.fileService = fileService;
        this.projectService = projectService;
        this.userService = userService;
        this.concurrencyService = concurrencyService;
        this.fileLockManager = fileLockManager;
    }

    // Get current authenticated user
    private User getCurrentUser() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || "anonymousUser".equals(auth.getName())) {
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
    }
    return userService.getUserByUsername(auth.getName())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
}

    @PostMapping
    public ResponseEntity<ApiResponse<FileDto>> createFile(@RequestBody File file, @RequestParam Long projectId) {
        return concurrencyService.executeWithWriteLock(projectId, () -> {
            Optional<Project> projectOpt = projectService.getProjectById(projectId);
            if (projectOpt.isPresent()) {
                file.setProject(projectOpt.get());
                File createdFile = fileService.createFile(file);
                FileDto fileDto = new FileDto(createdFile);
                return new ResponseEntity<>(ApiResponse.success("File created successfully", fileDto), HttpStatus.CREATED);
            } else {
                return new ResponseEntity<>(ApiResponse.error("Project not found"), HttpStatus.NOT_FOUND);
            }
        });
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<FileDto>> getFileById(@PathVariable Long id) {
        Optional<File> fileOpt = fileService.getFileById(id);
        if (fileOpt.isPresent()) {
            File file = fileOpt.get();
            return concurrencyService.executeWithReadLock(file.getProject().getId(), () -> {
                FileDto fileDto = new FileDto(file);
                return new ResponseEntity<>(ApiResponse.success(fileDto), HttpStatus.OK);
            });
        } else {
            return new ResponseEntity<>(ApiResponse.error("File not found"), HttpStatus.NOT_FOUND);
        }
    }

@GetMapping("/project/{projectId}")
public ResponseEntity<ApiResponse<List<FileDto>>> getFilesByProject(@PathVariable Long projectId) {
    return concurrencyService.executeWithReadLock(projectId, () -> {
        Optional<Project> projectOpt = projectService.getProjectById(projectId);
        if (projectOpt.isPresent()) {
            List<File> files = fileService.getFilesByProject(projectOpt.get());
            List<FileDto> fileDtos = files.stream()
                .map(FileDto::new)
                .collect(Collectors.toList());
            return new ResponseEntity<>(ApiResponse.success(fileDtos), HttpStatus.OK);
        } else {
            return new ResponseEntity<>(ApiResponse.error("Project not found"), HttpStatus.NOT_FOUND);
        }
    });
}



    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteFile(@PathVariable Long id) {
        Optional<File> existingFileOpt = fileService.getFileById(id);
        if (existingFileOpt.isPresent()) {
            File existingFile = existingFileOpt.get();
            User currentUser = getCurrentUser();
            
            return concurrencyService.executeWithWriteLock(existingFile.getProject().getId(), () -> {
                // Check if file is locked by another user
                if (fileLockManager.isLockedByOtherUser(existingFile, currentUser)) {
                    return new ResponseEntity<>(ApiResponse.error("File is locked by another user"), HttpStatus.CONFLICT);
                }
                
                fileService.deleteFile(id);
                return new ResponseEntity<>(ApiResponse.success("File deleted successfully", null), HttpStatus.OK);
            });
        } else {
            return new ResponseEntity<>(ApiResponse.error("File not found"), HttpStatus.NOT_FOUND);
        }
    }

    @PutMapping("/{id}/content")
    public ResponseEntity<ApiResponse<FileDto>> updateFileContent(
            @PathVariable Long id, 
            @RequestBody String content,
            Authentication authentication) {

        // Fallback to SecurityContextHolder if method param is null
        if (authentication == null) {
            authentication = SecurityContextHolder.getContext().getAuthentication();
        }

        if (authentication == null || "anonymousUser".equals(authentication.getName())) {
            return new ResponseEntity<>(ApiResponse.error("Authentication required"), HttpStatus.UNAUTHORIZED);
        }

        Optional<User> userOpt = userService.getUserByUsername(authentication.getName());
        if (userOpt.isEmpty()) {
            return new ResponseEntity<>(ApiResponse.error("User not found"), HttpStatus.UNAUTHORIZED);
        }

        User currentUser = userOpt.get();

        Optional<File> existingFileOpt = fileService.getFileById(id);
        if (existingFileOpt.isPresent()) {
            File existingFile = existingFileOpt.get();

            return concurrencyService.executeWithWriteLock(existingFile.getProject().getId(), () -> {
                if (fileLockManager.isLockedByOtherUser(existingFile, currentUser)) {
                    return new ResponseEntity<>(ApiResponse.error("File is locked by another user"), HttpStatus.CONFLICT);
                }

                existingFile.setContent(content);
                existingFile.setUpdatedAt(LocalDateTime.now());
                File updatedFile = fileService.updateFile(existingFile);
                FileDto fileDto = new FileDto(updatedFile);
                return new ResponseEntity<>(ApiResponse.success("File content updated successfully", fileDto), HttpStatus.OK);
            });
        } else {
            return new ResponseEntity<>(ApiResponse.error("File not found"), HttpStatus.NOT_FOUND);
        }
    }


    // File locking endpoints
    @PostMapping("/{id}/lock")
    public ResponseEntity<ApiResponse<Map<String, Object>>> lockFile(@PathVariable Long id) {
        Optional<File> fileOpt = fileService.getFileById(id);
        if (fileOpt.isPresent()) {
            File file = fileOpt.get();
            User currentUser = getCurrentUser();
            
            return concurrencyService.executeWithWriteLock(file.getProject().getId(), () -> {
                boolean acquired = fileLockManager.acquireLock(file, currentUser);
                if (acquired) {
                    return new ResponseEntity<>(ApiResponse.success("File locked successfully", fileLockManager.getLockInfo(file)), HttpStatus.OK);
                } else {
                    return new ResponseEntity<>(ApiResponse.error("File is already locked by another user"), HttpStatus.CONFLICT);
                }
            });
        } else {
            return new ResponseEntity<>(ApiResponse.error("File not found"), HttpStatus.NOT_FOUND);
        }
    }

    @PostMapping("/{id}/unlock")
    public ResponseEntity<ApiResponse<Void>> unlockFile(@PathVariable Long id) {
        Optional<File> fileOpt = fileService.getFileById(id);
        if (fileOpt.isPresent()) {
            File file = fileOpt.get();
            User currentUser = getCurrentUser();
            
            return concurrencyService.executeWithWriteLock(file.getProject().getId(), () -> {
                boolean released = fileLockManager.releaseLock(file, currentUser);
                if (released) {
                    return new ResponseEntity<>(ApiResponse.success("File unlocked successfully", null), HttpStatus.OK);
                } else {
                    return new ResponseEntity<>(ApiResponse.error("You don't have the lock on this file"), HttpStatus.FORBIDDEN);
                }
            });
        } else {
            return new ResponseEntity<>(ApiResponse.error("File not found"), HttpStatus.NOT_FOUND);
        }
    }

    @GetMapping("/{id}/lock-info")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getFileLockInfo(@PathVariable Long id) {
        Optional<File> fileOpt = fileService.getFileById(id);
        if (fileOpt.isPresent()) {
            File file = fileOpt.get();
            
            return concurrencyService.executeWithReadLock(file.getProject().getId(), () -> {
                Map<String, Object> lockInfo = fileLockManager.getLockInfo(file);
                return new ResponseEntity<>(ApiResponse.success(lockInfo), HttpStatus.OK);
            });
        } else {
            return new ResponseEntity<>(ApiResponse.error("File not found"), HttpStatus.NOT_FOUND);
        }
    }

    // Adjust your FileController.java
@GetMapping("/{id}/raw")
public ResponseEntity<String> getRawFileContent(@PathVariable Long id) {
    Optional<File> fileOpt = fileService.getFileById(id);
    if (fileOpt.isPresent()) {
        return ResponseEntity
                .ok()
                .contentType(MediaType.TEXT_PLAIN)
                .body(fileOpt.get().getContent());
    } else {
        return ResponseEntity.notFound().build();
    }
}
}