package com.sunsetstar.observation;

import java.util.List;

/**
 * 내 기록 목록 한 페이지.
 *
 * @param items      이 페이지의 기록 (최신순, 최대 20개)
 * @param page       지금 페이지 번호 (0부터)
 * @param totalPages 전체 페이지 수
 * @param totalCount 전체 기록 수
 * @param hasNext    다음 페이지가 있는지. 프런트는 true면 "더 보기"를 보여 준다
 */
public record ObservationPageResponse(
        List<ObservationResponse> items,
        int page,
        int totalPages,
        long totalCount,
        boolean hasNext
) {
}
