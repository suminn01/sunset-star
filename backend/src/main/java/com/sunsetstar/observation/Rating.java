package com.sunsetstar.observation;

/**
 * 직접 보고 매긴 평가 (기능명세서 F7). observations.rating 칸에 이름(GOOD 등)으로 저장된다.
 */
public enum Rating {
    GOOD("예뻤어요"),
    SOSO("그냥 그래요"),
    BAD("별로였어요");

    private final String label;

    Rating(String label) {
        this.label = label;
    }

    /** 화면에 보여 줄 문장 */
    public String label() {
        return label;
    }
}
