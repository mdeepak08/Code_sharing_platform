package com.codeshare.platform.service;

import com.codeshare.platform.model.Branch;
import com.codeshare.platform.model.Project;

import java.util.List;
import java.util.Optional;

public interface BranchService {
    Branch createBranch(Branch branch);
    Optional<Branch> getBranchById(Long id);
    List<Branch> getBranchesByProject(Project project);
    Optional<Branch> getDefaultBranch(Project project);
    Branch updateBranch(Branch branch);
    void deleteBranch(Long id);
    Optional<Branch> getBranchByProjectAndName(Project project, String name);
}