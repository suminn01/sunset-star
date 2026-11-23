package com.sunsetstar.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 회원 한 명 = users 테이블의 한 줄 (기능명세서 DB 설계 users).
 *
 * @Entity가 붙은 클래스는 JPA가 테이블로 만들어 준다. application.yml의 ddl-auto: update 덕분에
 * 서버를 켜면 users 테이블이 없을 때 자동으로 생긴다. 칸 이름은 createdAt → created_at처럼 바뀐다.
 *
 * | 칸          | 타입           | 설명                                  |
 * | id          | BIGINT PK      | 자동 증가                              |
 * | email       | VARCHAR(100)   | 로그인 아이디, 중복 불가 (UNIQUE)       |
 * | password    | VARCHAR(60)    | BCrypt 암호화 값 (60자). 카카오 회원은 비어 있음 |
 * | nickname    | VARCHAR(10)    | 화면에 보이는 이름                      |
 * | provider    | VARCHAR(10)    | LOCAL / KAKAO                         |
 * | provider_id | VARCHAR(100)   | 카카오 회원 번호 (도전 기능)             |
 * | created_at  | DATETIME       | 가입 시각                              |
 */
@Entity
@Table(name = "users")
public class User {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(length = 60)
    private String password;

    @Column(nullable = false, length = 10)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Provider provider;

    @Column(length = 100)
    private String providerId;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** JPA가 DB에서 읽어 온 값을 채울 때 쓰는 빈 생성자. 우리 코드에서는 쓰지 않는다 */
    protected User() {
    }

    private User(String email, String password, String nickname, Provider provider) {
        this.email = email;
        this.password = password;
        this.nickname = nickname;
        this.provider = provider;
        this.createdAt = LocalDateTime.now(SEOUL);
    }

    /**
     * 이메일·비밀번호로 가입하는 회원을 만든다.
     *
     * @param encodedPassword 꼭 BCrypt로 암호화한 값을 넣는다. 입력한 비밀번호를 그대로 넣으면 안 된다
     */
    public static User local(String email, String encodedPassword, String nickname) {
        return new User(email, encodedPassword, nickname, Provider.LOCAL);
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    /** BCrypt 암호화 값. 응답으로 내보내지 않는다 */
    public String getPassword() {
        return password;
    }

    public String getNickname() {
        return nickname;
    }

    public Provider getProvider() {
        return provider;
    }

    public String getProviderId() {
        return providerId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
