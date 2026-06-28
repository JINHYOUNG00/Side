package com.jinhyoung.salary.cycle;

import com.jinhyoung.salary.cycle.infra.Cycle;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 사이클 / 체크리스트(API명세 5장). 인증 필수 — principal=userId(JwtAuthenticationFilter)로 본인 사이클만 다룬다.
 *
 * <p>현재 사이클·체크리스트 조회({@code GET /cycles/current}, CYCLE-06)와 실수령액 확인({@code PATCH
 * /cycles/{id}/income}, CYCLE-04)을 노출한다. 스냅샷 생성({@code POST /cycles}, CYCLE-03)은 지급일 일일 판정
 * 트리거로 분리돼 있다. 라인 상태 전이({@code PATCH /plan-lines/{id}}, CYCLE-06)는 {@link PlanLineController}.
 */
@RestController
@RequestMapping("/api/v1/cycles")
public class CycleController {

    /** 실수령액 상한(구현규칙 5장 금액 규칙: 1 ~ 10억) — UserController와 동일. */
    private static final long INCOME_MAX = 1_000_000_000L;

    private final CycleIncomeService cycleIncomeService;
    private final CycleChecklistService cycleChecklistService;
    private final CycleSnapshotService cycleSnapshotService;

    public CycleController(
            CycleIncomeService cycleIncomeService,
            CycleChecklistService cycleChecklistService,
            CycleSnapshotService cycleSnapshotService) {
        this.cycleIncomeService = cycleIncomeService;
        this.cycleChecklistService = cycleChecklistService;
        this.cycleSnapshotService = cycleSnapshotService;
    }

    /** 오늘이 속한 사이클 + 통장별 체크리스트(CYCLE-06). 미생성이면 404 NOT_FOUND(스냅샷 생성 동선). */
    @GetMapping("/current")
    public ChecklistResponse current(@AuthenticationPrincipal Long userId) {
        return cycleChecklistService.getCurrentChecklist(userId);
    }

    /**
     * 현재 사이클 지급일 재보정. 이미 만들어진 사이클의 경계가 틀린 월급일로 박혀 있을 때, 바뀐 설정으로 경계를
     * 다시 도출해 이번 사이클을 다시 만든다(이체 시작 전 사이클만 — DONE 라인 있으면 409 CYCLE_LOCKED).
     * 현재 사이클 없으면 404. 메뉴의 월급일 수정(미래용)과 달리, 이건 "이번 사이클에 즉시 반영"하는 동선이다.
     */
    @PostMapping("/current/recalibrate")
    public CycleResponse recalibrateCurrent(@AuthenticationPrincipal Long userId) {
        Cycle cycle = cycleSnapshotService.recalibrateCurrentCycle(userId);
        return CycleResponse.from(cycle);
    }

    /**
     * 실수령액 확인·수정(CYCLE-04). 확정 시 income_confirmed=true로 바뀌고 LIVING 라인이 재계산된다.
     * 평소보다 기준 이상 큰/작은 차액이면 여윳돈/부족 배분 제안(CYCLE-05)을 함께 적재한다 — 응답엔 싣지 않고
     * PENDING 제안으로 만들어 홈·리포트 제안 카드(MOD-06)에서 노출한다. 실제 배분/축소 적용은 소유자 후속.
     */
    @PatchMapping("/{id}/income")
    public CycleResponse confirmIncome(
            @AuthenticationPrincipal Long userId,
            @PathVariable long id,
            @Valid @RequestBody ConfirmIncomeRequest request) {
        Cycle cycle = cycleIncomeService.confirmIncome(userId, id, request.income());
        return CycleResponse.from(cycle);
    }

    public record ConfirmIncomeRequest(@NotNull @Min(1) @Max(INCOME_MAX) Long income) {}

    public record CycleResponse(
            Long id, String label, LocalDate cycleStart, LocalDate cycleEnd, long income, boolean incomeConfirmed) {
        static CycleResponse from(Cycle cycle) {
            return new CycleResponse(
                    cycle.getId(),
                    cycle.getLabel(),
                    cycle.getCycleStart(),
                    cycle.getCycleEnd(),
                    cycle.getIncome(),
                    cycle.isIncomeConfirmed());
        }
    }
}
