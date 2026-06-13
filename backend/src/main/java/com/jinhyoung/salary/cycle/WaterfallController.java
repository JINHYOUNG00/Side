package com.jinhyoung.salary.cycle;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 폭포 조회(FLOW-02, API명세 3장). 인증 필수 — principal=userId(JwtAuthenticationFilter)로 본인 폭포만 조회한다.
 *
 * <p>{@code GET /me/waterfall}는 사이클 스냅샷이 아니라 현재 실수령액·활성 항목 기준의 라이브 폭포다(스냅샷
 * 생성·조회는 별도 {@code /cycles}). 조립은 {@link WaterfallQueryService}, 순수 계산은 owner의 도메인 클래스가 맡는다.
 */
@RestController
@RequestMapping("/api/v1/me/waterfall")
public class WaterfallController {

    private final WaterfallQueryService waterfallQueryService;

    public WaterfallController(WaterfallQueryService waterfallQueryService) {
        this.waterfallQueryService = waterfallQueryService;
    }

    @GetMapping
    public WaterfallResponse waterfall(@AuthenticationPrincipal Long userId) {
        return waterfallQueryService.getWaterfall(userId);
    }
}
