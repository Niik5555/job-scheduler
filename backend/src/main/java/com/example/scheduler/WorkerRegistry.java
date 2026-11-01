package com.example.scheduler;

import java.util.List;
import java.util.Set;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/** Reads worker heartbeats from Redis. A worker is alive while its key has not expired. */
@Component
@RequiredArgsConstructor
public class WorkerRegistry {

    private static final String PREFIX = "worker:";

    private final StringRedisTemplate redis;

    public boolean isAlive(String workerId) {
        return Boolean.TRUE.equals(redis.hasKey(PREFIX + workerId));
    }

    public List<String> aliveWorkers() {
        Set<String> keys = redis.keys(PREFIX + "*");
        if (keys == null) {
            return List.of();
        }
        return keys.stream().map(key -> key.substring(PREFIX.length())).sorted().toList();
    }
}
