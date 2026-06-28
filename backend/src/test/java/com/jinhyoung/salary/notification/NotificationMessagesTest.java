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

    @Test
    void 한국어_ENVELOPE_DUE는_봉투명과_천단위_준비_금액을_렌더한다() {
        // {0}=지출일, {1}=봉투명, {2}=준비 금액(목표액) — {2,number}로 locale 천단위 구분 적용.
        Object[] args = {"2027년 1월 10일", "자동차세", 1_200_000L};

        String subject = messages.getMessage("notification.envelope_due.subject", args, Locale.KOREAN);
        String body = messages.getMessage("notification.envelope_due.body", args, Locale.KOREAN);

        assertThat(subject).contains("자동차세").contains("2027년 1월 10일");
        assertThat(body).contains("자동차세").contains("2027년 1월 10일").contains("1,200,000");
    }

    @Test
    void 영어_ENVELOPE_DUE도_봉투명과_준비_금액을_렌더한다() {
        Object[] args = {"January 10, 2027", "Car tax", 1_200_000L};

        String body = messages.getMessage("notification.envelope_due.body", args, Locale.ENGLISH);

        assertThat(body).contains("Car tax").contains("January 10, 2027").contains("1,200,000");
    }

    @Test
    void 한국어_CHECK_IN_제목과_본문이_렌더된다() {
        // {0}=체크인 대상일(다음 지급일 전일).
        Object[] args = {"2026년 1월 25일"};

        String subject = messages.getMessage("notification.check_in.subject", args, Locale.KOREAN);
        String body = messages.getMessage("notification.check_in.body", args, Locale.KOREAN);

        assertThat(subject).contains("2026년 1월 25일").contains("체크인");
        assertThat(body).contains("2026년 1월 25일").contains("생활비");
    }

    @Test
    void 영어_CHECK_IN_제목과_본문이_렌더된다() {
        Object[] args = {"January 25, 2026"};

        String subject = messages.getMessage("notification.check_in.subject", args, Locale.ENGLISH);
        String body = messages.getMessage("notification.check_in.body", args, Locale.ENGLISH);

        assertThat(subject).contains("January 25, 2026").containsIgnoringCase("check-in");
        assertThat(body).contains("January 25, 2026").containsIgnoringCase("balance");
    }

    @Test
    void 한국어_FX_CHECKUP_제목과_본문이_렌더된다() {
        // {0}=점검일(분기 첫날).
        Object[] args = {"2026년 4월 1일"};

        String subject = messages.getMessage("notification.fx_checkup.subject", args, Locale.KOREAN);
        String body = messages.getMessage("notification.fx_checkup.body", args, Locale.KOREAN);

        assertThat(subject).contains("2026년 4월 1일").contains("외화");
        assertThat(body).contains("2026년 4월 1일").contains("예수금");
    }

    @Test
    void 영어_FX_CHECKUP_제목과_본문이_렌더된다() {
        Object[] args = {"April 1, 2026"};

        String subject = messages.getMessage("notification.fx_checkup.subject", args, Locale.ENGLISH);
        String body = messages.getMessage("notification.fx_checkup.body", args, Locale.ENGLISH);

        assertThat(subject).contains("April 1, 2026").containsIgnoringCase("foreign-currency");
        assertThat(body).contains("April 1, 2026").containsIgnoringCase("deposit");
    }

    @Test
    void 한국어_CUSTOM은_알림일과_사용자_메모를_렌더한다() {
        // {0}=알림일, {1}=사용자 메모. 문장은 번들이 조립하고 메모만 데이터로 끼운다(규칙 7).
        Object[] args = {"2026년 6월 14일", "전세 만기 점검"};

        String subject = messages.getMessage("notification.custom.subject", args, Locale.KOREAN);
        String body = messages.getMessage("notification.custom.body", args, Locale.KOREAN);

        assertThat(subject).contains("전세 만기 점검");
        assertThat(body).contains("2026년 6월 14일").contains("전세 만기 점검");
    }

    @Test
    void 영어_CUSTOM도_알림일과_사용자_메모를_렌더한다() {
        Object[] args = {"June 14, 2026", "Check lease renewal"};

        String subject = messages.getMessage("notification.custom.subject", args, Locale.ENGLISH);
        String body = messages.getMessage("notification.custom.body", args, Locale.ENGLISH);

        assertThat(subject).contains("Check lease renewal");
        assertThat(body).contains("June 14, 2026").contains("Check lease renewal");
    }
}
