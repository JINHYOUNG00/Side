package com.jinhyoung.salary.envelope.domain;

/**
 * 봉투 지출 처리 산술(ENV-04, 구현규칙). 의존성 없는 순수 클래스 — 적립액(saved)과 실제 지출액(actual)만으로
 * 부족분·잉여분과 지출 후 남는 적립액을 계산한다(규칙 9, 금액은 long 원 단위 — 규칙 2).
 *
 * <p>의미론:
 *
 * <ul>
 *   <li><b>부족</b>(actual &gt; saved) — 모자란 만큼은 충당 출처(생활비/비상금)에서 메우고 봉투는 비워진다(0).
 *   <li><b>정확</b>(actual == saved) — 적립분을 전부 쓰고 봉투는 비워진다(0).
 *   <li><b>잉여</b>(actual &lt; saved) — 남는 만큼을 이월(carryOver=true, 다음 지출까지 적립 유지)하거나 회수
 *       (carryOver=false, 봉투를 비움)한다.
 * </ul>
 *
 * <p>반복형 봉투의 다음 지출일 이동·적립 재시작과 일회성 종료는 ENV-05 소관이라 여기서 다루지 않는다 — 이 클래스는
 * "한 번의 지출이 적립 캐시를 어떻게 바꾸는가"만 답한다.
 */
public final class EnvelopeSpend {

    private EnvelopeSpend() {}

    /** 부족분 = max(0, actual − saved). 충당 출처로 메워야 하는 금액. */
    public static long shortfall(long savedAmount, long actualAmount) {
        return Math.max(0L, actualAmount - savedAmount);
    }

    /** 잉여분 = max(0, saved − actual). 이월 또는 회수 대상 금액. */
    public static long surplus(long savedAmount, long actualAmount) {
        return Math.max(0L, savedAmount - actualAmount);
    }

    /**
     * 지출 후 봉투에 남는 적립액. 부족·정확이면 소진되어 0이다. 잉여가 있을 때만 {@code carryOver}가 의미를 가져,
     * 이월이면 잉여분을 그대로 남기고 회수면 0으로 비운다.
     */
    public static long savedAfterSpend(long savedAmount, long actualAmount, boolean carryOver) {
        long surplus = surplus(savedAmount, actualAmount);
        if (surplus == 0L) {
            return 0L;
        }
        return carryOver ? surplus : 0L;
    }
}
