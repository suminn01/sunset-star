package com.sunsetstar.global;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

/**
 * 캐시 설정. 캐시마다 보관 시간이 달라서 application.yml 대신 코드로 정한다.
 *
 * | 캐시 이름              | 보관 시간 | 내용                                   |
 * | open-meteo-forecast    | 1시간     | 날씨 (기능명세서 F6)                    |
 * | open-meteo-air-quality | 1시간     | 미세먼지                                |
 * | open-meteo-west-sky    | 1시간     | 해 지는 쪽 세 지점 낮은 구름 (F3)        |
 * | kakao-region           | 1시간     | 좌표 → 지역명 (F1)                      |
 * | nearby-candidates      | 하루      | 근처 장소 후보 목록 (F5, 카카오 호출 줄이기) |
 *
 * 이 Bean을 만들면 Spring Boot가 자동으로 만들던 캐시 관리자 대신 이것을 쓴다.
 */
@Configuration
public class CacheConfig {

    /** 좌표를 최대 몇 개까지 기억할지 (캐시마다) */
    private static final long MAX_ENTRIES = 1000;

    private static final Duration ONE_HOUR = Duration.ofHours(1);
    private static final Duration ONE_DAY = Duration.ofDays(1);

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        // 기본: 1시간 보관
        manager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(MAX_ENTRIES)
                .expireAfterWrite(ONE_HOUR));
        manager.setCacheNames(List.of(
                "open-meteo-forecast",
                "open-meteo-air-quality",
                "open-meteo-west-sky",
                "kakao-region"));
        // 근처 장소 후보는 장소가 자주 바뀌지 않아서 하루 보관
        manager.registerCustomCache("nearby-candidates", Caffeine.newBuilder()
                .maximumSize(MAX_ENTRIES)
                .expireAfterWrite(ONE_DAY)
                .build());
        return manager;
    }
}
