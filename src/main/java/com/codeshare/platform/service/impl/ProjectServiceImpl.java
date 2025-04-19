package com.codeshare.platform.service.impl;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.codeshare.platform.model.Project;
import com.codeshare.platform.model.ProjectRole;
import com.codeshare.platform.model.User;
import com.codeshare.platform.model.UserProject;
import com.codeshare.platform.repository.ProjectRepository;
import com.codeshare.platform.repository.UserProjectRepository;
import com.codeshare.platform.service.ProjectService;

@Service
public class ProjectServiceImpl implements ProjectService {
    
    private final ProjectRepository projectRepository;
    private final UserProjectRepository userProjectRepository;

    @Autowired
    public ProjectServiceImpl(ProjectRepository projectRepository, UserProjectRepository userProjectRepository) {
        this.projectRepository = projectRepository;
        this.userProjectRepository = userProjectRepository;
    }

    @Override
    public Project createProject(Project project) {
        return projectRepository.save(project);
    }

    @Override
    public Optional<Project> getProjectById(Long id) {
        return projectRepository.findById(id);
    }

    @Override
    public List<Project> getAllProjects() {
        return projectRepository.findAll();
    }

    @Override
    public List<Project> getProjectsByOwner(User owner) {
        return projectRepository.findByOwner(owner);
    }

    @Override
    public List<Project> getProjectsByCollaborator(User collaborator) {
        List<UserProject> userProjects = userProjectRepository.findByUser(collaborator);
        return userProjects.stream()
                .map(UserProject::getProject)
                .collect(Collectors.toList());
    }

    @Override
    public List<Project> getPublicProjects() {
        return projectRepository.findByIsPublic(true);
    }

    @Override
    public Project updateProject(Project project) {
        return projectRepository.save(project);
    }

    @Override
    public void deleteProject(Long id) {
        projectRepository.deleteById(id);
    }

    @Override
    public void addCollaborator(Project project, User user) {
        project.addUser(user, ProjectRole.CONTRIBUTOR);
        projectRepository.save(project);
    }

    @Override
    public void removeCollaborator(Project project, User user) {
        project.removeUser(user);
        projectRepository.save(project);
    }
}