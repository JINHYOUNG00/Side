package com.jinhyoung.salary.suggestion.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 계획 보정·만기 리밸런싱 제안 룰(SUG-01·SUG-02). 의존성 없는 순수 클래스(규칙 9) — 입력(닫힌 사이클 결과·만기
 * 후보·운영 상수·기존 PENDING 키)만으로 제안 초안을 산출하고, DB·시계·프레임워크에 의존하지 않는다(기준일 {@code today}는
 * 호출 측이 주입된 KST {@code Clock}으로 산출해 넘긴다 — 규칙 3). 금액은 long 원 단위(규칙 2).
 *
 * <p>초기 룰은 구현규칙 7장을 그대로 옮긴 것이다:
 *
 * <ul>
 *   <li><b>RAISE_LIVING</b> — 직전 {@code streak}개 사이클이 모두 체크인이 있고 연속 {@code overspend > 0}이면,
 *       평균 초과액을 {@value #ROUNDING_UNIT}원 단위로 <b>올림</b>한 만큼 생활비 증액을 제안한다.
 *   <li><b>RAISE_SAVING</b> — 직전 {@code streak}개 사이클이 연속 {@code overspend ≤ −surplusThreshold}(잉여)이면,
 *       평균 잉여를 {@value #ROUNDING_UNIT}원 단위로 <b>내림</b>한 만큼 저축 증액을 제안한다. 내림 결과가 0이면
 *       생성하지 않는다.
 *   <li><b>REBALANCE_MATURITY</b> — 만기일 {@value #MATURITY_LEAD_DAYS}일 전이 도래한 항목마다 1회 제안한다.
 * </ul>
 *
 * <p><b>단절(break) 의미론</b>: streak은 가장 최근 {@code streak}개만 본다. 그 안에 결측(체크인 없음)이나 조건
 * 미달 사이클이 하나라도 있으면 발동하지 않는다 — 결측을 건너뛰고 더 과거를 끌어오지 않는다(구현규칙 7장 "제외가 아니라
 * 단절"). 입력 {@code recentClosedDesc}는 닫힌 사이클을 최신→과거 순으로 담는다.
 *
 * <p><b>중복 방지</b>: 같은 {@code dedupKey}의 PENDING 제안이 이미 있으면 새로 만들지 않는다. RAISE_*는 사용자당
 * 하나라 키가 type 이름이고, REBALANCE_MATURITY는 항목마다 별개라 {@code type + ":" + itemId}다.
 */
public final class SuggestionRule {

    /** 제안 금액 반올림 단위(원) — 구현규칙 7장 "10,000원 단위". */
    static final long ROUNDING_UNIT = 10_000L;

    /** 만기 리밸런싱 도래 기준(만기 며칠 전) — 구현규칙 7장 "end_date − 30일". */
    static final int MATURITY_LEAD_DAYS = 30;

    private SuggestionRule() {}

    /**
     * 생활비 증액 제안(RAISE_LIVING, SUG-02). 최근 {@code streak}개 사이클이 모두 체크인 존재 + {@code overspend > 0}
     * (연속 초과)일 때만 발동한다 — 결측·미달이 끼면 단절로 미발동. 제안액은 평균 초과액을 {@value #ROUNDING_UNIT}원
     * 단위로 올린 값(초과는 넉넉히 메우도록 올림).
     *
     * @param recentClosedDesc 닫힌 사이클 결과(최신→과거 순)
     * @param streak 발동 연속 사이클 수(app.policy overspend-streak, 보통 3)
     * @param existingPendingKeys 이미 PENDING인 dedup 키 집합(중복 방지)
     * @return 발동 시 제안 1건, 미발동/중복 시 {@link Optional#empty()}
     */
    public static Optional<SuggestionDraft> raiseLiving(
            List<CheckInOutcome> recentClosedDesc, int streak, Set<String> existingPendingKeys) {
        String key = SuggestionType.RAISE_LIVING.name();
        if (existingPendingKeys.contains(key) || recentClosedDesc.size() < streak) {
            return Optional.empty();
        }
        long sum = 0;
        for (int i = 0; i < streak; i++) {
            Long overspend = recentClosedDesc.get(i).overspend();
            if (overspend == null || overspend <= 0) {
                return Optional.empty(); // 결측 또는 초과 아님 = 단절
            }
            sum += overspend;
        }
        long avgOverspend = sum / streak;
        long suggestedIncrease = ceilTo(avgOverspend, ROUNDING_UNIT);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("suggestedIncrease", suggestedIncrease);
        payload.put("avgOverspend", avgOverspend);
        payload.put("streak", streak);
        return Optional.of(new SuggestionDraft(SuggestionType.RAISE_LIVING, key, payload));
    }

    /**
     * 저축 증액 제안(RAISE_SAVING, SUG-02). 최근 {@code streak}개 사이클이 모두 체크인 존재 +
     * {@code overspend ≤ −surplusThreshold}(연속 잉여)일 때만 발동한다 — 결측·미달이 끼면 단절. 제안액은 평균 잉여를
     * {@value #ROUNDING_UNIT}원 단위로 내린 값(저축은 보수적으로 증액). 내림 결과가 0이면 무의미하므로 생성하지 않는다.
     *
     * @param recentClosedDesc 닫힌 사이클 결과(최신→과거 순)
     * @param streak 발동 연속 사이클 수(app.policy overspend-streak, 보통 3)
     * @param surplusThreshold 잉여 기준액(app.policy surplus-threshold, 보통 30,000원). 잉여 = −overspend
     * @param existingPendingKeys 이미 PENDING인 dedup 키 집합(중복 방지)
     * @return 발동 시 제안 1건, 미발동/중복/제안액 0이면 {@link Optional#empty()}
     */
    public static Optional<SuggestionDraft> raiseSaving(
            List<CheckInOutcome> recentClosedDesc, int streak, long surplusThreshold, Set<String> existingPendingKeys) {
        String key = SuggestionType.RAISE_SAVING.name();
        if (existingPendingKeys.contains(key) || recentClosedDesc.size() < streak) {
            return Optional.empty();
        }
        long surplusSum = 0;
        for (int i = 0; i < streak; i++) {
            Long overspend = recentClosedDesc.get(i).overspend();
            if (overspend == null || overspend > -surplusThreshold) {
                return Optional.empty(); // 결측 또는 잉여 기준 미달 = 단절
            }
            surplusSum += -overspend; // 잉여는 양수
        }
        long avgSurplus = surplusSum / streak;
        long suggestedIncrease = floorTo(avgSurplus, ROUNDING_UNIT);
        if (suggestedIncrease <= 0) {
            return Optional.empty(); // 내림 결과 0 — 증액 0은 무의미
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("suggestedIncrease", suggestedIncrease);
        payload.put("avgSurplus", avgSurplus);
        payload.put("streak", streak);
        return Optional.of(new SuggestionDraft(SuggestionType.RAISE_SAVING, key, payload));
    }

    /**
     * 만기 리밸런싱 제안(REBALANCE_MATURITY, SUG-01). 만기일 {@value #MATURITY_LEAD_DAYS}일 전이 도래한
     * ({@code today ≥ maturityDate − 30일}) 항목마다 1건씩 제안한다 — 항목별 dedup(type+itemId)로 한 번만 만든다.
     * 만기가 지나면 항목이 보관(ARCHIVED)으로 빠져 입력에서 사라지므로 자연히 멈춘다.
     *
     * @param activeMaturing 만기일 보유 활성 항목 후보
     * @param today 기준일(주입된 KST Clock 산출값)
     * @param existingPendingKeys 이미 PENDING인 dedup 키 집합(중복 방지)
     * @return 도래·미중복 항목들의 제안 목록(없으면 빈 목록)
     */
    public static List<SuggestionDraft> rebalanceMaturity(
            List<MaturingItem> activeMaturing, LocalDate today, Set<String> existingPendingKeys) {
        List<SuggestionDraft> drafts = new ArrayList<>();
        for (MaturingItem item : activeMaturing) {
            LocalDate threshold = item.maturityDate().minusDays(MATURITY_LEAD_DAYS);
            if (today.isBefore(threshold)) {
                continue; // 아직 도래 전
            }
            String key = SuggestionType.REBALANCE_MATURITY.name() + ":" + item.itemId();
            if (existingPendingKeys.contains(key)) {
                continue; // 이미 제안됨
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("itemId", item.itemId());
            payload.put("itemName", item.itemName());
            payload.put("monthlyAmount", item.monthlyAmount());
            payload.put("maturityDate", item.maturityDate().toString());
            if (item.expectedMaturityAmount() != null) {
                payload.put("expectedMaturityAmount", item.expectedMaturityAmount());
            }
            drafts.add(new SuggestionDraft(SuggestionType.REBALANCE_MATURITY, key, payload));
        }
        return drafts;
    }

    /**
     * 여윳돈/부족 배분 제안(CYCLE-05). 확인된 실수령액이 평소 금액(baseIncome)보다 {@code windfallThreshold} 이상
     * 많으면 {@link SuggestionType#WINDFALL}(여윳돈 배분 검토), 그만큼 적으면 {@link SuggestionType#SHORTFALL}
     * (축소 대상 검토)을 제안한다. 차이가 임계 미만이면 제안하지 않는다. 제안은 사이클당 1건으로 dedup
     * (type+":"+cycleId) — 같은 사이클 실수령액을 다시 확인해도 중복 생성하지 않는다.
     *
     * <p>실제 배분/축소 적용(차액을 어느 항목에 넣고 뺄지)은 폭포 도메인(소유자 영역)에 닿는 후속 작업이며, 이 제안은
     * 차액 크기를 알리고 배분처 검토로 안내하는 advisory다(SUG 반영과 동일 경계).
     *
     * @param cycleId 대상 사이클(dedup·payload 키)
     * @param baseIncome 평소 실수령액(users.base_income)
     * @param confirmedIncome 이번 사이클 확인 실수령액(CYCLE-04)
     * @param windfallThreshold 발동 기준액(app.policy windfall-threshold, 보통 30,000원)
     * @param existingPendingKeys 이미 PENDING인 dedup 키 집합(중복 방지)
     * @return 여윳돈/부족 발동 시 제안 1건, 임계 미만/중복이면 {@link Optional#empty()}
     */
    public static Optional<SuggestionDraft> windfall(
            long cycleId,
            long baseIncome,
            long confirmedIncome,
            long windfallThreshold,
            Set<String> existingPendingKeys) {
        long difference = confirmedIncome - baseIncome;
        if (Math.abs(difference) < windfallThreshold) {
            return Optional.empty(); // 평소와 비슷 — 배분/축소 검토 불요
        }
        SuggestionType type = difference > 0 ? SuggestionType.WINDFALL : SuggestionType.SHORTFALL;
        String key = type.name() + ":" + cycleId;
        if (existingPendingKeys.contains(key)) {
            return Optional.empty();
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("cycleId", cycleId);
        payload.put("difference", Math.abs(difference)); // 크기(원) — 방향은 type이 구분
        payload.put("baseIncome", baseIncome);
        payload.put("confirmedIncome", confirmedIncome);
        return Optional.of(new SuggestionDraft(type, key, payload));
    }

    /** {@code value}를 {@code unit} 단위로 올림(value ≤ 0이면 0). 양의 평균 초과액 가정. */
    private static long ceilTo(long value, long unit) {
        if (value <= 0) {
            return 0;
        }
        return ((value + unit - 1) / unit) * unit;
    }

    /** {@code value}를 {@code unit} 단위로 내림(value ≤ 0이면 0). */
    private static long floorTo(long value, long unit) {
        if (value <= 0) {
            return 0;
        }
        return (value / unit) * unit;
    }
}
