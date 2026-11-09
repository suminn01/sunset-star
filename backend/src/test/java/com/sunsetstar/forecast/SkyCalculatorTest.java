package com.sunsetstar.forecast;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SkyCalculator가 해와 달을 맞게 계산하는지 확인한다. 인터넷이 필요 없다.
 *
 * 기준값
 * - 서울 천문박명 끝: 11월 9일 약 18:56, 6월 21일(하지) 약 21:52 (해 고도 공식으로 따로 계산한 값)
 * - 2026년 3월 3일: 개기월식이 있는 보름달
 * - 2026년 8월 12~13일: 개기일식이 있는 그믐(삭)
 * 계산 방법에 따라 몇 분씩 차이가 날 수 있어서 시각은 범위로 확인한다.
 */
class SkyCalculatorTest {

    private static final double SEOUL_LAT = 37.5665;
    private static final double SEOUL_LON = 126.9780;

    private final SkyCalculator sky = new SkyCalculator();

    @Test
    @DisplayName("서울 11월 9일 천문박명 끝은 18:40 ~ 19:10 사이 (약 18:56)")
    void duskInNovember() {
        LocalDateTime dusk = sky.astronomicalDusk(LocalDate.of(2026, 11, 9), SEOUL_LAT, SEOUL_LON);

        assertThat(dusk).isBetween(
                LocalDateTime.of(2026, 11, 9, 18, 40),
                LocalDateTime.of(2026, 11, 9, 19, 10));
    }

    @Test
    @DisplayName("하지(6월 21일)에는 천문박명 끝이 21:35 ~ 22:10 사이로 겨울보다 훨씬 늦다 (약 21:52)")
    void duskInSummer() {
        LocalDateTime dusk = sky.astronomicalDusk(LocalDate.of(2026, 6, 21), SEOUL_LAT, SEOUL_LON);

        assertThat(dusk).isBetween(
                LocalDateTime.of(2026, 6, 21, 21, 35),
                LocalDateTime.of(2026, 6, 21, 22, 10));
    }

    @Test
    @DisplayName("보름달(2026-03-03) 밤 21시: 거의 꽉 찬 달이 떠 있다")
    void fullMoonAtNight() {
        SkyCalculator.Moon moon = sky.moonAt(LocalDateTime.of(2026, 3, 3, 21, 0), SEOUL_LAT, SEOUL_LON);

        assertThat(moon.illumination()).isGreaterThan(0.95);
        assertThat(moon.isUp()).isTrue();
    }

    @Test
    @DisplayName("보름달은 해 반대편에 있어서 낮 12시에는 지평선 아래에 있다")
    void fullMoonAtNoon() {
        SkyCalculator.Moon moon = sky.moonAt(LocalDateTime.of(2026, 3, 3, 12, 0), SEOUL_LAT, SEOUL_LON);

        assertThat(moon.isUp()).isFalse();
    }

    @Test
    @DisplayName("그믐(2026-08-13 새벽 3시): 달 밝기가 거의 0이다")
    void newMoon() {
        SkyCalculator.Moon moon = sky.moonAt(LocalDateTime.of(2026, 8, 13, 3, 0), SEOUL_LAT, SEOUL_LON);

        assertThat(moon.illumination()).isLessThan(0.05);
    }

    @Test
    @DisplayName("보름달은 밤새 떠 있다가 다음 날 아침(05:30 ~ 08:30)에 진다")
    void fullMoonSetsInMorning() {
        LocalDateTime from = LocalDateTime.of(2026, 3, 3, 21, 0);

        LocalDateTime moonSet = sky.nextMoonSet(from, SEOUL_LAT, SEOUL_LON);

        assertThat(moonSet).isBetween(
                LocalDateTime.of(2026, 3, 4, 5, 30),
                LocalDateTime.of(2026, 3, 4, 8, 30));
    }

    @Test
    @DisplayName("해 지는 방향은 계절마다 다르다: 11월 약 249°, 춘분 약 270°, 하지 약 301°")
    void sunsetAzimuthBySeason() {
        double november = sky.sunAzimuth(LocalDateTime.of(2026, 11, 9, 17, 26), SEOUL_LAT, SEOUL_LON);
        double equinox = sky.sunAzimuth(LocalDateTime.of(2026, 3, 20, 18, 40), SEOUL_LAT, SEOUL_LON);
        double summer = sky.sunAzimuth(LocalDateTime.of(2026, 6, 21, 19, 57), SEOUL_LAT, SEOUL_LON);

        assertThat(november).isBetween(245.0, 253.0);
        assertThat(equinox).isBetween(266.0, 274.0);
        assertThat(summer).isBetween(297.0, 305.0);
    }

    @Test
    @DisplayName("방위각을 16방위 이름으로 바꾼다")
    void directionNames() {
        assertThat(SkyCalculator.directionName(249)).isEqualTo("서남서");
        assertThat(SkyCalculator.directionName(270)).isEqualTo("서");
        assertThat(SkyCalculator.directionName(292)).isEqualTo("서북서");
        assertThat(SkyCalculator.directionName(0)).isEqualTo("북");
        assertThat(SkyCalculator.directionName(359)).isEqualTo("북");
    }
}
