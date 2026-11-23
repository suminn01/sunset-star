package com.sunsetstar.observation;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * observations 테이블에 저장하고 꺼낸다. 모든 조회는 "내 기록"만 꺼내도록 userId를 받는다.
 */
public interface ObservationRepository extends JpaRepository<Observation, Long> {

    /**
     * 내 기록 목록, 최신순 (관측한 날 → 같은 날이면 나중에 쓴 것 먼저).
     * Pageable로 "몇 번째 페이지, 몇 개씩"을 정한다.
     */
    Page<Observation> findByUserIdOrderByObservedDateDescIdDesc(Long userId, Pageable pageable);

    /** 통계 계산용: 내 기록 전부 */
    List<Observation> findByUserId(Long userId);
}
