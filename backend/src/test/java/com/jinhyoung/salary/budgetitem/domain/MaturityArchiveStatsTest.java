package com.jinhyoung.salary.budgetitem.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 보관함 누적 통계 집계(ITEM-08)의 순수 단위 테스트. {@link MaturityArchiveStats#from}은 실수령액 목록만으로
 * 결정론적으로 동작한다 — Spring·DB 없이 미기록(null) 제외·누적 합산을 고정 검증한다(규칙 2 long 합산).
 */
class MaturityArchiveStatsTest {

    @Test
    void 모두_기록된_경우_보관_건수와_기록_건수가_같고_누적액은_합이다() {
        MaturityArchiveStats stats = MaturityArchiveStats.from(List.of(1_000_000L, 3_731_976L, 500_000L));

        assertThat(stats.archivedCount()).isEqualTo(3);
        assertThat(stats.recordedCount()).isEqualTo(3);
        assertThat(stats.totalReceivedAmount()).isEqualTo(5_231_976L);
    }

    @Test
    void 미기록_항목은_누적액에서_제외되고_보관_건수에만_포함된다() {
        MaturityArchiveStats stats = MaturityArchiveStats.from(Arrays.asList(1_000_000L, null, 500_000L));

        assertThat(stats.archivedCount()).isEqualTo(3);
        assertThat(stats.recordedCount()).isEqualTo(2);
        assertThat(stats.totalReceivedAmount()).isEqualTo(1_500_000L);
    }

    @Test
    void 전부_미기록이면_누적액과_기록_건수가_0이고_보관_건수만_남는다() {
        MaturityArchiveStats stats = MaturityArchiveStats.from(Arrays.asList(null, null));

        assertThat(stats.archivedCount()).isEqualTo(2);
        assertThat(stats.recordedCount()).isZero();
        assertThat(stats.totalReceivedAmount()).isZero();
    }

    @Test
    void 빈_목록이면_모두_0이다() {
        MaturityArchiveStats stats = MaturityArchiveStats.from(List.of());

        assertThat(stats.archivedCount()).isZero();
        assertThat(stats.recordedCount()).isZero();
        assertThat(stats.totalReceivedAmount()).isZero();
    }
}
