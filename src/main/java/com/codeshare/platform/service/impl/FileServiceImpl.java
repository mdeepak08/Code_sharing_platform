package com.codeshare.platform.service.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.codeshare.platform.model.Branch;
import com.codeshare.platform.model.Commit;
import com.codeshare.platform.model.File;
import com.codeshare.platform.model.Project;
import com.codeshare.platform.repository.CommitRepository;
import com.codeshare.platform.repository.FileRepository;
import com.codeshare.platform.service.FileService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class FileServiceImpl implements FileService {

    private final FileRepository fileRepository;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private CommitRepository commitRepository;
    private static final Logger logger = LoggerFactory.getLogger(FileServiceImpl.class);

    @Autowired
    public FileServiceImpl(FileRepository fileRepository) {
        this.fileRepository = fileRepository;
    }

    @Override
    public File createFile(File file) {
            // Preserve whitespace and formatting
    String content = file.getContent();
    // Do not trim or modify content formatting
    file.setContent(content);
    return fileRepository.save(file);
    }

    @Override
    public Optional<File> getFileById(Long id) {
        return fileRepository.findById(id);
    }

    @Override
    public List<File> getFilesByProject(Project project) {
        return fileRepository.findByProject(project);
    }

    @Override
    public Optional<File> getFileByProjectAndPath(Project project, String path) {
        return fileRepository.findByProjectAndPath(project, path);
    }

    @Override
    public File updateFile(File file) {
        return fileRepository.save(file);
    }

    @Override
    public void deleteFile(Long id) {
        fileRepository.deleteById(id);
    }
    @Override
    public List<File> getFilesByProjectAndBranch(Project project, Branch branch) {
        // Get the latest commit on this branch
        Optional<Commit> latestCommitOpt = commitRepository.findFirstByBranchOrderByCreatedAtDesc(branch);
        
        if (latestCommitOpt.isEmpty()) {
            // No commits yet - return the base project files
            return fileRepository.findByProject(project);
        }
        
        Commit latestCommit = latestCommitOpt.get();
        
        // Get the file state at this commit
        Map<String, String> fileSnapshot = new HashMap<>();
        try {
            // Parse the file changes stored in the commit
            if (latestCommit.getFileChanges() != null && !latestCommit.getFileChanges().isEmpty()) {
                // Fixed TypeReference usage
                fileSnapshot = objectMapper.readValue(
                    latestCommit.getFileChanges(), 
                    new TypeReference<HashMap<String, String>>() {}
                );
            }
        } catch (Exception e) {
            logger.error("Error parsing file changes for commit {}: {}", latestCommit.getId(), e.getMessage());
            // Fallback to current files in database
            return fileRepository.findByProject(project);
        }
        
        // Now we need to create or update the in-memory representation of the files
        List<File> result = new ArrayList<>();
        
        // First, get all existing files for the project
        List<File> existingFiles = fileRepository.findByProject(project);
        Map<String, File> filesByPath = existingFiles.stream()
                .collect(Collectors.toMap(File::getPath, f -> f));
        
        // Create a final copy of the fileSnapshot for use in the lambda
        final Map<String, String> finalFileSnapshot = fileSnapshot;
        
        // Now update/create files based on the commit snapshot
        for (Map.Entry<String, String> entry : fileSnapshot.entrySet()) {
            String filePath = entry.getKey();
            String fileContent = entry.getValue();
            
            if (filesByPath.containsKey(filePath)) {
                // File exists, update content in memory (not in DB)
                File existingFile = filesByPath.get(filePath);
                // Create a copy to avoid modifying the persisted entity
                File fileCopy = new File();
                fileCopy.setId(existingFile.getId());
                fileCopy.setName(existingFile.getName());
                fileCopy.setPath(existingFile.getPath());
                fileCopy.setProject(existingFile.getProject());
                fileCopy.setCreatedAt(existingFile.getCreatedAt());
                fileCopy.setUpdatedAt(latestCommit.getCreatedAt());
                fileCopy.setContent(fileContent); // Use content from commit
                result.add(fileCopy);
            } else {
                // File doesn't exist in DB yet (new in this branch)
                File newFile = new File();
                
                // Extract filename from path
                String fileName = filePath;
                if (filePath.contains("/")) {
                    fileName = filePath.substring(filePath.lastIndexOf('/') + 1);
                }
                
                newFile.setName(fileName);
                newFile.setPath(filePath);
                newFile.setProject(project);
                newFile.setContent(fileContent);
                newFile.setCreatedAt(latestCommit.getCreatedAt());
                newFile.setUpdatedAt(latestCommit.getCreatedAt());
                result.add(newFile);
            }
        }
        
        // Fixed lambda to use the final reference
        result = result.stream()
                .filter(file -> finalFileSnapshot.containsKey(file.getPath()))
                .collect(Collectors.toList());
        
        return result;
    }
}