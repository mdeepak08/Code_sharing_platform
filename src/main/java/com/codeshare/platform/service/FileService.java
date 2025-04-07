package com.codeshare.platform.service;

import com.codeshare.platform.model.File;
import com.codeshare.platform.model.Project;

import java.util.List;
import java.util.Optional;

public interface FileService {
    File createFile(File file);
    Optional<File> getFileById(Long id);
    List<File> getFilesByProject(Project project);
    Optional<File> getFileByProjectAndPath(Project project, String path);
    File updateFile(File file);
    void deleteFile(Long id);
}