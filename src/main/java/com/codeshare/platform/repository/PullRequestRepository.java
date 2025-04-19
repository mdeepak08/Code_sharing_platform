package com.codeshare.platform.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.codeshare.platform.model.Project;
import com.codeshare.platform.model.PullRequest;
import com.codeshare.platform.model.PullRequestStatus;
import com.codeshare.platform.model.User;

@Repository
public interface PullRequestRepository extends JpaRepository<PullRequest, Long> {
    List<PullRequest> findByProject(Project project);
    List<PullRequest> findByProjectAndStatus(Project project, PullRequestStatus status);
    List<PullRequest> findByAuthor(User author);
    List<PullRequest> findByStatus(PullRequestStatus status);
}