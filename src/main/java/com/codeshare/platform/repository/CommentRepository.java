package com.codeshare.platform.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.codeshare.platform.model.Comment;
import com.codeshare.platform.model.PullRequest;

@Repository
public interface CommentRepository extends JpaRepository<Comment, Long> {
    List<Comment> findByPullRequest(PullRequest pullRequest);
    List<Comment> findByPullRequestAndFilePath(PullRequest pullRequest, String filePath);
    List<Comment> findByPullRequestAndFilePathAndLineNumber(PullRequest pullRequest, String filePath, Integer lineNumber);
}