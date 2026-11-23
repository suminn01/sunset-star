package com.sunsetstar.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * users 테이블에 저장하고 꺼내는 일을 맡는다.
 *
 * 인터페이스만 만들면 Spring Data JPA가 실제 코드를 알아서 만들어 준다.
 * - JpaRepository에 이미 있는 것: save(저장), findById(id로 찾기), delete(삭제) 등
 * - 메서드 이름으로 만드는 것: findByEmail → "SELECT * FROM users WHERE email = ?"
 */
public interface UserRepository extends JpaRepository<User, Long> {

    /** 로그인할 때 이메일로 회원을 찾는다. 없으면 빈 Optional */
    Optional<User> findByEmail(String email);

    /** 회원가입할 때 이미 쓰는 이메일인지 확인한다 */
    boolean existsByEmail(String email);
}
