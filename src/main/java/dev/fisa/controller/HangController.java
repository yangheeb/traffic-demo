package dev.fisa.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor
public class HangController {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 시나리오 재현용 엔드포인트
     * → 쿼리 자체는 정상 (0.1초)
     * → 200 TPS가 동시에 몰리면
     * → 커넥션 10개가 고갈
     * → 새 요청이 커넥션 못 얻어 Timeout 발생
     */
    @GetMapping("/api/request")
    public String request() {
        // 살짝 처리 시간이 걸리는 정상 쿼리
        jdbcTemplate.execute("SELECT SLEEP(0.5)"); // 0.1초
        return "ok";
    }
}