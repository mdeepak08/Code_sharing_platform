package com.codeshare.platform.repository;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.codeshare.platform.model.User;
import com.codeshare.platform.model.UserActivity;

@Repository
public interface UserActivityRepository extends JpaRepository<UserActivity, Long> {
    List<UserActivity> findByUserOrderByCreatedAtDesc(User user);
    List<UserActivity> findByUserOrderByCreatedAtDesc(User user, Pageable pageable);
    List<UserActivity> findByTargetIdAndActivityType(Long targetId, UserActivity.ActivityType activityType);
}