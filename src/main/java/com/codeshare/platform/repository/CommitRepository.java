package com.codeshare.platform.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.codeshare.platform.model.Branch;
import com.codeshare.platform.model.Commit;

@Repository
public interface CommitRepository extends JpaRepository<Commit, Long> {
    List<Commit> findByBranchOrderByCreatedAtDesc(Branch branch);

    public List<Commit> findByBranch(Branch branch);

}