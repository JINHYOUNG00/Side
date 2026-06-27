package com.jinhyoung.salary.suggestion.infra;

import com.jinhyoung.salary.suggestion.domain.SuggestionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 보정/리밸런싱 제안(ERD suggestions, SUG-01~03). 엔티티는 infra에 둔다(아키텍처 v1.1) — 발동 판정·제안 산술은
 * 순수 {@link com.jinhyoung.salary.suggestion.domain.SuggestionRule}이 맡는다.
 *
 * <p>{@code payload}는 jsonb 구조화 파라미터(SUG-03, 규칙 7)로 {@link JdbcTypeCode SqlTypes.JSON}으로 매핑한다 —
 * 문장은 담지 않고 클라이언트 i18n 템플릿이 type+payload로 문구를 조립한다. {@code status}는 PENDING으로 생성돼
 * 반영(APPLIED)·닫기(DISMISSED)로 단방향 전이하며, 해소 시각을 {@code resolved_at}에 남긴다. {@code created_at}은
 * DB default(now())에 맡겨 매핑하지 않는다(check_ins·plan_lines 전례).
 *
 * <p><b>반영(apply)의 범위</b>: 이 엔티티/서비스는 상태 전이와 해소 시각만 기록한다 — payload가 제시한 금액으로
 * 실제 배분 계획(폭포·plan_lines)을 자동 변경하지는 않는다. 자동 반영은 폭포 도메인(소유자 영역)에 닿는 후속 작업이며,
 * 현재 클라이언트는 payload의 권고치를 사용자에게 보여 해당 편집 화면으로 안내한다.
 */
@Entity
@Table(name = "suggestions")
public class Suggestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SuggestionType type;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private SuggestionStatus status;

    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;

    protected Suggestion() {
        // JPA
    }

    private Suggestion(Long userId, SuggestionType type, Map<String, Object> payload) {
        this.userId = userId;
        this.type = type;
        this.payload = payload;
        this.status = SuggestionStatus.PENDING;
    }

    /** 새 제안 생성(SUG-02). PENDING으로 시작하며 resolved_at은 비어 있다. payload는 룰이 만든 구조화 데이터. */
    public static Suggestion create(Long userId, SuggestionType type, Map<String, Object> payload) {
        return new Suggestion(userId, type, payload);
    }

    /**
     * 제안 반영(MOD-06) — 상태를 APPLIED로 전이하고 해소 시각을 기록한다. 실제 배분 계획 변경은 이 메서드의 책임이
     * 아니다(엔티티 주석 참조). 해소 시각은 주입된 KST {@code Clock}으로 산출해 넘긴다(규칙 3, 직접 호출 금지).
     */
    public void apply(OffsetDateTime resolvedAt) {
        this.status = SuggestionStatus.APPLIED;
        this.resolvedAt = resolvedAt;
    }

    /** 제안 닫기(MOD-06) — 상태를 DISMISSED로 전이하고 해소 시각을 기록한다. */
    public void dismiss(OffsetDateTime resolvedAt) {
        this.status = SuggestionStatus.DISMISSED;
        this.resolvedAt = resolvedAt;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public SuggestionType getType() {
        return type;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public SuggestionStatus getStatus() {
        return status;
    }

    public OffsetDateTime getResolvedAt() {
        return resolvedAt;
    }
}
