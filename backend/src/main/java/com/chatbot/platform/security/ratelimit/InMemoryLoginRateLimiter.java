package com.chatbot.platform.security.ratelimit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe in-memory rate limiter protecting login endpoints from credential stuffing and brute-force attacks.
 * In a multi-instance production cluster, this component serves as the design template for Redis/Bucket4j backing.
 */
@Component
public class InMemoryLoginRateLimiter implements RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(InMemoryLoginRateLimiter.class);

    private static final int MAX_ATTEMPTS = 5;
    private static final long LOCKOUT_DURATION_SECONDS = 900; // 15 minutes

    private final ConcurrentHashMap<String, AttemptRecord> attempts = new ConcurrentHashMap<>();

    @Override
    public boolean tryAcquire(String key) {
        if (key == null || key.isBlank()) {
            return true;
        }

        AttemptRecord record = attempts.get(key);
        if (record == null) {
            return true;
        }

        Instant now = Instant.now();
        if (now.isAfter(record.lockedUntil)) {
            // Lockout period expired
            attempts.remove(key);
            return true;
        }

        return record.failedAttempts < MAX_ATTEMPTS;
    }

    @Override
    public void recordFailure(String key) {
        if (key == null || key.isBlank()) {
            return;
        }

        Instant now = Instant.now();
        attempts.compute(key, (k, existing) -> {
            if (existing == null || now.isAfter(existing.lockedUntil)) {
                return new AttemptRecord(1, now.plusSeconds(LOCKOUT_DURATION_SECONDS));
            }
            int newCount = existing.failedAttempts + 1;
            Instant lockUntil = (newCount >= MAX_ATTEMPTS)
                ? now.plusSeconds(LOCKOUT_DURATION_SECONDS)
                : existing.lockedUntil;

            if (newCount >= MAX_ATTEMPTS) {
                log.warn("Rate limit triggered: Account/IP [{}] locked out until {}", key, lockUntil);
            }
            return new AttemptRecord(newCount, lockUntil);
        });
    }

    @Override
    public void reset(String key) {
        if (key != null) {
            attempts.remove(key);
        }
    }

    private record AttemptRecord(int failedAttempts, Instant lockedUntil) {}
}
