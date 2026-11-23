package com.sunsetstar.auth;

/**
 * 회원가입 입력 규칙 숫자와 안내 문장 (기능명세서 F9 입력 규칙).
 *
 * | 항목     | 규칙                                   | 실패 시       |
 * | 이메일   | 이메일 형식, 100자 이하, 중복 불가        | 400 / 중복 409 |
 * | 비밀번호 | 8~64자, 영문과 숫자를 둘 다 포함          | 400           |
 * | 닉네임   | 2~10자 (앞뒤 공백은 빼고 센다), 중복 허용 | 400           |
 *
 * 비밀번호 최대 64자: BCrypt는 72바이트까지만 암호화할 수 있어서 넉넉히 64자로 막았다.
 * 같은 이유로 한글·공백은 받지 않는다(한글 한 글자가 3바이트). 영문·숫자·특수문자만 쓴다.
 */
public final class AuthRules {

    public static final int EMAIL_MAX = 100;

    public static final int PASSWORD_MIN = 8;
    public static final int PASSWORD_MAX = 64;
    /** 영문 1개 이상 + 숫자 1개 이상, 공백 없는 영문·숫자·특수문자만 */
    public static final String PASSWORD_PATTERN = "^(?=.*[A-Za-z])(?=.*\\d)[\\x21-\\x7E]+$";

    public static final int NICKNAME_MIN = 2;
    public static final int NICKNAME_MAX = 10;

    public static final String EMAIL_REQUIRED = "이메일을 입력해 주세요";
    public static final String EMAIL_INVALID = "이메일 형식이 올바르지 않아요";
    public static final String EMAIL_TOO_LONG = "이메일은 100자 이하로 입력해 주세요";
    public static final String PASSWORD_INVALID = "비밀번호는 8~64자이고 영문과 숫자를 둘 다 넣어 주세요 (한글·공백 불가)";
    public static final String NICKNAME_INVALID = "닉네임은 2~10자로 입력해 주세요";
    public static final String LOGIN_REQUIRED = "이메일과 비밀번호를 입력해 주세요";

    private AuthRules() {
    }
}
