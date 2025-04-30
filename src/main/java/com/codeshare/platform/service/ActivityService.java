package com.codeshare.platform.service;

import java.util.List;

import com.codeshare.platform.model.User;
import com.codeshare.platform.model.UserActivity;

public interface ActivityService {
    UserActivity trackActivity(
            User user, 
            UserActivity.ActivityType activityType, 
            String description, 
            Long targetId, 
            String resourceUrl, 
            String details);
    
    List<UserActivity> getUserRecentActivities(User user, int limit);
    
    List<UserActivity> getActivitiesByTarget(Long targetId, UserActivity.ActivityType activityType);
}