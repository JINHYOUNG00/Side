package com.jinhyoung.salary.report;

import com.jinhyoung.salary.budgetitem.domain.MaturityArchiveStats;
import com.jinhyoung.salary.budgetitem.infra.BudgetItem;
import com.jinhyoung.salary.budgetitem.infra.BudgetItemRepository;
import com.jinhyoung.salary.budgetitem.infra.ItemStatus;
import com.jinhyoung.salary.checkin.infra.CheckIn;
import com.jinhyoung.salary.checkin.infra.CheckInRepository;
import com.jinhyoung.salary.common.ApiException;
import com.jinhyoung.salary.common.ErrorCode;
import com.jinhyoung.salary.cycle.WaterfallQueryService;
import com.jinhyoung.salary.cycle.domain.PlanLineType;
import com.jinhyoung.salary.cycle.domain.SavingsRate;
import com.jinhyoung.salary.cycle.infra.Cycle;
import com.jinhyoung.salary.cycle.infra.CycleRepository;
import com.jinhyoung.salary.cycle.infra.PlanLine;
import com.jinhyoung.salary.cycle.infra.PlanLineRepository;
import com.jinhyoung.salary.envelope.infra.EnvelopeTransactionRepository;
import com.jinhyoung.salary.report.domain.ReportTrendPoint;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 추이·요약 리포트 조립(RPT-02, API명세 6장). 이 클래스는 <b>새로 계산하지 않는다</b> — 사이클별 실제 소진액은
 * 순수 {@link ReportTrendPoint}가, 저축률은 {@link SavingsRate}(SET-02)가, 만기 수령 누적은
 * {@link MaturityArchiveStats}(ITEM-08)가 산정한다. 여기서는 사용자 소유 데이터를 모아 도메인에 넘기고 응답을
 * 조립할 뿐이다(조회 전용).
 *
 * <p>모든 조회는 호출 사용자(userId)로 한정한다 — 소유권은 리포지토리 쿼리가 user_id로 강제한다(아키텍처 8장).
 */
@Service
public class ReportService {

    /** 추이 리포트 최대 조회 개월(= 사이클 수) — 차트·쿼리 보호용 상한. */
    static final int MAX_MONTHS = 36;

    private final CycleRepository cycleRepository;
    private final PlanLineRepository planLineRepository;
    private final CheckInRepository checkInRepository;
    private final BudgetItemRepository budgetItemRepository;
    private final EnvelopeTransactionRepository envelopeTransactionRepository;
    private final WaterfallQueryService waterfallQueryService;

    public ReportService(
            CycleRepository cycleRepository,
            PlanLineRepository planLineRepository,
            CheckInRepository checkInRepository,
            BudgetItemRepository budgetItemRepository,
            EnvelopeTransactionRepository envelopeTransactionRepository,
            WaterfallQueryService waterfallQueryService) {
        this.cycleRepository = cycleRepository;
        this.planLineRepository = planLineRepository;
        this.checkInRepository = checkInRepository;
        this.budgetItemRepository = budgetItemRepository;
        this.envelopeTransactionRepository = envelopeTransactionRepository;
        this.waterfallQueryService = waterfallQueryService;
    }

    /**
     * 사이클별 계획 vs 실제 추이(RPT-02). 최근 {@code months}개 사이클을 시간순(오래된→최근)으로 돌려준다.
     * 계획은 각 사이클의 LIVING(생활비) 계획액, 실제는 체크인 초과액으로 도출하며, 체크인이 없는 사이클은 결측
     * (actual=null, checkedIn=false)으로 구분한다. 사이클이 없으면 빈 목록(신규 사용자 빈 상태, RPT-03).
     *
     * @param months 조회 개월(사이클 수). 1~{@value #MAX_MONTHS} 밖이면 400 VALIDATION_FAILED
     */
    @Transactional(readOnly = true)
    public List<ReportTrendPoint> trend(long userId, int months) {
        if (months < 1 || months > MAX_MONTHS) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, Map.of("param", "months", "value", months));
        }

        List<Cycle> recent = cycleRepository.findByUserIdOrderByCycleStartDesc(userId, PageRequest.of(0, months));
        if (recent.isEmpty()) {
            return List.of();
        }
        List<Long> cycleIds = recent.stream().map(Cycle::getId).toList();

        // 사이클별 LIVING 계획액(보통 1건이지만 사이클 기준으로 합산).
        Map<Long, Long> plannedByCycle =
                planLineRepository.findByCycleIdInAndLineType(cycleIds, PlanLineType.LIVING).stream()
                        .collect(Collectors.groupingBy(
                                PlanLine::getCycleId, Collectors.summingLong(PlanLine::getPlannedAmount)));

        // 사이클별 초과액(체크인). 없으면 결측 — 맵에 키가 없어 null로 빠진다.
        Map<Long, Long> overspendByCycle = checkInRepository.findByCycleIdIn(cycleIds).stream()
                .collect(Collectors.toMap(CheckIn::getCycleId, CheckIn::getOverspend));

        // 조회는 내림차순 → 차트용으로 시간순(오래된→최근)으로 뒤집는다.
        List<Cycle> chronological = new ArrayList<>(recent);
        Collections.reverse(chronological);
        return chronological.stream()
                .map(cycle -> ReportTrendPoint.of(
                        cycle.getLabel(),
                        plannedByCycle.getOrDefault(cycle.getId(), 0L),
                        overspendByCycle.get(cycle.getId())))
                .toList();
    }

    /**
     * 리포트 요약(RPT-02) — 저축률·만기 수령 누적·봉투 집행 합계. 각 메트릭은 기존 순수 도메인을 재사용한다(중복
     * 정의 방지): 저축률은 폭포 조립({@link WaterfallQueryService})의 산정값을, 만기는 보관 항목 실수령액을
     * {@link MaturityArchiveStats}로, 봉투 집행은 SPEND 실제 지출액 합으로 집계한다.
     */
    @Transactional(readOnly = true)
    public ReportSummary summary(long userId) {
        SavingsRate savingsRate = waterfallQueryService.getWaterfall(userId).savingsRate();

        List<BudgetItem> archived =
                budgetItemRepository.findByUserIdAndStatusOrderBySortOrderAsc(userId, ItemStatus.ARCHIVED);
        MaturityArchiveStats maturity = MaturityArchiveStats.from(
                archived.stream().map(BudgetItem::getMaturityActualAmount).toList());

        long envelopeSpentTotal = envelopeTransactionRepository.sumSpentActualByUserId(userId);

        return new ReportSummary(savingsRate, maturity, envelopeSpentTotal);
    }
}
