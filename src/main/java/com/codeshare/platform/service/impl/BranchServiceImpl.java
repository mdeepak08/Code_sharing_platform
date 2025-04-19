package com.codeshare.platform.service.impl;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.codeshare.platform.model.Branch;
import com.codeshare.platform.model.Project;
import com.codeshare.platform.repository.BranchRepository;
import com.codeshare.platform.service.BranchService;

@Service
public class BranchServiceImpl implements BranchService {

    private final BranchRepository branchRepository;

    @Autowired
    public BranchServiceImpl(BranchRepository branchRepository) {
        this.branchRepository = branchRepository;
    }

    @Override
    public Branch createBranch(Branch branch) {
        return branchRepository.save(branch);
    }

    @Override
    public Optional<Branch> getBranchById(Long id) {
        return branchRepository.findById(id);
    }

    @Override
    public List<Branch> getBranchesByProject(Project project) {
        return branchRepository.findByProject(project);
    }

    @Override
    public Optional<Branch> getDefaultBranch(Project project) {
        return branchRepository.findByProjectAndIsDefault(project, true);
    }

    @Override
    public Branch updateBranch(Branch branch) {
        return branchRepository.save(branch);
    }

    @Override
    public void deleteBranch(Long id) {
        branchRepository.deleteById(id);
    }

    @Override
    public Optional<Branch> getBranchByProjectAndName(Project project, String name) {
        return branchRepository.findByProjectAndName(project, name);
    }
}