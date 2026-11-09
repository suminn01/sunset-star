package com.sunsetstar.global;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger 화면(/swagger-ui) 맨 위에 보이는 API 소개.
 *
 * @Configuration + @Bean: Spring에게 "이 객체를 만들어서 갖고 있어 줘"라고 알려 주는 방식이다.
 * springdoc이 이 OpenAPI 객체를 찾아서 화면 제목과 설명으로 쓴다.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI sunsetStarOpenApi() {
        return new OpenAPI().info(new Info()
                .title("노을별 API")
                .description("오늘의 노을·별 점수와 추천 시간을 알려 주는 API. 날씨 데이터: Open-Meteo")
                .version("v1"));
    }
}
