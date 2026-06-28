package com.jinhyoung.salary.suggestion;

import com.jinhyoung.salary.common.ApiException;
import com.jinhyoung.salary.common.ErrorCode;
import com.jinhyoung.salary.cycle.domain.PlanLineType;
import com.jinhyoung.salary.cycle.domain.WindfallAllocation;
import com.jinhyoung.salary.cycle.infra.Cycle;
import com.jinhyoung.salary.cycle.infra.CycleRepository;
import com.jinhyoung.salary.cycle.infra.PlanLine;
import com.jinhyoung.salary.cycle.infra.PlanLineRepository;
import com.jinhyoung.salary.cycle.infra.PlanLineStatus;
import com.jinhyoung.salary.suggestion.domain.SuggestionType;
import com.jinhyoung.salary.suggestion.infra.Suggestion;
import com.jinhyoung.salary.suggestion.infra.SuggestionRepository;
import com.jinhyoung.salary.suggestion.infra.SuggestionStatus;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 여윳돈/부족 제안의 인터랙티브 배분 적용(CYCLE-05). 사용자가 고른 plan_line별 배분/축소액을 받아 이번 사이클의
 * 계획(plan_lines)을 실제로 조정하고 제안을 APPLIED로 닫는다. 산술은 순수 {@link WindfallAllocation}이 맡고, 이
 * 서비스는 소유권·상태 게이트와 영속화를 담당한다.
 *
 * <p><b>경계</b>: 이번 사이클의 plan_lines(체크리스트)만 바꾼다 — 일회성 차액이라 반복 budget_items(다음 사이클
 * 계획)는 건드리지 않는다. <b>불변 스냅샷</b>(규칙 4): PENDING 라인만 조정하고 이미 이체된(DONE)·건너뛴(SKIPPED)
 * 라인은 손대지 않는다. LIVING 라인은 차액의 상대편이라 직접 대상으로 지정할 수 없다.
 */
@Service
public class WindfallAllocationService {

    private final SuggestionRepository suggestionRepository;
    private final CycleRepository cycleRepository;
    private final PlanLineRepository planLineRepository;
    private final Clock clock;

    public WindfallAllocationService(
            SuggestionRepository suggestionRepository,
            CycleRepository cycleRepository,
            PlanLineRepository planLineRepository,
            Clock clock) {
        this.suggestionRepository = suggestionRepository;
        this.cycleRepository = cycleRepository;
        this.planLineRepository = planLineRepository;
        this.clock = clock;
    }

    /** 한 대상 plan_line에 적용할 배분(WINDFALL)·축소(SHORTFALL) 금액. */
    public record Allocation(long planLineId, long amount) {}

    /**
     * 여윳돈/부족 제안을 배분 적용한다(CYCLE-05). 제안은 호출 사용자의 PENDING WINDFALL/SHORTFALL이어야 하며,
     * 대상 라인은 그 제안 사이클의 PENDING·비-LIVING 라인이어야 한다. 검증 통과 시 대상·LIVING 계획액을 조정하고
     * 제안을 APPLIED로 닫는다. 위반은 모두 코드 기반 예외(NOT_FOUND/409/VALIDATION_FAILED)로 알린다.
     *
     * @return APPLIED로 닫힌 제안
     */
    @Transactional
    public Suggestion allocate(long userId, long suggestionId, List<Allocation> allocations) {
        Suggestion suggestion = suggestionRepository
                .findByIdAndUserId(suggestionId, userId)
                .orElseThrow(() ->
                        new ApiException(ErrorCode.NOT_FOUND, Map.of("resource", "suggestion", "id", suggestionId)));
        if (suggestion.getStatus() != SuggestionStatus.PENDING) {
            throw new ApiException(ErrorCode.SUGGESTION_ALREADY_RESOLVED, Map.of("id", suggestionId));
        }
        SuggestionType type = suggestion.getType();
        if (type != SuggestionType.WINDFALL && type != SuggestionType.SHORTFALL) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, Map.of("reason", "not an allocatable suggestion"));
        }

        long cycleId = ((Number) suggestion.getPayload().get("cycleId")).longValue();
        long difference = ((Number) suggestion.getPayload().get("difference")).longValue();
        // 사이클 소유권 재확인(payload는 신뢰하되 소유권은 쿼리로 강제).
        Cycle cycle = cycleRepository
                .findByIdAndUserId(cycleId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, Map.of("resource", "cycle", "id", cycleId)));

        List<PlanLine> lines = planLineRepository.findByCycleIdOrderByIdAsc(cycle.getId());
        Map<Long, PlanLine> byId = lines.stream().collect(Collectors.toMap(PlanLine::getId, Function.identity()));
        PlanLine living = lines.stream()
                .filter(line -> line.getLineType() == PlanLineType.LIVING && line.getStatus() == PlanLineStatus.PENDING)
                .findFirst()
                .orElse(null);

        List<PlanLine> targets = new ArrayList<>();
        List<WindfallAllocation.Line> domainLines = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (Allocation allocation : allocations) {
            if (!seen.add(allocation.planLineId())) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, Map.of("reason", "duplicate line"));
            }
            PlanLine target = byId.get(allocation.planLineId());
            if (target == null) {
                throw new ApiException(
                        ErrorCode.NOT_FOUND, Map.of("resource", "planLine", "id", allocation.planLineId()));
            }
            if (target.getLineType() == PlanLineType.LIVING) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, Map.of("reason", "living is not a target"));
            }
            if (target.getStatus() != PlanLineStatus.PENDING) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, Map.of("reason", "line not pending"));
            }
            targets.add(target);
            domainLines.add(new WindfallAllocation.Line(target.getPlannedAmount(), allocation.amount()));
        }

        WindfallAllocation.Result result;
        try {
            if (type == SuggestionType.WINDFALL) {
                if (living == null) {
                    throw new ApiException(
                            ErrorCode.VALIDATION_FAILED, Map.of("reason", "no living line to draw from"));
                }
                result = WindfallAllocation.distributeWindfall(difference, living.getPlannedAmount(), domainLines);
            } else {
                Long livingPlanned = living == null ? null : living.getPlannedAmount();
                result = WindfallAllocation.coverShortfall(difference, livingPlanned, domainLines);
            }
        } catch (IllegalArgumentException e) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, Map.of("reason", e.getMessage()));
        }

        for (int i = 0; i < targets.size(); i++) {
            targets.get(i).updatePlannedAmount(result.newTargetAmounts().get(i));
        }
        if (result.newLiving() != null && living != null) {
            living.updatePlannedAmount(result.newLiving());
        }
        suggestion.apply(OffsetDateTime.now(clock));
        return suggestion;
    }
}
