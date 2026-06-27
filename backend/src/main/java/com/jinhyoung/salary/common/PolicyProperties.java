package com.jinhyoung.salary.common;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 운영 조정 파라미터(application.yml의 {@code app.policy}, 구현규칙 6장). 코드 수정 없이 환경값으로 바꾼다.
 *
 * <p>현재는 외화 버퍼율만 둔다. windfall-threshold·surplus-threshold 등 나머지 상수는 해당 기능(CYCLE-05·
 * SUG-02) 구현 시 이 레코드에 추가한다.
 *
 * @param fxBufferRate 외화 권장 월 이체액 버퍼율(ITEM-04). 기본 0.05(구현규칙 6장)
 */
@ConfigurationProperties("app.policy")
public record PolicyProperties(@DefaultValue("0.05") BigDecimal fxBufferRate) {}
