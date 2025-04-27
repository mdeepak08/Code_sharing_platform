package com.codeshare.platform.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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
    @Query("SELECT pr FROM PullRequest pr JOIN pr.reviewers rev WHERE rev = :user")
    List<PullRequest> findByReviewer(@Param("user") User user);

    @Query("SELECT DISTINCT pr FROM PullRequest pr JOIN Comment c ON c.pullRequest = pr WHERE c.user = :user")
    List<PullRequest> findByCommentAuthor(@Param("user") User user);

    @Query("SELECT pr FROM PullRequest pr WHERE pr.description LIKE %:mention%")
    List<PullRequest> findByDescriptionContaining(@Param("mention") String mention);
}