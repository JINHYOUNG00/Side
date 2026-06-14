package com.jinhyoung.salary.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;

/**
 * 알림 메일 메시지 번들(NOTI-05) 검증. 스프링 부트가 자동 구성하는 것과 동일하게 basename {@code messages}를
 * UTF-8로 로드해 — PAYDAY 알림의 제목·본문 키가 ko/en 양쪽에 존재하고, 대상일 인자가 채워지며, 언어별로 다른 문장이
 * 렌더되는지(번들 누락·인코딩 깨짐 가드)를 확인한다.
 */
class NotificationMessagesTest {

    private final ResourceBundleMessageSource messages = messageSource();

    private static ResourceBundleMessageSource messageSource() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("messages");
        source.setDefaultEncoding("UTF-8");
        return source;
    }

    @Test
    void 한국어_PAYDAY_제목과_본문이_렌더된다() {
        Object[] args = {"2026년 1월 26일"};

        String subject = messages.getMessage("notification.payday.subject", args, Locale.KOREAN);
        String body = messages.getMessage("notification.payday.body", args, Locale.KOREAN);

        assertThat(subject).contains("2026년 1월 26일").contains("월급날");
        assertThat(body).contains("2026년 1월 26일").contains("체크리스트");
    }

    @Test
    void 영어_PAYDAY_제목과_본문이_렌더된다() {
        Object[] args = {"January 26, 2026"};

        String subject = messages.getMessage("notification.payday.subject", args, Locale.ENGLISH);
        String body = messages.getMessage("notification.payday.body", args, Locale.ENGLISH);

        assertThat(subject).contains("January 26, 2026").containsIgnoringCase("payday");
        assertThat(body).contains("January 26, 2026").containsIgnoringCase("checklist");
    }

    @Test
    void 언어별로_다른_문장이_렌더된다() {
        Object[] args = {"x"};

        String ko = messages.getMessage("notification.payday.subject", args, Locale.KOREAN);
        String en = messages.getMessage("notification.payday.subject", args, Locale.ENGLISH);

        assertThat(ko).isNotEqualTo(en);
    }
}
