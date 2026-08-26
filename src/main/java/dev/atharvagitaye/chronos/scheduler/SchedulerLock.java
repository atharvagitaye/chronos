package dev.atharvagitaye.chronos.scheduler;

import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class SchedulerLock {

    private static final Logger log = LoggerFactory.getLogger(SchedulerLock.class);
    private final StringRedisTemplate redisTemplate;

    public SchedulerLock(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Attempts to acquire a lock for the given key and duration.
     * @param key the lock key
     * @param duration the duration to hold the lock
     * @return true if the lock was acquired, false otherwise
     */
    public boolean acquireLock(String key, Duration duration) {
        try {
            Boolean locked = redisTemplate.opsForValue().setIfAbsent(key, "locked", duration);
            return Boolean.TRUE.equals(locked);
        } catch (Exception e) {
            log.debug("Redis is unavailable for scheduler lock [key={}]. Proceeding without lock (relying on DB pessimistic locking): {}", key, e.getMessage());
            // Degraded behavior: if Redis is unavailable, allow the lock acquisition 
            // so scheduling can still happen (Postgres pessimistic locking prevents duplicates).
            return true; 
        }
    }
}
