package com.jinhyoung.salary.report;

import com.jinhyoung.salary.report.domain.ReportTrendPoint;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 추이·요약 리포트(RPT-02, API명세 6장). 인증 필수 — principal=userId(JwtAuthenticationFilter)로 본인 데이터만
 * 조회한다(조회 전용). 체크인 기록(POST /check-ins)은 RPT-01의 {@code CheckInController} 소관이다.
 */
@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    /** 추이 기본 조회 개월(= 사이클 수). API명세 {@code ?months=6}. */
    private static final String DEFAULT_MONTHS = "6";

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    /**
     * 사이클별 계획 vs 실제 추이(RPT-02). {@code months}는 최근 사이클 수(기본 6). 범위 밖이면 400
     * VALIDATION_FAILED. 시간순(오래된→최근)으로 정렬돼 차트가 바로 그릴 수 있다.
     */
    @GetMapping("/trend")
    public List<ReportTrendPoint> trend(
            @AuthenticationPrincipal Long userId, @RequestParam(defaultValue = DEFAULT_MONTHS) int months) {
        return reportService.trend(userId, months);
    }

    /** 저축률·만기 수령 누적·봉투 집행 합계 요약(RPT-02). */
    @GetMapping("/summary")
    public ReportSummary summary(@AuthenticationPrincipal Long userId) {
        return reportService.summary(userId);
    }

    /**
     * 연 단위 결산(RPT-04) — 그 해의 저축률·만기 수령 누적·봉투 집행을 집계한다. {@code year}는 필수이며
     * 범위(2000~현재 연도+1) 밖이면 400 VALIDATION_FAILED. 조회 전용·본인 데이터만.
     */
    @GetMapping("/annual")
    public AnnualReport annual(@AuthenticationPrincipal Long userId, @RequestParam int year) {
        return reportService.annual(userId, year);
    }
}
