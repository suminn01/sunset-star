package com.sunsetstar.auth;

import com.sunsetstar.global.ApiException;
import com.sunsetstar.global.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 회원가입 규칙 테스트 (기능명세서 F9).
 *
 * 진짜 DB 대신 Mockito로 만든 가짜 UserRepository를 쓴다.
 * when(...).thenReturn(...)으로 "이렇게 물으면 이렇게 답해" 하고 정해 둘 수 있다.
 */
class AuthServiceTest {

    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        passwordEncoder = new BCryptPasswordEncoder();
        authService = new AuthService(userRepository, passwordEncoder);
        // 저장하면 받은 회원을 그대로 돌려준다
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void 가입하면_비밀번호는_암호화해서_저장한다() {
        UserResponse response = authService.signup(new SignupRequest("sumin@example.com", "sunset2026", "노을러"));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(saved.capture());
        User user = saved.getValue();

        assertThat(user.getPassword()).isNotEqualTo("sunset2026").startsWith("$2a$");
        assertThat(passwordEncoder.matches("sunset2026", user.getPassword())).isTrue();
        assertThat(user.getProvider()).isEqualTo(Provider.LOCAL);
        assertThat(response).isEqualTo(new UserResponse("sumin@example.com", "노을러"));
    }

    @Test
    void 이메일은_소문자로_닉네임은_앞뒤_공백을_빼고_저장한다() {
        UserResponse response = authService.signup(new SignupRequest(" Sumin@Example.COM ", "sunset2026", " 노을러 "));

        assertThat(response).isEqualTo(new UserResponse("sumin@example.com", "노을러"));
    }

    @Test
    void 이미_있는_이메일이면_409() {
        when(userRepository.existsByEmail("sumin@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.signup(new SignupRequest("Sumin@example.com", "sunset2026", "노을러")))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).errorCode())
                .isEqualTo(ErrorCode.DUPLICATE_EMAIL);
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void 동시에_같은_이메일로_가입해_DB가_막아도_409() {
        when(userRepository.saveAndFlush(any(User.class))).thenThrow(new DataIntegrityViolationException("Duplicate entry"));

        assertThatThrownBy(() -> authService.signup(new SignupRequest("sumin@example.com", "sunset2026", "노을러")))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).errorCode())
                .isEqualTo(ErrorCode.DUPLICATE_EMAIL);
    }

    @Test
    void 공백을_빼면_닉네임이_2자가_안_되면_400() {
        assertThatThrownBy(() -> authService.signup(new SignupRequest("sumin@example.com", "sunset2026", " 노 ")))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).errorCode())
                .isEqualTo(ErrorCode.INVALID_PARAMETER);
    }
}
