package com.sunsetstar.forecast;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 근처 관측 장소 추천 API (기능명세서 F5). 로그인 없이 누구나 부를 수 있다.
 *
 * GET /api/nearby?lat=37.5665&lon=126.978&light=SUBURB&type=SUNSET&date=2026-11-16
 * → 현재 위치 점수와 더 좋은 곳 최대 3곳 (NearbyResponse)
 */
@Tag(name = "근처 장소", description = "현재 위치보다 노을·별이 더 잘 보일 근처 장소 추천")
@RestController
@RequestMapping("/api/nearby")
public class NearbyController {

    private static final String LAT_MESSAGE = "위도(lat)는 -90 ~ 90 사이여야 해요";
    private static final String LON_MESSAGE = "경도(lon)는 -180 ~ 180 사이여야 해요";

    private final NearbyService nearbyService;

    public NearbyController(NearbyService nearbyService) {
        this.nearbyService = nearbyService;
    }

    @Operation(summary = "근처 관측 장소 추천",
            description = "현재 위치보다 10점 이상 좋은 근처 장소를 최대 3곳 추천한다. 카카오가 실패해도 현재 위치 점수는 나온다")
    @GetMapping
    public NearbyResponse nearby(
            @Parameter(description = "위도", example = "37.5665")
            @RequestParam @DecimalMin(value = "-90", message = LAT_MESSAGE)
            @DecimalMax(value = "90", message = LAT_MESSAGE) double lat,
            @Parameter(description = "경도", example = "126.978")
            @RequestParam @DecimalMin(value = "-180", message = LON_MESSAGE)
            @DecimalMax(value = "180", message = LON_MESSAGE) double lon,
            @Parameter(description = "빛 공해 단계: CITY(도심) / SUBURB(교외) / RURAL(시골)")
            @RequestParam(defaultValue = "SUBURB") LightPollution light,
            @Parameter(description = "종류: SUNSET(노을) / STAR(별)")
            @RequestParam(defaultValue = "SUNSET") SkyType type,
            @Parameter(description = "날짜 yyyy-MM-dd (오늘부터 7일 안). 안 보내면 오늘")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate day = date != null ? date : LocalDate.now(SkyCalculator.ZONE);
        return nearbyService.nearby(lat, lon, light, type, day);
    }
}
