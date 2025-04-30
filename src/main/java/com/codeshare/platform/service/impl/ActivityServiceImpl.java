package com.codeshare.platform.service.impl;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.codeshare.platform.model.User;
import com.codeshare.platform.model.UserActivity;
import com.codeshare.platform.repository.UserActivityRepository;
import com.codeshare.platform.service.ActivityService;

@Service
public class ActivityServiceImpl implements ActivityService {

    @Autowired
    private UserActivityRepository activityRepository;
    
    @Override
    public UserActivity trackActivity(User user, UserActivity.ActivityType activityType, String description, 
                                      Long targetId, String resourceUrl, String details) {
        UserActivity activity = new UserActivity();
        activity.setUser(user);
        activity.setActivityType(activityType);
        activity.setDescription(description);
        activity.setTargetId(targetId);
        activity.setResourceUrl(resourceUrl);
        activity.setDetails(details);
        
        return activityRepository.save(activity);
    }

    @Override
    public List<UserActivity> getUserRecentActivities(User user, int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        return activityRepository.findByUserOrderByCreatedAtDesc(user, pageable);
    }

    @Override
    public List<UserActivity> getActivitiesByTarget(Long targetId, UserActivity.ActivityType activityType) {
        return activityRepository.findByTargetIdAndActivityType(targetId, activityType);
    }
}