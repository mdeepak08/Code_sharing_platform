package com.codeshare.platform.service.impl;

import com.codeshare.platform.model.File;
import com.codeshare.platform.model.Project;
import com.codeshare.platform.repository.FileRepository;
import com.codeshare.platform.service.FileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class FileServiceImpl implements FileService {

    private final FileRepository fileRepository;

    @Autowired
    public FileServiceImpl(FileRepository fileRepository) {
        this.fileRepository = fileRepository;
    }

    @Override
    public File createFile(File file) {
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
}
