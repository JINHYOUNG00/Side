package com.jinhyoung.salary.suggestion.domain;

import java.util.Map;

/**
 * 룰이 산출한 제안 초안(SUG-02·SUG-03) — 영속화 전의 순수 값(규칙 9). 서비스가 이를 {@code Suggestion} 엔티티로
 * 저장한다.
 *
 * <p>{@code payload}는 문장이 아닌 <b>구조화 데이터</b>(SUG-03, 규칙 7)다 — 문장 조립은 클라이언트 i18n 템플릿이
 * type+payload로 수행한다. {@code dedupKey}는 동일 제안 중복 생성을 막는 키로, 사용자별 PENDING 제안 중 같은 키가
 * 이미 있으면 새로 만들지 않는다(구현규칙 7장). RAISE_LIVING·RAISE_SAVING은 사용자당 하나라 키가 type 이름이고,
 * REBALANCE_MATURITY는 항목마다 별개라 {@code type + ":" + itemId}다.
 *
 * <p>{@code payload}는 null 값을 담지 않는다 — 값이 없는 파라미터(예: 산출 불가한 예상 만기금액)는 키 자체를
 * 생략한다(jsonb에 null을 박는 대신). 생성자가 {@link Map#copyOf}로 불변화하며 null 값이 있으면 거부해 이 규율을
 * 기계적으로 강제한다.
 *
 * @param type 제안 종류
 * @param dedupKey 중복 방지 키(같은 키의 PENDING 제안이 있으면 생성 안 함)
 * @param payload 구조화 파라미터(불변 맵, null 값 불가) — jsonb로 저장, 클라이언트가 문구 조립
 */
public record SuggestionDraft(SuggestionType type, String dedupKey, Map<String, Object> payload) {

    public SuggestionDraft {
        payload = Map.copyOf(payload);
    }
}
