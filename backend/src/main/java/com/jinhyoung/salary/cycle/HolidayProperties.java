package com.jinhyoung.salary.cycle;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 공휴일 수집 설정(application.yml의 {@code app.holiday}). 공공데이터포털 특일 API. 서비스 키는 환경변수로만
 * 주입하고 저장소엔 빈 기본값만 둔다 — 키가 비면 수집이 실패해 주말 회피 폴백으로 동작한다(CYCLE-01).
 *
 * @param baseUri    특일 API getRestDeInfo 엔드포인트
 * @param serviceKey 공공데이터포털 인증키(URL 인코딩된 일반 키). 미주입 시 빈 문자열
 */
@ConfigurationProperties("app.holiday")
public record HolidayProperties(String baseUri, String serviceKey) {}
