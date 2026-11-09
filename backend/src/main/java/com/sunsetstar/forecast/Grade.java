package com.sunsetstar.forecast;

/**
 * 점수 등급 5단계 (기능명세서 F6 등급 기준). 노을과 별이 같은 등급표를 쓴다.
 *
 * | 점수   | 등급      |
 * | 85~100 | 꼭 보세요 |
 * | 70~84  | 볼 만해요 |
 * | 50~69  | 그럭저럭  |
 * | 30~49  | 아쉬워요  |
 * | 0~29   | 쉬어 가요 |
 *
 * 점수가 높은 등급부터 적어 두고, 위에서부터 "최저 점수 이상인가?"를 확인해 처음 맞는 등급을 고른다.
 */
public enum Grade {

    MUST_SEE(85, "꼭 보세요", "오늘 노을 놓치지 마세요!", "오늘 밤 별 놓치지 마세요!"),
    WORTH_IT(70, "볼 만해요", "오늘 하늘 볼 만해요", "오늘 밤 별 볼 만해요"),
    SO_SO(50, "그럭저럭", "기대는 조금만 해 주세요", "밝은 별 몇 개는 볼 수 있어요"),
    NOT_GREAT(30, "아쉬워요", "오늘은 조금 아쉬운 하늘이에요", "오늘 밤은 조금 아쉬워요"),
    REST(0, "쉬어 가요", "오늘은 쉬어 가는 날이에요", "오늘 밤은 쉬어 가는 날이에요");

    private final int minScore;
    private final String label;
    private final String sunsetLine;
    private final String starLine;

    Grade(int minScore, String label, String sunsetLine, String starLine) {
        this.minScore = minScore;
        this.label = label;
        this.sunsetLine = sunsetLine;
        this.starLine = starLine;
    }

    /**
     * 점수에 맞는 등급을 찾는다. 예: of(75) → WORTH_IT(볼 만해요)
     *
     * @throws IllegalArgumentException 점수가 0~100 밖일 때
     */
    public static Grade of(int score) {
        if (score < 0 || score > 100) {
            throw new IllegalArgumentException("점수는 0~100이어야 합니다: " + score);
        }
        for (Grade grade : values()) {
            if (score >= grade.minScore) {
                return grade;
            }
        }
        // 위에서 0~100을 확인했고 REST가 0점부터라 여기까지 오지 않는다.
        throw new IllegalStateException("등급을 찾지 못했습니다: " + score);
    }

    /** 이 등급이 시작되는 점수 (예: 볼 만해요 → 70) */
    public int minScore() {
        return minScore;
    }

    /** 화면에 보여 줄 등급 이름 (예: "볼 만해요") */
    public String label() {
        return label;
    }

    /** 노을 카드의 한 줄 (예: "오늘 하늘 볼 만해요") */
    public String sunsetLine() {
        return sunsetLine;
    }

    /** 별 카드의 한 줄 (예: "오늘 밤 별 볼 만해요") */
    public String starLine() {
        return starLine;
    }
}
