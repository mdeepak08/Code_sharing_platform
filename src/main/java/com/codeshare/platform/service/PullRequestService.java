package com.codeshare.platform.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.codeshare.platform.model.Project;
import com.codeshare.platform.model.PullRequest;
import com.codeshare.platform.model.PullRequestStatus;
import com.codeshare.platform.model.User;

public interface PullRequestService {
    PullRequest createPullRequest(PullRequest pullRequest);
    Optional<PullRequest> getPullRequestById(Long id);
    List<PullRequest> getPullRequestsByProject(Project project);
    List<PullRequest> getPullRequestsByProjectAndStatus(Project project, PullRequestStatus status);
    List<PullRequest> getPullRequestsByAuthor(User author);
    PullRequest updatePullRequest(PullRequest pullRequest);
    void deletePullRequest(Long id);
    boolean checkMergeable(PullRequest pullRequest);
    void mergePullRequest(PullRequest pullRequest, User merger, String mergeMessage);
    void closePullRequest(PullRequest pullRequest, User closer);
    Map<String, Object> getDiffStats(PullRequest pullRequest);
    List<String> getChangedFiles(PullRequest pullRequest);
    List<PullRequest> getAllOpenPullRequests();
}