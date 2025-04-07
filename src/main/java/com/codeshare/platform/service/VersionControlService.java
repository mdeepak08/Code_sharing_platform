package com.codeshare.platform.service;

import com.codeshare.platform.model.*;
import java.util.List;
import java.util.Map;

public interface VersionControlService {
    // Commit operations
    Commit commitChanges(Branch branch, User author, String message, Map<String, String> fileChanges);
    
    // Branch operations
    Branch createBranch(Project project, String branchName, User creator);
    void mergeBranches(Branch sourceBranch, Branch targetBranch, User merger);
    
    // File operations
    String getFileContent(File file, Branch branch);
    Map<String, String> getProjectSnapshot(Project project, Branch branch);
    
    // History operations
    List<Commit> getCommitHistory(Branch branch);
    List<String> getFileHistory(File file, Branch branch);
    
    // Diff operations
    Map<String, Object> getFileDiff(File file, Commit oldCommit, Commit newCommit);
}