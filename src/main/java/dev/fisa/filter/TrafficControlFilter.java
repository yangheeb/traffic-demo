package dev.fisa.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Optional;

@Slf4j
@Component
public class TrafficControlFilter implements Filter {

    @Autowired(required = false)  // 빈이 없어도 에러 안 남
    private RateLimitFilter rateLimitFilter;

    @Override
    public void doFilter(ServletRequest request,
                         ServletResponse response,
                         FilterChain chain) throws IOException, jakarta.servlet.ServletException {

        // strategy 없으면 Filter 없이 그냥 통과
        if (rateLimitFilter == null || rateLimitFilter.allowRequest()) {
            chain.doFilter(request, response);
            return;
        }

        HttpServletResponse res = (HttpServletResponse) response;
        res.setStatus(429);
        res.getWriter().write("차단: 요청이 너무 많습니다");
    }
}