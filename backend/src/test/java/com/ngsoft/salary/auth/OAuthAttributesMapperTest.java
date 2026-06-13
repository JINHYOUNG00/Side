package com.ngsoft.salary.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

/** 공급자별 응답 → OAuthUserInfo 정규화 어댑터 단위 테스트(순수, DB 불필요). */
class OAuthAttributesMapperTest {

    @Test
    void 카카오_중첩_구조를_정규화한다() {
        Map<String, Object> attrs = Map.of(
                "id",
                123456789L,
                "kakao_account",
                Map.of("email", "a@kakao.com", "profile", Map.of("nickname", "월급이")));

        OAuthUserInfo info = new KakaoAttributesMapper().map(attrs);

        assertThat(info.provider()).isEqualTo(OAuthProvider.KAKAO);
        assertThat(info.providerId()).isEqualTo("123456789");
        assertThat(info.email()).isEqualTo("a@kakao.com");
        assertThat(info.nickname()).isEqualTo("월급이");
    }

    @Test
    void 카카오_동의거부로_이메일_프로필이_없어도_id는_매핑된다() {
        OAuthUserInfo info = new KakaoAttributesMapper().map(Map.of("id", 42L));

        assertThat(info.providerId()).isEqualTo("42");
        assertThat(info.email()).isNull();
        assertThat(info.nickname()).isNull();
    }

    @Test
    void 구글_sub를_providerId로_매핑한다() {
        OAuthUserInfo info =
                new GoogleAttributesMapper().map(Map.of("sub", "google-uid-1", "email", "b@gmail.com", "name", "굴글이"));

        assertThat(info.provider()).isEqualTo(OAuthProvider.GOOGLE);
        assertThat(info.providerId()).isEqualTo("google-uid-1");
        assertThat(info.email()).isEqualTo("b@gmail.com");
        assertThat(info.nickname()).isEqualTo("굴글이");
    }

    @Test
    void 네이버_response_래퍼를_벗겨_매핑한다() {
        OAuthUserInfo info = new NaverAttributesMapper()
                .map(Map.of("response", Map.of("id", "naver-uid-1", "email", "c@naver.com", "nickname", "네이버")));

        assertThat(info.provider()).isEqualTo(OAuthProvider.NAVER);
        assertThat(info.providerId()).isEqualTo("naver-uid-1");
        assertThat(info.email()).isEqualTo("c@naver.com");
    }

    @Test
    void 필수_식별자가_없으면_거부한다() {
        assertThatThrownBy(() -> new KakaoAttributesMapper().map(Map.of("kakao_account", Map.of())))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GoogleAttributesMapper().map(Map.of("email", "x@y.com")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NaverAttributesMapper().map(Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void provider_경로변수를_대소문자_무시로_파싱하고_네이버는_비활성이다() {
        assertThat(OAuthProvider.from("kakao")).isEqualTo(OAuthProvider.KAKAO);
        assertThat(OAuthProvider.from("GOOGLE")).isEqualTo(OAuthProvider.GOOGLE);
        assertThat(OAuthProvider.KAKAO.isEnabled()).isTrue();
        assertThat(OAuthProvider.NAVER.isEnabled()).isFalse();
    }
}
