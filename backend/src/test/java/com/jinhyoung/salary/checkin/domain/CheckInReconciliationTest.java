package com.jinhyoung.salary.checkin.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** 월말 체크인 초과액 보정 산술(RPT-01) 단위 테스트 — overspend = toppedUp − livingRemaining. */
class CheckInReconciliationTest {

    @Test
    void 충당분이_남은_잔액보다_크면_초과다() {
        // 30,000 충당했는데 10,000만 남음 → 20,000 초과 사용.
        assertThat(CheckInReconciliation.overspend(10_000, 30_000)).isEqualTo(20_000);
    }

    @Test
    void 충당분과_남은_잔액이_같으면_정확히_0이다() {
        assertThat(CheckInReconciliation.overspend(30_000, 30_000)).isZero();
    }

    @Test
    void 남은_잔액이_충당분보다_크면_잉여라_음수다() {
        // 충당 없이 41,000 남음 → 41,000 잉여(덜 씀).
        assertThat(CheckInReconciliation.overspend(41_000, 0)).isEqualTo(-41_000);
    }

    @Test
    void 충당_없이_잔액도_0이면_정확히_맞춘_것이다() {
        assertThat(CheckInReconciliation.overspend(0, 0)).isZero();
    }
}
