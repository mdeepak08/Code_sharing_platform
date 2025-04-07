package com.codeshare.platform.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.springframework.stereotype.Service;

@Service
public class ConcurrencyService {
    
    // Map to store locks for each project
    private final Map<Long, ReadWriteLock> projectLocks = new ConcurrentHashMap<>();
    
    /**
     * Get a read lock for a specific project
     * Read locks allow multiple readers but block writers
     */
    public Lock getProjectReadLock(Long projectId) {
        ReadWriteLock lock = projectLocks.computeIfAbsent(projectId, k -> new ReentrantReadWriteLock());
        return lock.readLock();
    }
    
    /**
     * Get a write lock for a specific project
     * Write locks block both readers and other writers
     */
    public Lock getProjectWriteLock(Long projectId) {
        ReadWriteLock lock = projectLocks.computeIfAbsent(projectId, k -> new ReentrantReadWriteLock());
        return lock.writeLock();
    }
    
    /**
     * Execute a read operation with proper locking
     */
    public <T> T executeWithReadLock(Long projectId, LockCallback<T> callback) {
        Lock lock = getProjectReadLock(projectId);
        lock.lock();
        try {
            return callback.execute();
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * Execute a write operation with proper locking
     */
    public <T> T executeWithWriteLock(Long projectId, LockCallback<T> callback) {
        Lock lock = getProjectWriteLock(projectId);
        lock.lock();
        try {
            return callback.execute();
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * Functional interface for operations that need to be executed under a lock
     */
    @FunctionalInterface
    public interface LockCallback<T> {
        T execute();
    }
}