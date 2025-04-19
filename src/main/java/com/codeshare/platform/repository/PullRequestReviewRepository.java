package com.codeshare.platform.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.codeshare.platform.model.PullRequest;
import com.codeshare.platform.model.PullRequestReview;
import com.codeshare.platform.model.User;

@Repository
public interface PullRequestReviewRepository extends JpaRepository<PullRequestReview, Long> {
    List<PullRequestReview> findByPullRequest(PullRequest pullRequest);
    List<PullRequestReview> findByReviewer(User reviewer);
}