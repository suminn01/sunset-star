package com.sunsetstar.forecast;

/**
 * 무엇을 보러 가는지: 노을 또는 별. 근처 장소 추천(F5)은 종류마다 찾는 장소와 점수가 다르다.
 */
public enum SkyType {

    SUNSET("노을"),
    STAR("별");

    private final String label;

    SkyType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
