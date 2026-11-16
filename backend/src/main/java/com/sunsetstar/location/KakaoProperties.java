package com.sunsetstar.location;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 카카오 로컬 API 설정값 (application.yml과 application-secret.yml의 kakao.*).
 *
 * <pre>
 * kakao:
 *   base-url: https://dapi.kakao.com          ← application.yml (커밋됨)
 *   rest-api-key: 1a2b3c...                   ← application-secret.yml (커밋되지 않음)
 * </pre>
 *
 * REST API 키는 비밀번호와 같아서 코드와 GitHub에 올리지 않는다 (CLAUDE.md 규칙).
 * 키가 비어 있으면 서버가 켜질 때 바로 알려 준다.
 *
 * @param baseUrl    카카오 API 주소
 * @param restApiKey 카카오 REST API 키
 */
@Validated
@ConfigurationProperties(prefix = "kakao")
public record KakaoProperties(
        @NotBlank String baseUrl,
        @NotBlank(message = "카카오 REST API 키가 비어 있어요. application-secret.yml에 kakao.rest-api-key를 넣어 주세요")
        String restApiKey
) {

    /**
     * 로그나 에러 메시지에 객체가 찍혀도 키가 그대로 보이지 않게 가린다.
     * record는 기본으로 모든 값을 보여 주는 toString을 만들어서, 직접 바꿔 둔다.
     */
    @Override
    public String toString() {
        return "KakaoProperties[baseUrl=" + baseUrl + ", restApiKey=****]";
    }
}
