package com.sunsetstar.auth;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 회원가입 입력 규칙 테스트 (기능명세서 F9 입력 규칙).
 * 서버를 켜지 않고 SignupRequest에 붙은 규칙(@Email, @Size, @Pattern)만 검사한다.
 */
class SignupRequestTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    /** 규칙을 어긴 칸의 안내 문장 목록 */
    private Set<String> messages(SignupRequest request) {
        return validator.validate(request).stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toSet());
    }

    @Test
    void 규칙에_맞으면_통과() {
        assertThat(messages(new SignupRequest("sumin@example.com", "sunset2026", "노을러"))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"sumin", "sumin@", "@example.com", "sumin example.com"})
    void 이메일_형식이_틀리면_실패(String email) {
        assertThat(messages(new SignupRequest(email, "sunset2026", "노을러")))
                .containsExactly(AuthRules.EMAIL_INVALID);
    }

    @Test
    void 이메일이_비면_실패() {
        assertThat(messages(new SignupRequest(" ", "sunset2026", "노을러")))
                .contains(AuthRules.EMAIL_REQUIRED);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "abc123",        // 8자 미만
            "sunsetstar",    // 숫자 없음
            "12345678",      // 영문 없음
            "sunset 2026",   // 공백
            "노을sunset2026" // 한글
    })
    void 비밀번호_규칙을_어기면_실패(String password) {
        assertThat(messages(new SignupRequest("sumin@example.com", password, "노을러")))
                .containsExactly(AuthRules.PASSWORD_INVALID);
    }

    @Test
    void 비밀번호는_특수문자를_써도_된다() {
        assertThat(messages(new SignupRequest("sumin@example.com", "sunset!2026", "노을러"))).isEmpty();
    }

    @Test
    void 비밀번호가_64자를_넘으면_실패() {
        String password = "a1".repeat(33); // 66자
        assertThat(messages(new SignupRequest("sumin@example.com", password, "노을러")))
                .containsExactly(AuthRules.PASSWORD_INVALID);
    }

    @ParameterizedTest
    @ValueSource(strings = {"노", "열한글자닉네임입니다요"})
    void 닉네임이_2자_미만이거나_10자_초과면_실패(String nickname) {
        assertThat(messages(new SignupRequest("sumin@example.com", "sunset2026", nickname)))
                .containsExactly(AuthRules.NICKNAME_INVALID);
    }
}
