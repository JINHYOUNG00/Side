package com.jinhyoung.salary.common;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 시간 빈 — 전 도메인이 주입받는 단일 KST Clock(CLAUDE.md 규칙 3, 아키텍처 8장).
 * 도메인 계산기·JWT 발급은 now() 직접 호출 대신 이 Clock만 사용한다.
 */
@Configuration
public class TimeConfig {

    public static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Bean
    public Clock kstClock() {
        return Clock.system(KST);
    }
}
