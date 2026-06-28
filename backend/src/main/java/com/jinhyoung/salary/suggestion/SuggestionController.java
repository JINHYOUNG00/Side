package com.jinhyoung.salary.suggestion;

import com.jinhyoung.salary.suggestion.infra.Suggestion;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 보정/리밸런싱 제안(SUG-01~03, API명세 6장). 인증 필수 — principal=userId(JwtAuthenticationFilter)로 본인 제안만
 * 다룬다. 제안 생성은 일일 배치(SuggestionService)가 맡고, 여기서는 조회·반영·닫기만 제공한다.
 *
 * <p>응답 payload는 문장이 아닌 구조화 데이터(SUG-03, 규칙 7) — 클라이언트 i18n 템플릿이 type+payload로 문구를
 * 조립한다.
 */
@RestController
@RequestMapping("/api/v1/suggestions")
public class SuggestionController {

    private final SuggestionService suggestionService;
    private final WindfallAllocationService windfallAllocationService;

    public SuggestionController(
            SuggestionService suggestionService, WindfallAllocationService windfallAllocationService) {
        this.suggestionService = suggestionService;
        this.windfallAllocationService = windfallAllocationService;
    }

    /** 노출 대상(PENDING) 제안 목록 — 최신순. */
    @GetMapping
    public List<SuggestionResponse> list(@AuthenticationPrincipal Long userId) {
        return suggestionService.listPending(userId).stream()
                .map(SuggestionResponse::from)
                .toList();
    }

    /** 제안 반영(MOD-06) — APPLIED로 전이. 부재·미소유는 404, 이미 해소면 409. */
    @PostMapping("/{id}/apply")
    public SuggestionResponse apply(@AuthenticationPrincipal Long userId, @PathVariable long id) {
        return SuggestionResponse.from(suggestionService.apply(userId, id));
    }

    /** 제안 닫기(MOD-06) — DISMISSED로 전이. 게이트는 반영과 동일. */
    @PostMapping("/{id}/dismiss")
    public SuggestionResponse dismiss(@AuthenticationPrincipal Long userId, @PathVariable long id) {
        return SuggestionResponse.from(suggestionService.dismiss(userId, id));
    }

    /**
     * 여윳돈/부족 제안 배분 적용(CYCLE-05, 인터랙티브). 사용자가 고른 plan_line별 배분(WINDFALL)·축소(SHORTFALL)
     * 금액으로 이번 사이클 계획을 조정하고 제안을 APPLIED로 닫는다. 부재·미소유는 404, 이미 해소면 409, 검증 실패는
     * 400. WINDFALL/SHORTFALL이 아닌 제안엔 400.
     */
    @PostMapping("/{id}/allocate")
    public SuggestionResponse allocate(
            @AuthenticationPrincipal Long userId, @PathVariable long id, @Valid @RequestBody AllocateRequest request) {
        return SuggestionResponse.from(windfallAllocationService.allocate(userId, id, request.toAllocations()));
    }

    /**
     * 배분 요청 — plan_line별 금액 목록(최소 1건). 각 금액은 양수(배분/축소 크기). 합·상한·0 미만 등 도메인 불변
     * 위반은 서비스가 VALIDATION_FAILED로 가린다.
     */
    public record AllocateRequest(@NotEmpty List<@Valid Item> allocations) {

        public record Item(@NotNull Long planLineId, @NotNull @Min(1) Long amount) {}

        List<WindfallAllocationService.Allocation> toAllocations() {
            return allocations.stream()
                    .map(item -> new WindfallAllocationService.Allocation(item.planLineId(), item.amount()))
                    .toList();
        }
    }

    /**
     * 제안 조회 응답(SUG-03). type과 구조화 payload, 상태를 싣는다 — 문구는 클라이언트가 type+payload로 조립한다
     * (규칙 7).
     */
    public record SuggestionResponse(Long id, String type, String status, Map<String, Object> payload) {
        static SuggestionResponse from(Suggestion suggestion) {
            return new SuggestionResponse(
                    suggestion.getId(),
                    suggestion.getType().name(),
                    suggestion.getStatus().name(),
                    suggestion.getPayload());
        }
    }
}
