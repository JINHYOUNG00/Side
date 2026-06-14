package com.jinhyoung.salary.cycle;

import com.jinhyoung.salary.common.ApiException;
import com.jinhyoung.salary.common.ErrorCode;
import com.jinhyoung.salary.cycle.infra.Cycle;
import com.jinhyoung.salary.cycle.infra.CycleRepository;
import com.jinhyoung.salary.cycle.infra.PlanLine;
import com.jinhyoung.salary.cycle.infra.PlanLineRepository;
import com.jinhyoung.salary.cycle.infra.PlanLineStatus;
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
 * <p>봉투(ENVELOPE) 라인의 적립 연동(EnvelopeTransaction 생성·SPEND 잠금, 구현규칙 2장)은 봉투 도메인이 없는
 * 현재(Phase 2) 대상이 아니다 — plan_lines에는 ITEM·LIVING 라인만 존재하므로 상태 전이는 상태·처리 시각 갱신뿐이다.
 * 봉투 도입(Phase 3) 시 ENVELOPE 라인 전이에 적립 부수효과를 더한다.
 */
@Service
public class CycleChecklistService {

    private final CycleRepository cycleRepository;
    private final PlanLineRepository planLineRepository;
    private final Clock clock;

    public CycleChecklistService(CycleRepository cycleRepository, PlanLineRepository planLineRepository, Clock clock) {
        this.cycleRepository = cycleRepository;
        this.planLineRepository = planLineRepository;
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

        line.changeStatus(status, clock.instant());
        return new ChecklistResponse.Line(
                line.getId(), line.getNameSnapshot(), line.getPlannedAmount(), line.getStatus());
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
