package com.jinhyoung.salary.cycle;

import com.jinhyoung.salary.common.ApiException;
import com.jinhyoung.salary.common.ErrorCode;
import com.jinhyoung.salary.cycle.domain.PlanLineType;
import com.jinhyoung.salary.cycle.infra.Cycle;
import com.jinhyoung.salary.cycle.infra.CycleRepository;
import com.jinhyoung.salary.cycle.infra.PlanLine;
import com.jinhyoung.salary.cycle.infra.PlanLineRepository;
import com.jinhyoung.salary.cycle.infra.PlanLineStatus;
import com.jinhyoung.salary.envelope.EnvelopeService;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 통장별 체크리스트 조회·상태 전이(CYCLE-06, API명세 5장).
 *
 * <ul>
 *   <li>{@code GET /cycles/current} — 오늘이 속한 사이클의 plan_lines를 이체 대상 통장({@code account_id})별로
 *       묶은 체크리스트 + 진행도. 미생성이면 NOT_FOUND(스냅샷 생성 동선).
 *   <li>{@code PATCH /plan-lines/{id}} — 라인 상태 전이 PENDING ↔ DONE/SKIPPED. 본인 사이클의 라인만 다루고
 *       (미소유·부재는 NOT_FOUND로 비노출), 지난 사이클(cycle_end 경과)은 과거 불변이라 잠근다(구현규칙 2장).
 * </ul>
 *
 * <p>봉투(ENVELOPE) 라인의 적립 연동(CYCLE-07, 구현규칙 2장): 라인이 DONE으로 전이되면 그 봉투에 적립
 * (DEPOSIT) 1건을 남기고 saved_amount를 늘리며, DONE에서 해제되면 그 사이클의 적립을 회수한다. 단 이미 SPEND가
 * 있으면 해제할 수 없다(409 LINE_LOCKED_BY_SPEND). 적립 부수효과 자체는 봉투 도메인({@link EnvelopeService})에
 * 위임한다 — 여기선 "DONE 경계"를 판정해 호출만 한다. ITEM·LIVING 라인 전이는 상태·처리 시각 갱신뿐이다.
 */
@Service
public class CycleChecklistService {

    private final CycleRepository cycleRepository;
    private final PlanLineRepository planLineRepository;
    private final EnvelopeService envelopeService;
    private final Clock clock;

    public CycleChecklistService(
            CycleRepository cycleRepository,
            PlanLineRepository planLineRepository,
            EnvelopeService envelopeService,
            Clock clock) {
        this.cycleRepository = cycleRepository;
        this.planLineRepository = planLineRepository;
        this.envelopeService = envelopeService;
        this.clock = clock;
    }

    /** 오늘이 속한 사이클 + 통장별 체크리스트. 기준일은 주입된 KST Clock(규칙 3). */
    @Transactional(readOnly = true)
    public ChecklistResponse getCurrentChecklist(long userId) {
        LocalDate today = LocalDate.now(clock);
        Cycle cycle = cycleRepository
                .findByUserIdAndCycleStartLessThanEqualAndCycleEndGreaterThanEqual(userId, today, today)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, Map.of("resource", "cycle")));

        List<PlanLine> lines = planLineRepository.findByCycleIdOrderByIdAsc(cycle.getId());
        return assemble(cycle, lines);
    }

    /**
     * 라인 상태를 전이하고 갱신된 라인을 반환한다.
     *
     * @param userId 인증 주체 — 본인 사이클의 라인만(미소유·부재는 NOT_FOUND로 존재 비노출)
     * @param lineId 대상 plan_line
     * @param status 새 상태(PENDING/DONE/SKIPPED — DTO에서 enum 검증)
     */
    @Transactional
    public ChecklistResponse.Line changeLineStatus(long userId, long lineId, PlanLineStatus status) {
        PlanLine line = planLineRepository
                .findById(lineId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, Map.of("resource", "planLine", "id", lineId)));

        // 소유권 게이트: 라인이 가리키는 사이클이 본인 것인지. 미소유·부재 모두 NOT_FOUND로 통일(존재 비노출).
        Cycle cycle = cycleRepository
                .findByIdAndUserId(line.getCycleId(), userId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, Map.of("resource", "planLine", "id", lineId)));

        // 과거 사이클(cycle_end 경과)은 불변 — 상태 변경 불가(구현규칙 2장).
        LocalDate today = LocalDate.now(clock);
        if (today.isAfter(cycle.getCycleEnd())) {
            throw new ApiException(ErrorCode.CYCLE_LOCKED, Map.of("cycleId", cycle.getId()));
        }

        // 봉투 적립 연동(CYCLE-07)은 상태 전이 전에 수행한다 — SPEND 잠금이면 여기서 막혀 라인은 그대로다.
        applyEnvelopeLinkage(userId, line, status, today);

        line.changeStatus(status, clock.instant());
        return new ChecklistResponse.Line(
                line.getId(), line.getNameSnapshot(), line.getPlannedAmount(), line.getStatus());
    }

    /**
     * ENVELOPE 라인의 DONE 경계 전이에 봉투 적립 부수효과를 건다(CYCLE-07, 구현규칙 2장). DONE으로 들어가면
     * 적립(DEPOSIT), DONE에서 나오면 회수한다 — DONE↔DONE·PENDING↔SKIPPED 등 경계를 넘지 않는 전이와
     * ITEM·LIVING 라인은 부수효과가 없다(멱등). 적립/회수 자체는 {@link EnvelopeService}에 위임한다.
     */
    private void applyEnvelopeLinkage(long userId, PlanLine line, PlanLineStatus next, LocalDate today) {
        if (line.getLineType() != PlanLineType.ENVELOPE) {
            return;
        }
        boolean wasDone = line.getStatus() == PlanLineStatus.DONE;
        boolean willBeDone = next == PlanLineStatus.DONE;
        if (!wasDone && willBeDone) {
            envelopeService.recordChecklistDeposit(
                    userId, line.getEnvelopeId(), line.getPlannedAmount(), line.getCycleId(), today);
        } else if (wasDone && !willBeDone) {
            envelopeService.revertChecklistDeposit(
                    userId, line.getEnvelopeId(), line.getPlannedAmount(), line.getCycleId());
        }
    }

    /** plan_lines를 통장별로 묶어 체크리스트 응답을 조립한다(계산 없음 — 합산·집계만). */
    private ChecklistResponse assemble(Cycle cycle, List<PlanLine> lines) {
        // 라인은 id asc로 들어오므로, 통장 첫 등장 순서(≈항목 입력순)를 LinkedHashMap으로 보존한다.
        Map<Long, List<PlanLine>> byAccount = new LinkedHashMap<>();
        for (PlanLine line : lines) {
            byAccount
                    .computeIfAbsent(line.getAccountId(), k -> new ArrayList<>())
                    .add(line);
        }

        List<ChecklistResponse.AccountGroup> groups = byAccount.values().stream()
                .map(group -> {
                    PlanLine head = group.get(0);
                    long total =
                            group.stream().mapToLong(PlanLine::getPlannedAmount).sum();
                    List<ChecklistResponse.Line> lineViews = group.stream()
                            .map(line -> new ChecklistResponse.Line(
                                    line.getId(), line.getNameSnapshot(), line.getPlannedAmount(), line.getStatus()))
                            .toList();
                    return new ChecklistResponse.AccountGroup(
                            head.getAccountId(), head.getAccountNameSnapshot(), total, lineViews);
                })
                .toList();

        int total = lines.size();
        int done = (int) lines.stream()
                .filter(line -> line.getStatus() != PlanLineStatus.PENDING)
                .count();

        return new ChecklistResponse(
                cycle.getId(),
                cycle.getLabel(),
                cycle.getCycleStart(),
                cycle.getCycleEnd(),
                cycle.getIncome(),
                cycle.isIncomeConfirmed(),
                groups,
                new ChecklistResponse.Progress(done, total));
    }
}
