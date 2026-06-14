package com.jinhyoung.salary.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jinhyoung.salary.user.infra.User;
import com.jinhyoung.salary.user.infra.UserRepository;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * 이메일 알림 채널 어댑터(NOTI-05) 단위 테스트. 발송 인터페이스({@link MailClient})를 모킹해 — 어댑터가 수신자
 * 이메일을 조회해 구조화 데이터로 발송을 호출하는지, 이메일 미수집(동의 거부) 사용자는 건너뛰는지, 발송 실패는 그대로
 * 전파하는지(중복 방지 게이트가 롤백·재시도하도록)를 검증한다.
 */
class EmailNotificationSenderTest {

    private static final long USER_ID = 42L;
    private static final LocalDate TARGET_DATE = LocalDate.of(2026, 1, 26);

    private final UserRepository userRepository = mock(UserRepository.class);
    private final MailClient mailClient = mock(MailClient.class);
    private final EmailNotificationSender sender = new EmailNotificationSender(userRepository, mailClient);

    private User userWithEmail(String email) {
        // createFromOAuth가 email을 그대로 보존(널 허용), locale은 기본 "ko".
        return User.createFromOAuth("KAKAO", "p" + USER_ID, email, "nick");
    }

    @Test
    void 이메일이_있는_사용자에게_구조화_데이터로_발송한다() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(userWithEmail("user@x.com")));

        sender.send(NotificationType.PAYDAY, USER_ID, TARGET_DATE);

        verify(mailClient).send("user@x.com", "ko", NotificationType.PAYDAY, TARGET_DATE);
    }

    @Test
    void 이메일이_없으면_발송하지_않고_건너뛴다() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(userWithEmail(null)));

        sender.send(NotificationType.PAYDAY, USER_ID, TARGET_DATE);

        verify(mailClient, never()).send(anyString(), anyString(), any(), any());
    }

    @Test
    void 이메일이_공백이면_발송하지_않고_건너뛴다() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(userWithEmail("  ")));

        sender.send(NotificationType.PAYDAY, USER_ID, TARGET_DATE);

        verify(mailClient, never()).send(anyString(), anyString(), any(), any());
    }

    @Test
    void 발송이_실패하면_예외를_전파한다() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(userWithEmail("user@x.com")));
        doThrow(new RuntimeException("smtp down"))
                .when(mailClient)
                .send(eq("user@x.com"), eq("ko"), eq(NotificationType.PAYDAY), eq(TARGET_DATE));

        assertThatThrownBy(() -> sender.send(NotificationType.PAYDAY, USER_ID, TARGET_DATE))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("smtp down");
    }

    @Test
    void 대상_사용자가_없으면_예외를_던진다() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sender.send(NotificationType.PAYDAY, USER_ID, TARGET_DATE))
                .isInstanceOf(IllegalStateException.class);
        verify(mailClient, never()).send(anyString(), anyString(), any(), any());
    }

    @Test
    void 채널은_EMAIL이다() {
        assertThat(sender.channel()).isEqualTo(NotificationChannel.EMAIL);
    }
}
