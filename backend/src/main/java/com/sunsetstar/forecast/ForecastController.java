package com.sunsetstar.forecast;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 예보 API의 주소(URL)를 정하고, 요청을 받아 ForecastService에 넘긴다.
 *
 * GET /api/forecast?lat=37.5665&lon=126.978&light=SUBURB
 * → 7일치 노을·별 점수 카드 (ForecastResponse, JSON)
 *
 * Controller는 "어떤 주소로 무엇을 받는지"만 정하고, 계산은 하지 않는다.
 * 로그인 없이 누구나 부를 수 있다 (기능명세서 F9 접근 제어).
 */
@RestController
@RequestMapping("/api/forecast")
public class ForecastController {

    private final ForecastService forecastService;

    public ForecastController(ForecastService forecastService) {
        this.forecastService = forecastService;
    }

    /**
     * 7일 예보.
     *
     * @param lat   위도 (−90 ~ 90)
     * @param lon   경도 (−180 ~ 180)
     * @param light 빛 공해 단계: CITY(도심) / SUBURB(교외) / RURAL(시골). 안 보내면 SUBURB
     */
    @GetMapping
    public ForecastResponse forecast(
            @RequestParam @DecimalMin("-90") @DecimalMax("90") double lat,
            @RequestParam @DecimalMin("-180") @DecimalMax("180") double lon,
            @RequestParam(defaultValue = "SUBURB") LightPollution light) {
        return forecastService.forecast(lat, lon, light);
    }
}
