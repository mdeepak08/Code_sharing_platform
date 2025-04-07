package com.codeshare.platform.service.impl;

import com.codeshare.platform.model.Branch;
import com.codeshare.platform.model.Commit;
import com.codeshare.platform.repository.CommitRepository;
import com.codeshare.platform.service.CommitService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class CommitServiceImpl implements CommitService {

    private final CommitRepository commitRepository;

    @Autowired
    public CommitServiceImpl(CommitRepository commitRepository) {
        this.commitRepository = commitRepository;
    }

    @Override
    public Commit createCommit(Commit commit) {
        return commitRepository.save(commit);
    }

    @Override
    public Optional<Commit> getCommitById(Long id) {
        return commitRepository.findById(id);
    }

    @Override
    public List<Commit> getCommitsByBranch(Branch branch) {
        return commitRepository.findByBranch(branch);
    }

    @Override
    public Commit updateCommit(Commit commit) {
        return commitRepository.save(commit);
    }

    @Override
    public void deleteCommit(Long id) {
        commitRepository.deleteById(id);
    }
}
