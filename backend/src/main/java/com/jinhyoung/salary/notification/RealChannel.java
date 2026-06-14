package com.jinhyoung.salary.notification;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.beans.factory.annotation.Qualifier;

/**
 * 실제 알림 발송 채널 빈을 식별하는 한정자(NOTI-05). 중복 방지 게이트({@link DeduplicatingNotificationSender})는
 * {@code @Primary}라 {@code NotificationSender} 타입으로는 자기 자신이 잡힌다 — 그래서 감쌀 "진짜 채널"을 이
 * 한정자로 명시해 주입받는다.
 *
 * <p>활성 채널은 {@code app.notification.channel}로 선택한다(기본 {@code log} → {@link LoggingNotificationSender},
 * {@code email} → {@link EmailNotificationSender}). 둘은 {@code @ConditionalOnProperty}로 동시에 존재하지 않으므로
 * 이 한정자가 가리키는 채널 빈은 항상 하나다. (NOTI-04 메모 ⑤가 예고한 인터페이스+한정자 디커플링)
 */
@Qualifier
@Target({ElementType.TYPE, ElementType.PARAMETER, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface RealChannel {}
