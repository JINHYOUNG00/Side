package com.jinhyoung.salary.budgetitem.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/**
 * ExpectedMaturity 단위 테스트(ITEM-05/06) — 수동 입력값 우선, SAVING 공식 계산, 계산 불가 케이스(null),
 * 개월 수 도출(end-inclusive). 순수 JUnit/assertj만 사용(도메인 순수성 유지).
 */
class ExpectedMaturityTest {

    private static final BigDecimal RATE_8 = new BigDecimal("8.0");

    @Test
    void 수동_입력값이_있으면_공식_대신_그_값을_쓴다() {
        // ITEM-06: 특수 상품(청년도약 등). 이율·만기일이 있어도 수동값이 우선이다.
        Long result = ExpectedMaturity.resolve(
                Category.SAVING,
                700_000,
                RATE_8,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2030, 12, 31),
                TaxType.NORMAL_15_4,
                5_000_000L);

        assertThat(result).isEqualTo(5_000_000L);
    }

    @Test
    void 비저축_항목은_수동값이_없으면_null() {
        Long result = ExpectedMaturity.resolve(
                Category.FIXED,
                300_000,
                RATE_8,
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2027, 6, 30),
                TaxType.NORMAL_15_4,
                null);

        assertThat(result).isNull();
    }

    @Test
    void 저축_항목은_단리_공식으로_계산한다_골든값_재현() {
        // 골든 적금A: 월 30만 · 연 8% · 12개월 · 일반과세 → 3,731,976. 만기일을 마지막 날로 두면 12개월(end-inclusive).
        Long result = ExpectedMaturity.resolve(
                Category.SAVING,
                300_000,
                RATE_8,
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2027, 6, 30),
                TaxType.NORMAL_15_4,
                null);

        assertThat(result).isEqualTo(3_731_976L);
    }

    @Test
    void 만기일을_다음_사이클_첫날로_둬도_같은_개월수로_계산된다() {
        // 만기일 2027-07-01(시작+12개월)도 end-inclusive로 12개월 → 골든값과 동일.
        Long result = ExpectedMaturity.resolve(
                Category.SAVING,
                300_000,
                RATE_8,
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2027, 7, 1),
                TaxType.NORMAL_15_4,
                null);

        assertThat(result).isEqualTo(3_731_976L);
    }

    @Test
    void 세금우대는_농특세_1_4퍼센트만_적용한다() {
        // 월 10만 · 8% · 12개월: 이자 52,000, 세금 52,000×0.014=728 → total 1,251,272.
        Long result = ExpectedMaturity.resolve(
                Category.SAVING,
                100_000,
                RATE_8,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
                TaxType.PREFERENTIAL,
                null);

        assertThat(result).isEqualTo(1_251_272L);
    }

    @Test
    void 이율이_없으면_계산_불가로_null() {
        Long result = ExpectedMaturity.resolve(
                Category.SAVING,
                300_000,
                null,
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2027, 6, 30),
                TaxType.NORMAL_15_4,
                null);

        assertThat(result).isNull();
    }

    @Test
    void 만기일이_없으면_계산_불가로_null() {
        Long result = ExpectedMaturity.resolve(
                Category.SAVING, 300_000, RATE_8, LocalDate.of(2026, 7, 1), null, TaxType.NORMAL_15_4, null);

        assertThat(result).isNull();
    }

    @Test
    void 구간이_1개월_미만이면_null() {
        // 시작 2026-07-01, 만기 2026-07-15 → end-inclusive 0개월 → 계산 안 함.
        Long result = ExpectedMaturity.resolve(
                Category.SAVING,
                300_000,
                RATE_8,
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 15),
                TaxType.NORMAL_15_4,
                null);

        assertThat(result).isNull();
    }
}
