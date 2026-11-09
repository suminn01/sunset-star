package com.sunsetstar.forecast;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 예보 API의 주소(URL)를 정하고, 요청을 받아 ForecastService에 넘긴다.
 *
 * GET /api/forecast?lat=37.5665&lon=126.978&light=SUBURB
 * → 7일치 노을·별 점수 카드 (ForecastResponse)
 *
 * GET /api/forecast/detail?lat=37.5665&lon=126.978&light=SUBURB&date=2026-11-09
 * → 하루 상세: 노을 요소별 점수, 별 시간대별 점수, 달 정보 (ForecastDetailResponse)
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

    /**
     * 하루 상세 예보. 7일 카드를 눌렀을 때 쓴다.
     *
     * @param date 날짜 (yyyy-MM-dd, 오늘부터 7일 안)
     */
    @GetMapping("/detail")
    public ForecastDetailResponse detail(
            @RequestParam @DecimalMin("-90") @DecimalMax("90") double lat,
            @RequestParam @DecimalMin("-180") @DecimalMax("180") double lon,
            @RequestParam(defaultValue = "SUBURB") LightPollution light,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return forecastService.detail(lat, lon, light, date);
    }
}
