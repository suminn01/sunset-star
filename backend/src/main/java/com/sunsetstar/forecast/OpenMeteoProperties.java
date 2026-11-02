package com.sunsetstar.forecast;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * application.yml의 open-meteo.* 설정값을 담는 클래스.
 *
 * <pre>
 * open-meteo:
 *   forecast-url: https://api.open-meteo.com/v1/forecast              → forecastUrl
 *   air-quality-url: https://air-quality-api.open-meteo.com/v1/air-quality → airQualityUrl
 *   model: best_match                                                  → model
 * </pre>
 *
 * 주소와 모델명을 코드에 직접 쓰지 않고 여기서 꺼내 쓴다.
 * 기상청 데이터가 복구되면 application.yml의 model만 kma_seamless로 바꾸면 된다.
 *
 * @param forecastUrl   날씨 예보 API 주소 (구름, 습도, 시정, 강수, 일몰·일출)
 * @param airQualityUrl 대기질 API 주소 (PM2.5, PM10)
 * @param model         날씨 모델 이름 (기본 best_match)
 */
@Validated
@ConfigurationProperties(prefix = "open-meteo")
public record OpenMeteoProperties(
        @NotBlank String forecastUrl,
        @NotBlank String airQualityUrl,
        @NotBlank String model
) {
}
