package com.jinhyoung.salary.notification;

import java.time.LocalDate;

/**
 * 메일 발송 포트(NOTI-05, 아키텍처 infra "외부 클라이언트(메일)"). {@link EmailNotificationSender}가 수신자와
 * 구조화된 알림 정보를 넘기면, 실제 SMTP 발송은 이 포트 뒤 어댑터가 담당한다.
 *
 * <p>인자는 <b>구조화 데이터</b>만 받는다 — 제목·본문 같은 문장은 여기서 만들지 않는다(규칙 7). 실제 어댑터
 * ({@code SmtpMailClient})가 {@code locale}로 메시지 번들(messages_ko/en)을 골라 내용을 렌더링한다. 어댑터는
 * {@code app.notification.channel=email}일 때만 등록되고 SMTP 설정(spring.mail.*)을 함께 요구한다 — 기본 채널은
 * 로그({@link LoggingNotificationSender})라 SMTP 미설정 환경도 무손상.
 *
 * <p>발송 실패는 예외로 던진다 — 호출자({@link EmailNotificationSender})가 그대로 전파하면 중복 방지 게이트의
 * "기록 먼저, 발송 후 확정" 트랜잭션이 롤백돼 다음 배치에서 재시도된다(NOTI-04).
 */
public interface MailClient {

    /**
     * 한 사용자에게 알림 메일 1건을 발송한다.
     *
     * @param toEmail 수신 이메일(널/공백 아님 — 호출자가 보장)
     * @param locale 수신자 언어(ko/en) — 어댑터가 내용 렌더링에 사용
     * @param type 알림 종류
     * @param targetDate 알림 대상일
     */
    void send(String toEmail, String locale, NotificationType type, LocalDate targetDate);
}
