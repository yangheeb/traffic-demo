package dev.fisa.filter;

import dev.fisa.config.TrafficProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@ConditionalOnProperty(name = "traffic.strategy", havingValue = "FIXED_WINDOW")
public class FixedWindowFilter implements RateLimitFilter {

    private final int maxRequests;
    private final AtomicInteger counter;
    private volatile long windowStart;
    private final long windowSizeMs = 1000; // 1초 창

    public FixedWindowFilter(TrafficProperties properties) {
        this.maxRequests = properties.getTpsLimit();
        this.counter = new AtomicInteger(0);
        this.windowStart = System.currentTimeMillis();
    }

    @Override
    public boolean allowRequest() {
        resetIfNeeded();

        int current = counter.incrementAndGet();
        if (current > maxRequests) {
            log.info("[FixedWindow] 차단 - 현재 카운트: {}", current);
            return false;
        }

        log.info("[FixedWindow] 허용 - 현재 카운트: {}", current);
        return true;
    }

    private void resetIfNeeded() {
        long now = System.currentTimeMillis();
        if (now - windowStart >= windowSizeMs) {
            counter.set(0);
            windowStart = now;
            log.info("[FixedWindow] 창 초기화");
        }
    }
}