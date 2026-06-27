package com.jinhyoung.salary.report.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 추이 점 산정(RPT-02) 단위 테스트 — 의존성 없는 순수 계산. 체크인 초과액으로부터 실제 소진액을 도출하고,
 * 체크인이 없으면 결측으로 구분하는지 검증한다.
 */
class ReportTrendPointTest {

    @Test
    void 초과시_실제가_계획보다_크다() {
        // 계획 375,000에 초과액 12,000 → 실제 387,000(더 씀).
        ReportTrendPoint point = ReportTrendPoint.of("2026-05", 375_000L, 12_000L);

        assertThat(point.label()).isEqualTo("2026-05");
        assertThat(point.planned()).isEqualTo(375_000L);
        assertThat(point.actual()).isEqualTo(387_000L);
        assertThat(point.checkedIn()).isTrue();
    }

    @Test
    void 잉여시_실제가_계획보다_작다() {
        // 초과액 음수(-41,000)는 잉여 → 실제 = 375,000 − 41,000 = 334,000(덜 씀).
        ReportTrendPoint point = ReportTrendPoint.of("2026-04", 375_000L, -41_000L);

        assertThat(point.actual()).isEqualTo(334_000L);
        assertThat(point.checkedIn()).isTrue();
    }

    @Test
    void 정확히_맞으면_실제가_계획과_같다() {
        // 초과액 0 → 계획만큼 정확히 소진.
        ReportTrendPoint point = ReportTrendPoint.of("2026-03", 375_000L, 0L);

        assertThat(point.actual()).isEqualTo(375_000L);
        assertThat(point.checkedIn()).isTrue();
    }

    @Test
    void 체크인_미수행이면_실제는_결측이다() {
        // overspend null = 결측(체크인 없음) → actual은 측정 불가(null), checkedIn=false.
        ReportTrendPoint point = ReportTrendPoint.of("2026-02", 375_000L, null);

        assertThat(point.planned()).isEqualTo(375_000L);
        assertThat(point.actual()).isNull();
        assertThat(point.checkedIn()).isFalse();
    }
}
