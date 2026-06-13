package com.jinhyoung.salary.common;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 스케줄링 활성화(아키텍처 4장 일일 배치 04:00 KST). Quartz·Spring Batch는 채택하지 않는다(ADR-04) —
 * 하루 1회·소규모 데이터엔 {@code @Scheduled} 단일 인스턴스로 충분하고 모든 단계가 멱등이라 안전하다.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {}
