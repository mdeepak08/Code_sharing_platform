package com.codeshare.platform.repository;

import com.codeshare.platform.model.File;
import com.codeshare.platform.model.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FileRepository extends JpaRepository<File, Long> {
    List<File> findByProject(Project project);
    Optional<File> findByProjectAndPath(Project project, String path);
}