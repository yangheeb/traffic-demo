package dev.fisa.filter;

public interface RateLimitFilter {
    boolean allowRequest();
}