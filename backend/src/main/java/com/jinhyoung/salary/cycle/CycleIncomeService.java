package com.jinhyoung.salary.cycle;

import com.jinhyoung.salary.common.ApiException;
import com.jinhyoung.salary.common.ErrorCode;
import com.jinhyoung.salary.cycle.domain.PlanLineType;
import com.jinhyoung.salary.cycle.infra.Cycle;
import com.jinhyoung.salary.cycle.infra.CycleRepository;
import com.jinhyoung.salary.cycle.infra.PlanLine;
import com.jinhyoung.salary.cycle.infra.PlanLineRepository;
import com.jinhyoung.salary.cycle.infra.PlanLineStatus;
import com.jinhyoung.salary.suggestion.SuggestionService;
import com.jinhyoung.salary.user.infra.User;
import com.jinhyoung.salary.user.infra.UserRepository;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 실수령액 확인(CYCLE-04, API명세 5장). 체크리스트 1단계에서 이번 사이클 실수령액을 확인·수정한다.
 * 기본값은 평소 실수령액(스냅샷 생성 시 {@code income=base_income}, {@code income_confirmed=false})이며,
 * 사용자가 확정하면 {@code income_confirmed=true}로 바뀌고 그 금액에 맞춰 LIVING 라인을 재계산한다.
 *
 * <p><b>LIVING 재계산(구현규칙 3장)</b>: 항목·봉투·EMERGENCY 라인은 사이클 스냅샷에 고정된 값이라 실수령액
 * 변동은 전부 생활비(LIVING)가 흡수한다. 따라서 새 LIVING 금액 = {@code income − Σ(LIVING 외 라인)}이다
 * (스냅샷 생성 시 빌더가 쓴 공식과 동일 — 폭포 캐스케이드를 다시 돌리지 않고 잔액만 재산정).
 *
 * <ul>
 *   <li>LIVING 라인이 <b>PENDING일 때만</b> 갱신한다. 이미 이체된(DONE) 라인은 건드리지 않는다(차액은 EXTRA
 *       라인 제안으로 — CYCLE-05/Phase 6 소관이라 여기서는 보존만).
 *   <li>재계산 결과가 0 이하(과배분/부족)면 LIVING 라인을 제거한다 — 이체할 돈이 없을 때 라인을 만들지 않는
 *       스냅샷 생성 의미론(FLOW-03)과 일치시킨다.
 *   <li>생활비 통장 미지정 등으로 LIVING 라인이 애초에 없으면 재계산 대상이 없다(실수령액·확인 플래그만 갱신).
 *       없던 LIVING 라인을 새로 만드는 건 "이번 달 반영" 재생성(ITEM-07) 소관이다.
 * </ul>
 */
@Service
public class CycleIncomeService {

    private final CycleRepository cycleRepository;
    private final PlanLineRepository planLineRepository;
    private final UserRepository userRepository;
    private final SuggestionService suggestionService;

    public CycleIncomeService(
            CycleRepository cycleRepository,
            PlanLineRepository planLineRepository,
            UserRepository userRepository,
            SuggestionService suggestionService) {
        this.cycleRepository = cycleRepository;
        this.planLineRepository = planLineRepository;
        this.userRepository = userRepository;
        this.suggestionService = suggestionService;
    }

    /**
     * 사이클의 실수령액을 확인·수정하고 LIVING 라인을 재계산한다.
     *
     * @param userId 인증 주체 — 본인 사이클만 다룬다(미소유·부재는 NOT_FOUND로 존재 비노출)
     * @param cycleId 대상 사이클
     * @param income 확인된 실수령액(검증은 컨트롤러 DTO에서 — 구현규칙 5장 1~10억)
     * @return 확인 반영된 사이클 헤더
     */
    @Transactional
    public Cycle confirmIncome(long userId, long cycleId, long income) {
        Cycle cycle = cycleRepository
                .findByIdAndUserId(cycleId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, Map.of("resource", "cycle", "id", cycleId)));

        cycle.confirmIncome(income);
        recalculateLivingLine(cycleId, income);

        // CYCLE-05: 확인 실수령액이 평소보다 기준 이상 크거나 작으면 여윳돈/부족 배분 제안을 만든다(advisory). 실제
        // 배분/축소 적용은 폭포 도메인(소유자) 후속이라 여기서는 제안 생성까지만 한다. 사이클당 dedup이라 재확인에 멱등.
        User user = userRepository
                .findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, Map.of("resource", "user", "id", userId)));
        suggestionService.generateWindfall(userId, cycleId, user.getBaseIncome(), income);
        return cycle;
    }

    /** 구현규칙 3장: LIVING = income − Σ(LIVING 외 라인). PENDING일 때만 갱신하고, 0 이하면 라인을 제거한다. */
    private void recalculateLivingLine(long cycleId, long income) {
        List<PlanLine> lines = planLineRepository.findByCycleIdOrderByIdAsc(cycleId);

        PlanLine living = lines.stream()
                .filter(line -> line.getLineType() == PlanLineType.LIVING)
                .findFirst()
                .orElse(null);
        if (living == null || living.getStatus() != PlanLineStatus.PENDING) {
            return;
        }

        long allocatedToOthers = lines.stream()
                .filter(line -> line.getLineType() != PlanLineType.LIVING)
                .mapToLong(PlanLine::getPlannedAmount)
                .sum();
        long newLiving = income - allocatedToOthers;

        if (newLiving > 0) {
            living.updatePlannedAmount(newLiving);
        } else {
            planLineRepository.delete(living);
        }
    }
}
