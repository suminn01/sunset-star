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
 *
 * 로그인
 * - 이메일이 없어도, 비밀번호가 틀려도 똑같이 401 LOGIN_FAILED. 어느 쪽이 틀렸는지 알려 주면
 *   남이 "이 이메일은 가입돼 있구나"를 알아낼 수 있어서다
 * - 이메일이 없을 때도 비밀번호 비교를 한 번 해서, 응답 시간으로도 구분되지 않게 한다
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    /** 없는 이메일로 로그인할 때 비교에 쓰는 아무 암호화 값 */
    private final String dummyPassword;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.dummyPassword = passwordEncoder.encode("sunset-star-dummy-password");
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

    /** 이메일·비밀번호가 맞으면 로그인할 사람 정보를, 틀리면 401 LOGIN_FAILED */
    @Transactional(readOnly = true)
    public LoginUser authenticate(LoginRequest request) {
        User user = userRepository.findByEmail(normalizeEmail(request.email())).orElse(null);
        String savedPassword = user != null && user.getPassword() != null ? user.getPassword() : dummyPassword;
        boolean matches = passwordEncoder.matches(request.password(), savedPassword);
        if (user == null || user.getPassword() == null || !matches) {
            throw new ApiException(ErrorCode.LOGIN_FAILED);
        }
        return LoginUser.from(user);
    }

    /** 내 정보. 세션은 남아 있는데 회원이 사라졌다면 로그인이 안 된 것으로 본다 (401) */
    @Transactional(readOnly = true)
    public UserResponse me(Long userId) {
        return userRepository.findById(userId)
                .map(UserResponse::from)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));
    }

    /** 이메일 비교 기준: 앞뒤 공백 제거 + 소문자 */
    static String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }
}
