package com.codeshare.platform.service.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
        List<File> existingFiles = fileRepository.findByProject(project);
        List<File> result = new ArrayList<>();
        
        // Get the latest commit for this branch
        Optional<Commit> latestCommit = commitRepository.findFirstByBranchOrderByCreatedAtDesc(branch);
        
        if (!latestCommit.isPresent()) {
            // If no commits for this branch, return the base files
            return existingFiles;
        }
        
        // Get the file snapshot for this branch
        Map<String, String> fileSnapshot = new HashMap<>();
        buildSnapshotFromCommitHistory(fileSnapshot, latestCommit.get());
        
        // Create copies of the files with content from the branch
        for (File file : existingFiles) {
            String content = fileSnapshot.get(file.getPath());
            
            // Only include files that exist in this branch's snapshot
            if (content != null) {
                File fileCopy = new File();
                fileCopy.setId(file.getId());
                fileCopy.setName(file.getName());
                fileCopy.setPath(file.getPath());
                fileCopy.setProject(file.getProject());
                fileCopy.setCreatedAt(file.getCreatedAt());
                fileCopy.setContent(content);
                result.add(fileCopy);
            }
        }
        
        return result;
    }
    
    /**
     * Helper method to build a snapshot by traversing commit history
     */
    private void buildSnapshotFromCommitHistory(Map<String, String> snapshot, Commit commit) {
        // First apply parent commit changes
        if (commit.getParentCommit() != null) {
            buildSnapshotFromCommitHistory(snapshot, commit.getParentCommit());
        }
        
        // Then apply this commit's changes
        try {
            if (commit.getFileChanges() != null && !commit.getFileChanges().isEmpty()) {
                Map<String, String> commitChanges = objectMapper.readValue(
                    commit.getFileChanges(), 
                    new TypeReference<HashMap<String, String>>() {}
                );
                snapshot.putAll(commitChanges);
            }
        } catch (Exception e) {
            // Log error but continue
            System.err.println("Error parsing file changes: " + e.getMessage());
        }
    }
}