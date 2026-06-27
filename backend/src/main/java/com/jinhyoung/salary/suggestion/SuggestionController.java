package com.jinhyoung.salary.suggestion;

import com.jinhyoung.salary.suggestion.infra.Suggestion;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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

    public SuggestionController(SuggestionService suggestionService) {
        this.suggestionService = suggestionService;
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
