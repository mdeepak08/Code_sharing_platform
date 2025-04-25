package com.codeshare.platform.service;

import java.util.List;
import java.util.Optional;

import com.codeshare.platform.model.Branch;
import com.codeshare.platform.model.File;
import com.codeshare.platform.model.Project;

public interface FileService {
    File createFile(File file);
    Optional<File> getFileById(Long id);
    List<File> getFilesByProject(Project project);
    Optional<File> getFileByProjectAndPath(Project project, String path);
    File updateFile(File file);
    void deleteFile(Long id);
    List<File> getFilesByProjectAndBranch(Project project, Branch branch);
}