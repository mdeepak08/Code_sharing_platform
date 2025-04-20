package com.codeshare.platform.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.codeshare.platform.dto.ApiResponse;
import com.codeshare.platform.model.Commit;
import com.codeshare.platform.repository.CommitRepository;

@RestController
@RequestMapping("/api/commits")
public class CommitController {

    @Autowired
    private CommitRepository commitRepository;

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Commit>> getCommitById(@PathVariable Long id) {
        Optional<Commit> commitOpt = commitRepository.findById(id);
        
        if (commitOpt.isPresent()) {
            return new ResponseEntity<>(ApiResponse.success(commitOpt.get()), HttpStatus.OK);
        } else {
            return new ResponseEntity<>(ApiResponse.error("Commit not found"), HttpStatus.NOT_FOUND);
        }
    }

    @GetMapping("/batch")
    public ResponseEntity<ApiResponse<List<Commit>>> getCommitsBatch(@RequestParam List<Long> ids) {
        List<Commit> commits = new ArrayList<>();
        
        for (Long id : ids) {
            Optional<Commit> commitOpt = commitRepository.findById(id);
            commitOpt.ifPresent(commits::add);
        }
        
        if (commits.isEmpty()) {
            return new ResponseEntity<>(ApiResponse.error("No commits found"), HttpStatus.NOT_FOUND);
        }
        
        return new ResponseEntity<>(ApiResponse.success(commits), HttpStatus.OK);
    }
}