package com.chatbot.platform.security.ratelimit;

/**
 * Interface defining rate limiting contracts.
 * Acts as an abstraction and extension point that can be swapped with Redis, Bucket4j,
 * or distributed token bucket implementations in production.
 */
public interface RateLimiter {

    /**
     * Checks if an attempt is allowed for the given client key (e.g., normalized email or IP).
     *
     * @param key the identifier for the rate limit bucket
     * @return true if allowed, false if limit is exceeded
     */
    boolean tryAcquire(String key);

    /**
     * Records a failed authentication attempt.
     */
    void recordFailure(String key);

    /**
     * Resets failed attempt counter upon successful authentication.
     */
    void reset(String key);
}
