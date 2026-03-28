package dev.fisa.filter;

import dev.fisa.config.TrafficProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@ConditionalOnProperty(name = "traffic.strategy", havingValue = "LEAKY_BUCKET")
public class LeakyBucketFilter implements RateLimitFilter {

    private final int capacity;        // 버킷 최대 용량
    private final AtomicInteger queue; // 현재 버킷에 쌓인 요청 수
    private volatile long lastLeakTime;
    private final long leakIntervalMs; // 요청 1개 처리 간격

    public LeakyBucketFilter(TrafficProperties properties) {
        this.capacity = properties.getTpsLimit();
        this.queue = new AtomicInteger(0);
        this.lastLeakTime = System.currentTimeMillis();
        this.leakIntervalMs = 1000L / properties.getTpsLimit(); // 1초/15 = 66ms
    }

    @Override
    public boolean allowRequest() {
        leakIfNeeded();

        int current = queue.get();
        if (current >= capacity) {
            log.info("[LeakyBucket] 차단 - 버킷 가득 참: {}", current);
            return false;
        }

        if (queue.compareAndSet(current, current + 1)) {
            log.info("[LeakyBucket] 허용 - 버킷 사용량: {}", current + 1);
            return true;
        }

        return allowRequest();
    }

    private void leakIfNeeded() {
        long now = System.currentTimeMillis();
        long elapsed = now - lastLeakTime;
        int leakCount = (int) (elapsed / leakIntervalMs);

        if (leakCount > 0) {
            queue.updateAndGet(v -> Math.max(0, v - leakCount));
            lastLeakTime = now;
            log.info("[LeakyBucket] {}개 처리됨", leakCount);
        }
    }
}