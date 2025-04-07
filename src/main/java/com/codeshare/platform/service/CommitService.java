package com.codeshare.platform.service;

import com.codeshare.platform.model.Branch;
import com.codeshare.platform.model.Commit;

import java.util.List;
import java.util.Optional;

public interface CommitService {
    Commit createCommit(Commit commit);
    Optional<Commit> getCommitById(Long id);
    List<Commit> getCommitsByBranch(Branch branch);
    Commit updateCommit(Commit commit);
    void deleteCommit(Long id);
}