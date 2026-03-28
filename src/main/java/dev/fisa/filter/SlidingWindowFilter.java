package dev.fisa.filter;

import dev.fisa.config.TrafficProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentLinkedQueue;

@Slf4j
@Component
@ConditionalOnProperty(name = "traffic.strategy", havingValue = "SLIDING_WINDOW")
public class SlidingWindowFilter implements RateLimitFilter {

    private final int maxRequests;
    private final long windowSizeMs = 1000; // 1초
    private final ConcurrentLinkedQueue<Long> requestTimestamps;

    public SlidingWindowFilter(TrafficProperties properties) {
        this.maxRequests = properties.getTpsLimit();
        this.requestTimestamps = new ConcurrentLinkedQueue<>();
    }

    @Override
    public boolean allowRequest() {
        long now = System.currentTimeMillis();
        long windowStart = now - windowSizeMs;

        // 1초 이전 요청 제거
        while (!requestTimestamps.isEmpty()
                && requestTimestamps.peek() < windowStart) {
            requestTimestamps.poll();
        }

        if (requestTimestamps.size() >= maxRequests) {
            log.info("[SlidingWindow] 차단 - 현재 요청 수: {}",
                    requestTimestamps.size());
            return false;
        }

        requestTimestamps.offer(now);
        log.info("[SlidingWindow] 허용 - 현재 요청 수: {}",
                requestTimestamps.size());
        return true;
    }
}