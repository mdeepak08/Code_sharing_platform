package com.codeshare.platform.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.codeshare.platform.model.Project;
import com.codeshare.platform.model.User;

@Repository
public interface ProjectRepository extends JpaRepository<Project, Long> {
    List<Project> findByOwner(User owner);
    List<Project> findByIsPublicTrue();
    List<Project> findByIsPublic(boolean isPublic);
    List<Project> findByForkedFrom(Project sourceProject);
    int countByForkedFrom(Project sourceProject);
}