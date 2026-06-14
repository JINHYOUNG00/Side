package com.jinhyoung.salary.notification.infra;

import com.jinhyoung.salary.notification.MailClient;
import com.jinhyoung.salary.notification.NotificationType;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.MessageSource;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * {@link MailClient}의 실 SMTP 어댑터(NOTI-05, 아키텍처 infra "외부 클라이언트(메일)"). 스프링
 * {@link JavaMailSender}로 한 통의 평문 메일을 발송한다. 제목·본문 문장은 코드가 만들지 않고 수신자 언어로
 * {@link MessageSource} 번들(messages_ko/en)에서 렌더한다 — 규칙 7(문구 하드코딩 금지).
 *
 * <p>{@code app.notification.channel=email}일 때만 빈으로 등록된다({@link ConditionalOnProperty}). 이때
 * {@code spring.mail.host}가 함께 설정돼 있어야 {@link JavaMailSender} 자동 구성 빈이 존재한다 — 미설정 시 부팅이
 * 실패하는데, 이는 "email 채널을 켰으면 SMTP를 설정하라"는 누락 신호다. 기본(log) 채널에선 이 빈이 등록되지 않으므로
 * SMTP 미설정 환경도 무손상이다.
 *
 * <p>발송 실패(SMTP 예외)는 그대로 전파한다 — 호출자({@code EmailNotificationSender})를 거쳐 중복 방지 게이트의
 * "기록 먼저, 발송 후 확정" 트랜잭션이 롤백돼 다음 배치에서 재시도된다(NOTI-04).
 */
@Component
@ConditionalOnProperty(name = "app.notification.channel", havingValue = "email")
public class SmtpMailClient implements MailClient {

    private final JavaMailSender mailSender;
    private final MessageSource messages;
    private final String from;

    public SmtpMailClient(
            JavaMailSender mailSender,
            MessageSource messages,
            @Value("${app.notification.email.from:no-reply@salary.local}") String from) {
        this.mailSender = mailSender;
        this.messages = messages;
        this.from = from;
    }

    @Override
    public void send(
            String toEmail, String locale, NotificationType type, LocalDate targetDate, Object... messageArgs) {
        Locale loc = resolveLocale(locale);
        String date = DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)
                .withLocale(loc)
                .format(targetDate);
        // {0}=대상일(locale 포맷), {1..}=호출자가 넘긴 부가 데이터(봉투명·준비 금액 등). 문장은 번들이 조립(규칙 7).
        Object[] args = new Object[messageArgs.length + 1];
        args[0] = date;
        System.arraycopy(messageArgs, 0, args, 1, messageArgs.length);
        String key = "notification." + type.name().toLowerCase(Locale.ROOT);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(toEmail);
        message.setSubject(messages.getMessage(key + ".subject", args, loc));
        message.setText(messages.getMessage(key + ".body", args, loc));
        mailSender.send(message);
    }

    /** 수신자 언어 코드(ko/en)를 {@link Locale}로 — 비거나 알 수 없으면 한국어 기본. */
    private static Locale resolveLocale(String locale) {
        if (locale == null || locale.isBlank()) {
            return Locale.KOREAN;
        }
        return Locale.forLanguageTag(locale);
    }
}
