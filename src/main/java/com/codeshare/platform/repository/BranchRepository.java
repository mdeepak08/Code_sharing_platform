package com.codeshare.platform.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.codeshare.platform.model.Branch;
import com.codeshare.platform.model.Project;

@Repository
public interface BranchRepository extends JpaRepository<Branch, Long> {
    List<Branch> findByProject(Project project);
    Optional<Branch> findByProjectAndName(Project project, String name);
    Optional<Branch> findByProjectAndIsDefaultTrue(Project project);

    public Optional<Branch> findByProjectAndIsDefault(Project project, boolean b);
}