package com.sunsetstar.forecast;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 등급 5단계가 경계 점수에서 정확히 나뉘는지 확인한다.
 * 경계(84와 85, 29와 30처럼 등급이 바뀌는 점수)에서 틀리기 쉬워서 경계 양쪽을 모두 넣었다.
 */
class GradeTest {

    @ParameterizedTest(name = "{0}점 → {1}")
    @CsvSource({
            "100, MUST_SEE",
            "85,  MUST_SEE",
            "84,  WORTH_IT",
            "70,  WORTH_IT",
            "69,  SO_SO",
            "50,  SO_SO",
            "49,  NOT_GREAT",
            "30,  NOT_GREAT",
            "29,  REST",
            "0,   REST"
    })
    @DisplayName("점수에 맞는 등급이 나온다")
    void of(int score, Grade expected) {
        assertThat(Grade.of(score)).isEqualTo(expected);
    }

    @Test
    @DisplayName("등급 이름과 한 줄 문장이 명세서와 같다")
    void labels() {
        assertThat(Grade.of(75).label()).isEqualTo("볼 만해요");
        assertThat(Grade.of(75).sunsetLine()).isEqualTo("오늘 하늘 볼 만해요");
        assertThat(Grade.of(75).starLine()).isEqualTo("오늘 밤 별 볼 만해요");
    }

    @Test
    @DisplayName("0~100 밖의 점수는 에러가 난다")
    void outOfRange() {
        assertThatThrownBy(() -> Grade.of(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Grade.of(101)).isInstanceOf(IllegalArgumentException.class);
    }
}
