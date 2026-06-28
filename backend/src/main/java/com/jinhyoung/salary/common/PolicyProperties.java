package com.jinhyoung.salary.common;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 운영 조정 파라미터(application.yml의 {@code app.policy}, 구현규칙 6장). 코드 수정 없이 환경값으로 바꾼다.
 *
 * <p>windfall-threshold(CYCLE-05) 등 나머지 상수는 해당 기능 구현 시 이 레코드에 추가한다.
 *
 * @param fxBufferRate 외화 권장 월 이체액 버퍼율(ITEM-04). 기본 0.05(구현규칙 6장)
 * @param overspendStreak 보정 제안 발동 연속 사이클 수(SUG-02). 기본 3(구현규칙 6장)
 * @param surplusThreshold 잉여 저축 증액 제안 기준액(원, SUG-02). 기본 30,000(구현규칙 6장)
 * @param windfallThreshold 여윳돈/부족 배분 제안 발동 기준액(원, CYCLE-05). 기본 30,000(구현규칙 6장)
 */
@ConfigurationProperties("app.policy")
public record PolicyProperties(
        @DefaultValue("0.05") BigDecimal fxBufferRate,
        @DefaultValue("3") int overspendStreak,
        @DefaultValue("30000") long surplusThreshold,
        @DefaultValue("30000") long windfallThreshold) {}
