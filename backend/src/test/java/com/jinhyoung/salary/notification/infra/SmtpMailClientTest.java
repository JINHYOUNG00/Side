package com.jinhyoung.salary.notification.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jinhyoung.salary.notification.NotificationType;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.MessageSource;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * 실 SMTP 어댑터({@link SmtpMailClient}) 단위 테스트. {@link JavaMailSender}와 {@link MessageSource}를 모킹해 —
 * 어댑터가 수신자 언어로 번들 키를 골라(locale 전달) 제목·본문을 채우고, 발신/수신 주소를 세팅해 한 통을 발송하는지,
 * 대상일을 locale 포맷으로 메시지 인자에 넘기는지, 발송 실패는 그대로 전파하는지를 검증한다.
 */
class SmtpMailClientTest {

    private static final String FROM = "no-reply@salary.test";
    private static final String TO = "user@x.com";
    private static final LocalDate TARGET_DATE = LocalDate.of(2026, 1, 26);

    private final JavaMailSender mailSender = mock(JavaMailSender.class);
    private final MessageSource messages = mock(MessageSource.class);
    private final SmtpMailClient client = new SmtpMailClient(mailSender, messages, FROM);

    private static String localizedDate(Locale loc) {
        return DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)
                .withLocale(loc)
                .format(TARGET_DATE);
    }

    @Test
    void 한국어_수신자에게_KOREAN_locale로_렌더해_발송한다() {
        when(messages.getMessage(eq("notification.payday.subject"), any(), eq(Locale.KOREAN)))
                .thenReturn("제목");
        when(messages.getMessage(eq("notification.payday.body"), any(), eq(Locale.KOREAN)))
                .thenReturn("본문");

        client.send(TO, "ko", NotificationType.PAYDAY, TARGET_DATE);

        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(sent.capture());
        SimpleMailMessage msg = sent.getValue();
        assertThat(msg.getFrom()).isEqualTo(FROM);
        assertThat(msg.getTo()).containsExactly(TO);
        assertThat(msg.getSubject()).isEqualTo("제목");
        assertThat(msg.getText()).isEqualTo("본문");
    }

    @Test
    void 대상일을_locale_포맷으로_메시지_인자에_넘긴다() {
        when(messages.getMessage(any(), any(), any())).thenReturn("x");

        client.send(TO, "ko", NotificationType.PAYDAY, TARGET_DATE);

        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(messages).getMessage(eq("notification.payday.subject"), args.capture(), eq(Locale.KOREAN));
        assertThat(args.getValue()).containsExactly(localizedDate(Locale.KOREAN));
    }

    @Test
    void 영어_수신자는_ENGLISH_locale로_렌더한다() {
        when(messages.getMessage(any(), any(), any())).thenReturn("x");

        client.send(TO, "en", NotificationType.PAYDAY, TARGET_DATE);

        verify(messages).getMessage(eq("notification.payday.subject"), any(), eq(Locale.ENGLISH));
        verify(messages).getMessage(eq("notification.payday.body"), any(), eq(Locale.ENGLISH));
    }

    @Test
    void locale가_비면_한국어로_기본_렌더한다() {
        when(messages.getMessage(any(), any(), any())).thenReturn("x");

        client.send(TO, "  ", NotificationType.PAYDAY, TARGET_DATE);

        verify(messages).getMessage(eq("notification.payday.subject"), any(), eq(Locale.KOREAN));
    }

    @Test
    void 발송이_실패하면_예외를_전파한다() {
        when(messages.getMessage(any(), any(), any())).thenReturn("x");
        doThrow(new RuntimeException("smtp down")).when(mailSender).send(any(SimpleMailMessage.class));

        assertThatThrownBy(() -> client.send(TO, "ko", NotificationType.PAYDAY, TARGET_DATE))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("smtp down");
    }
}
