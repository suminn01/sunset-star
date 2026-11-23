package com.sunsetstar.auth;

import com.sunsetstar.global.ApiException;
import com.sunsetstar.global.ErrorCode;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * 회원가입·로그인 규칙을 처리한다 (기능명세서 F9).
 *
 * 회원가입 순서
 * 1. 이메일은 앞뒤 공백을 빼고 소문자로 바꾼다 (Sumin@Example.com 과 sumin@example.com 을 같은 사람으로 본다)
 * 2. 이미 있는 이메일이면 409 DUPLICATE_EMAIL
 * 3. 비밀번호를 BCrypt로 암호화해서 저장한다. 입력한 비밀번호 그대로는 어디에도 남기지 않는다
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserResponse signup(SignupRequest request) {
        String email = normalizeEmail(request.email());
        String nickname = request.nickname().strip();
        if (nickname.length() < AuthRules.NICKNAME_MIN) {
            // " 가 " 처럼 공백을 빼면 2자가 안 되는 경우
            throw new ApiException(ErrorCode.INVALID_PARAMETER, AuthRules.NICKNAME_INVALID);
        }
        if (userRepository.existsByEmail(email)) {
            throw new ApiException(ErrorCode.DUPLICATE_EMAIL);
        }

        User user = User.local(email, passwordEncoder.encode(request.password()), nickname);
        try {
            return UserResponse.from(userRepository.saveAndFlush(user));
        } catch (DataIntegrityViolationException e) {
            // 같은 이메일로 거의 동시에 두 번 가입하면 위 검사를 둘 다 통과할 수 있다. 그때는 DB의 UNIQUE가 막아 준다
            throw new ApiException(ErrorCode.DUPLICATE_EMAIL);
        }
    }

    /** 이메일 비교 기준: 앞뒤 공백 제거 + 소문자 */
    static String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }
}
