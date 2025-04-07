package com.codeshare.platform.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.codeshare.platform.model.Project;
import com.codeshare.platform.model.ProjectRole;
import com.codeshare.platform.model.User;
import com.codeshare.platform.model.UserProject;

@Repository
public interface UserProjectRepository extends JpaRepository<UserProject, Long> {
    List<UserProject> findByUser(User user);
    List<UserProject> findByProject(Project project);
    Optional<UserProject> findByUserAndProject(User user, Project project);
    List<UserProject> findByUserAndRole(User user, ProjectRole role);
    List<UserProject> findByProjectAndRole(Project project, ProjectRole role);
} 