package com.jinhyoung.salary.cycle;

import com.jinhyoung.salary.cycle.infra.PlanLineStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 체크리스트 라인 상태 전이(CYCLE-06, API명세 5장 {@code PATCH /plan-lines/{id}}). 인증 필수 —
 * principal=userId로 본인 사이클의 라인만 다룬다(미소유·부재는 NOT_FOUND, 지난 사이클은 CYCLE_LOCKED).
 *
 * <p>조회({@code GET /cycles/current})는 {@link CycleController}에 있다. 라인은 사이클에 종속되지만
 * URI는 API명세대로 {@code /plan-lines/{id}}로 평면화한다.
 */
@RestController
@RequestMapping("/api/v1/plan-lines")
public class PlanLineController {

    private final CycleChecklistService cycleChecklistService;

    public PlanLineController(CycleChecklistService cycleChecklistService) {
        this.cycleChecklistService = cycleChecklistService;
    }

    /** 라인 상태 전이 PENDING ↔ DONE/SKIPPED. 갱신된 라인을 반환한다(진행도 재조회는 GET /cycles/current). */
    @PatchMapping("/{id}")
    public ChecklistResponse.Line changeStatus(
            @AuthenticationPrincipal Long userId,
            @PathVariable long id,
            @Valid @RequestBody ChangeStatusRequest request) {
        return cycleChecklistService.changeLineStatus(userId, id, request.status());
    }

    /** 잘못된 enum 값은 역직렬화 단계에서 VALIDATION_FAILED로 걸린다(GlobalExceptionHandler). */
    public record ChangeStatusRequest(@NotNull PlanLineStatus status) {}
}
