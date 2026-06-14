package com.jinhyoung.salary.cycle;

import com.jinhyoung.salary.cycle.infra.Cycle;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 사이클 / 체크리스트(API명세 5장). 인증 필수 — principal=userId(JwtAuthenticationFilter)로 본인 사이클만 다룬다.
 *
 * <p>현재는 실수령액 확인({@code PATCH /cycles/{id}/income}, CYCLE-04)만 노출한다. 스냅샷 생성({@code POST /cycles},
 * CYCLE-03)은 지급일 일일 판정 트리거로 분리돼 있고, 현재 사이클·체크리스트 조회·라인 상태 전이(CYCLE-06~07)는 후속이다.
 */
@RestController
@RequestMapping("/api/v1/cycles")
public class CycleController {

    /** 실수령액 상한(구현규칙 5장 금액 규칙: 1 ~ 10억) — UserController와 동일. */
    private static final long INCOME_MAX = 1_000_000_000L;

    private final CycleIncomeService cycleIncomeService;

    public CycleController(CycleIncomeService cycleIncomeService) {
        this.cycleIncomeService = cycleIncomeService;
    }

    /**
     * 실수령액 확인·수정(CYCLE-04). 확정 시 income_confirmed=true로 바뀌고 LIVING 라인이 재계산된다.
     * 평소보다 큰 차액의 여윳돈 배분 제안(CYCLE-05)은 Phase 6 소관이라 응답에 포함하지 않는다.
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
