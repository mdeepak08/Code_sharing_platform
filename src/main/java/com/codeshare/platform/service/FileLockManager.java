package com.codeshare.platform.service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.codeshare.platform.model.File;
import com.codeshare.platform.model.User;

@Service
public class FileLockManager {
    
    // A record to store lock information
    private record FileLockInfo(Long userId, String username, LocalDateTime lockTime) {}
    
    // Map to store file locks: fileId -> lock information
    private final Map<Long, FileLockInfo> fileLocks = new ConcurrentHashMap<>();
    
    // Lock timeout in minutes
    private static final int LOCK_TIMEOUT_MINUTES = 30;
    
    /**
     * Acquire a lock on a file for a user
     * @return true if lock was acquired, false otherwise
     */
    public boolean acquireLock(File file, User user) {
        Long fileId = file.getId();
        FileLockInfo currentLock = fileLocks.get(fileId);
        
        // If file is not locked or lock has expired
        if (currentLock == null || isLockExpired(currentLock)) {
            fileLocks.put(fileId, new FileLockInfo(user.getId(), user.getUsername(), LocalDateTime.now()));
            return true;
        }
        
        // If current user already has the lock
        if (currentLock.userId().equals(user.getId())) {
            // Refresh the lock time
            fileLocks.put(fileId, new FileLockInfo(user.getId(), user.getUsername(), LocalDateTime.now()));
            return true;
        }
        
        // File is locked by another user
        return false;
    }
    
    /**
     * Release a lock on a file
     * @return true if lock was released, false if user didn't own the lock
     */
    public boolean releaseLock(File file, User user) {
        Long fileId = file.getId();
        FileLockInfo currentLock = fileLocks.get(fileId);
        
        // If file is not locked
        if (currentLock == null) {
            return true;
        }
        
        // Check if current user owns the lock
        if (currentLock.userId().equals(user.getId())) {
            fileLocks.remove(fileId);
            return true;
        }
        
        // File is locked by another user
        return false;
    }
    
    /**
     * Check if a file is locked by another user
     */
    public boolean isLockedByOtherUser(File file, User user) {
        Long fileId = file.getId();
        FileLockInfo currentLock = fileLocks.get(fileId);
        
        // If file is not locked or lock has expired
        if (currentLock == null || isLockExpired(currentLock)) {
            return false;
        }
        
        // Check if current user owns the lock
        return !currentLock.userId().equals(user.getId());
    }
    
    /**
     * Get lock information for a file
     */
    public Map<String, Object> getLockInfo(File file) {
        Long fileId = file.getId();
        FileLockInfo lock = fileLocks.get(fileId);
        
        Map<String, Object> info = new ConcurrentHashMap<>();
        if (lock == null || isLockExpired(lock)) {
            info.put("locked", false);
            return info;
        }
        
        info.put("locked", true);
        info.put("username", lock.username());
        info.put("lockTime", lock.lockTime());
        info.put("expiresAt", lock.lockTime().plusMinutes(LOCK_TIMEOUT_MINUTES));
        
        return info;
    }
    
    /**
     * Check if a lock has expired
     */
    private boolean isLockExpired(FileLockInfo lock) {
        return lock.lockTime().plusMinutes(LOCK_TIMEOUT_MINUTES).isBefore(LocalDateTime.now());
    }
}