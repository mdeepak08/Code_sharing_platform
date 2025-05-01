package com.codeshare.platform.service;

import java.util.List;
import java.util.Optional;

import com.codeshare.platform.model.Project;
import com.codeshare.platform.model.User;

public interface ProjectService {
    Project createProject(Project project);
    Optional<Project> getProjectById(Long id);
    List<Project> getAllProjects();
    List<Project> getProjectsByOwner(User owner);
    List<Project> getProjectsByCollaborator(User collaborator);
    List<Project> getPublicProjects();
    Project updateProject(Project project);
    void deleteProject(Long id);
    void addCollaborator(Project project, User user);
    void removeCollaborator(Project project, User user);
    Project forkProject(Project sourceProject, User newOwner);
}