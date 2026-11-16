package com.sunsetstar.location;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 위치 API (기능명세서 F1). 로그인 없이 누구나 부를 수 있다.
 *
 * GET /api/locations/search?query=합정역       → 위치 검색 결과 목록
 * GET /api/locations/region?lat=37.55&lon=126.92 → "서울 마포구 서교동"
 */
@Tag(name = "위치", description = "위치 검색과 좌표 → 지역명 (카카오 로컬)")
@RestController
@RequestMapping("/api/locations")
public class LocationController {

    private static final String LAT_MESSAGE = "위도(lat)는 -90 ~ 90 사이여야 해요";
    private static final String LON_MESSAGE = "경도(lon)는 -180 ~ 180 사이여야 해요";

    private final LocationService locationService;

    public LocationController(LocationService locationService) {
        this.locationService = locationService;
    }

    @Operation(summary = "위치 검색", description = "장소 이름(합정역)이나 주소(망원동, 도로명 주소)로 검색해 좌표를 돌려준다")
    @GetMapping("/search")
    public LocationSearchResponse search(
            @Parameter(description = "검색어 (예: 합정역, 망원동, 월드컵로 212)", example = "합정역")
            @RequestParam @NotBlank(message = "검색어를 입력해 주세요")
            @Size(max = 50, message = "검색어는 50자까지 입력할 수 있어요") String query) {
        return locationService.search(query);
    }

    @Operation(summary = "좌표 → 지역명", description = "현재 위치 좌표를 \"서울 마포구 서교동\" 같은 이름으로 바꾼다")
    @GetMapping("/region")
    public RegionResponse region(
            @Parameter(description = "위도", example = "37.5665")
            @RequestParam @DecimalMin(value = "-90", message = LAT_MESSAGE)
            @DecimalMax(value = "90", message = LAT_MESSAGE) double lat,
            @Parameter(description = "경도", example = "126.978")
            @RequestParam @DecimalMin(value = "-180", message = LON_MESSAGE)
            @DecimalMax(value = "180", message = LON_MESSAGE) double lon) {
        return locationService.region(lat, lon);
    }
}
