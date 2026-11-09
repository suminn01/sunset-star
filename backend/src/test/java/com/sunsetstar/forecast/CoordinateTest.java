package com.sunsetstar.forecast;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Coordinate.destination: 어느 방향으로 몇 km 간 지점 계산.
 * 기준값은 같은 공식(대권 항법)으로 따로 계산한 값이다.
 */
class CoordinateTest {

    private static final Coordinate SEOUL = new Coordinate(37.5665, 126.978);

    @Test
    @DisplayName("서쪽(270°)으로 100km → 경도가 약 1.13° 줄어든다 (37.561, 125.844)")
    void west100km() {
        Coordinate result = SEOUL.destination(270, 100);

        assertThat(result.latitude()).isCloseTo(37.5611, within(0.001));
        assertThat(result.longitude()).isCloseTo(125.8435, within(0.001));
    }

    @Test
    @DisplayName("북쪽(0°)으로 100km → 위도만 약 0.9° 늘어난다 (38.466, 126.978)")
    void north100km() {
        Coordinate result = SEOUL.destination(0, 100);

        assertThat(result.latitude()).isCloseTo(38.4658, within(0.001));
        assertThat(result.longitude()).isCloseTo(126.978, within(0.001));
    }

    @Test
    @DisplayName("서남서(245°)로 25·50·100km 지점이 차례로 멀어진다")
    void westSouthWestPoints() {
        assertThat(SEOUL.destination(245, 25).longitude()).isCloseTo(126.7213, within(0.001));
        assertThat(SEOUL.destination(245, 50).longitude()).isCloseTo(126.4652, within(0.001));
        assertThat(SEOUL.destination(245, 100).longitude()).isCloseTo(125.955, within(0.001));
    }

    @Test
    @DisplayName("0km를 가면 제자리다")
    void zeroDistance() {
        Coordinate result = SEOUL.destination(270, 0);

        assertThat(result.latitude()).isCloseTo(37.5665, within(0.000001));
        assertThat(result.longitude()).isCloseTo(126.978, within(0.000001));
    }
}
