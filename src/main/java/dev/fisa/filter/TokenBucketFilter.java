package dev.fisa.filter;

import dev.fisa.config.TrafficProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@ConditionalOnProperty(name = "traffic.strategy", havingValue = "TOKEN_BUCKET")
public class TokenBucketFilter implements RateLimitFilter {

    private final int maxTokens;
    private final AtomicInteger tokens;
    private volatile long lastRefillTime;
    private final long refillIntervalMs = 1000; // 1초마다 충전

    public TokenBucketFilter(TrafficProperties properties) {
        this.maxTokens = properties.getTpsLimit();
        this.tokens = new AtomicInteger(maxTokens);
        this.lastRefillTime = System.currentTimeMillis();
    }

    @Override
    public boolean allowRequest() {
        refillIfNeeded();

        int current = tokens.get();
        if (current <= 0) {
            log.info("[TokenBucket] 차단 - 토큰 없음");
            return false;
        }

        if (tokens.compareAndSet(current, current - 1)) {
            log.info("[TokenBucket] 허용 - 남은 토큰: {}", current - 1);
            return true;
        }

        return allowRequest(); // CAS 실패 시 재시도
    }

    private void refillIfNeeded() {
        long now = System.currentTimeMillis();
        if (now - lastRefillTime >= refillIntervalMs) {
            tokens.set(maxTokens);
            lastRefillTime = now;
            log.info("[TokenBucket] 토큰 충전 - {}개", maxTokens);
        }
    }
}