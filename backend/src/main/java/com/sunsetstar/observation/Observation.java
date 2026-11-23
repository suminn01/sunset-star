package com.sunsetstar.observation;

import com.sunsetstar.auth.User;
import com.sunsetstar.forecast.SkyType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 관측 기록 한 건 = observations 테이블의 한 줄 (기능명세서 DB 설계 observations).
 *
 * 회원과의 관계: 회원 한 명이 기록 여러 개를 가진다 (1:N).
 * 기록 쪽에서 보면 "여러 기록이 회원 하나에 속한다"라서 @ManyToOne이고, DB에는 user_id 칸으로 들어간다.
 * LAZY: 기록을 꺼낼 때 회원 정보까지 매번 같이 꺼내지 않고, 필요할 때만 꺼낸다.
 *
 * 예측 점수 스냅샷: 기록하는 순간 화면에 보이던 점수와 요소별 가감점을 그대로 저장한다.
 * 나중에 점수 공식을 바꿔도 "그때 앱이 뭐라고 예측했는지"는 바뀌지 않아야 적중률을 제대로 잴 수 있다.
 *
 * 인덱스 (user_id, observed_date): "내 기록을 날짜순으로" 찾는 조회가 많아서 둘을 묶어 색인을 만든다.
 */
@Entity
@Table(name = "observations",
        indexes = @Index(name = "idx_observations_user_date", columnList = "user_id, observed_date"))
public class Observation {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private LocalDate observedDate;

    /** SUNSET(노을) / STAR(별) */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private SkyType type;

    @Column(nullable = false, precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(nullable = false, precision = 9, scale = 6)
    private BigDecimal longitude;

    @Column(nullable = false)
    private int predictedScore;

    /** 요소별 가감점 스냅샷. 예: [{"name":"위쪽 구름","points":15,"reason":"..."}] (MySQL JSON 칸) */
    @Column(columnDefinition = "json")
    private String factors;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Rating rating;

    /** 서버 uploads 폴더 안의 사진 파일 이름. 사진이 없으면 비어 있다 */
    @Column(length = 255)
    private String photoPath;

    @Column(length = ObservationRules.MEMO_MAX)
    private String memo;

    @Column(length = ObservationRules.PLACE_NAME_MAX)
    private String placeName;

    /** F5에서 추천받은 장소의 카카오 장소 ID */
    @Column(length = ObservationRules.KAKAO_PLACE_ID_MAX)
    private String kakaoPlaceId;

    /** 추천 장소에서 봤는지 (추천 장소 만족도 계산용) */
    @Column(nullable = false)
    private boolean fromRecommendation;

    /** 알림을 눌러 들어와 기록했는지 (알림 후 관측 수 계산용) */
    @Column(nullable = false)
    private boolean fromNotification;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** JPA가 DB에서 읽어 올 때 쓰는 빈 생성자 */
    protected Observation() {
    }

    public Observation(User user, LocalDate observedDate, SkyType type,
                       BigDecimal latitude, BigDecimal longitude,
                       int predictedScore, String factors, Rating rating, String memo,
                       String placeName, String kakaoPlaceId,
                       boolean fromRecommendation, boolean fromNotification) {
        this.user = user;
        this.observedDate = observedDate;
        this.type = type;
        this.latitude = latitude;
        this.longitude = longitude;
        this.predictedScore = predictedScore;
        this.factors = factors;
        this.rating = rating;
        this.memo = memo;
        this.placeName = placeName;
        this.kakaoPlaceId = kakaoPlaceId;
        this.fromRecommendation = fromRecommendation;
        this.fromNotification = fromNotification;
        this.createdAt = LocalDateTime.now(SEOUL);
    }

    /** 사진 저장에 성공하면 파일 이름을 붙인다 */
    public void attachPhoto(String photoPath) {
        this.photoPath = photoPath;
    }

    /**
     * 예측이 맞았는지. SOSO는 판정하지 않는다(null).
     * 60점 이상 + GOOD, 또는 60점 미만 + BAD면 적중.
     */
    public Boolean isHit() {
        return switch (rating) {
            case GOOD -> predictedScore >= ObservationRules.HIT_SCORE;
            case BAD -> predictedScore < ObservationRules.HIT_SCORE;
            case SOSO -> null;
        };
    }

    /** 이 기록의 주인인지 */
    public boolean isOwnedBy(Long userId) {
        return user.getId().equals(userId);
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public LocalDate getObservedDate() { return observedDate; }
    public SkyType getType() { return type; }
    public BigDecimal getLatitude() { return latitude; }
    public BigDecimal getLongitude() { return longitude; }
    public int getPredictedScore() { return predictedScore; }
    public String getFactors() { return factors; }
    public Rating getRating() { return rating; }
    public String getPhotoPath() { return photoPath; }
    public String getMemo() { return memo; }
    public String getPlaceName() { return placeName; }
    public String getKakaoPlaceId() { return kakaoPlaceId; }
    public boolean isFromRecommendation() { return fromRecommendation; }
    public boolean isFromNotification() { return fromNotification; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
