package com.sunsetstar.forecast;

import org.shredzone.commons.suncalc.MoonIllumination;
import org.shredzone.commons.suncalc.MoonPosition;
import org.shredzone.commons.suncalc.MoonTimes;
import org.shredzone.commons.suncalc.SunPosition;
import org.shredzone.commons.suncalc.SunTimes;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * 해와 달의 위치를 계산한다. 별 점수(기능명세서 F4)에 필요한 천문 정보를 commons-suncalc로 구한다.
 *
 * - astronomicalDusk: 천문박명이 끝나는 시각 = 하늘이 완전히 어두워지는 시각. 별 점수는 이때부터 매긴다
 * - moonAt: 그 시각의 달 밝기와 높이. 달이 밝고 떠 있으면 희미한 별이 안 보인다
 * - nextMoonSet: 달이 지는 시각. "달이 01:10에 지면 더 좋아요" 문장에 쓴다
 * - sunAzimuth: 그 시각 해의 방향. 일몰 시각에 쓰면 해 지는 방향이 된다 (노을 ③ 서쪽 하늘, F3)
 *
 * Open-Meteo는 일몰 시각만 주고 천문박명이나 달 정보는 주지 않아서 이 계산이 따로 필요하다.
 * 모든 시각은 한국 시간(Asia/Seoul)으로 주고받는다.
 */
@Component
public class SkyCalculator {

    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    /** 16방위 이름. 북쪽(0°)부터 시계 방향으로 22.5°씩 */
    private static final String[] DIRECTION_NAMES = {
            "북", "북북동", "북동", "동북동", "동", "동남동", "남동", "남남동",
            "남", "남남서", "남서", "서남서", "서", "서북서", "북서", "북북서"
    };

    /**
     * 그날 천문박명이 끝나는 시각 (해가 지평선 아래 18°로 내려가는 순간).
     * 예: 서울 11월 9일 → 약 18:56, 6월 21일 → 약 21:52
     *
     * @return 그날 천문박명이 끝나지 않으면 null (한국에서는 일어나지 않는다)
     */
    public LocalDateTime astronomicalDusk(LocalDate date, double latitude, double longitude) {
        SunTimes times = SunTimes.compute()
                .on(date.atStartOfDay(ZONE)) // 그날 0시부터
                .at(latitude, longitude)
                .twilight(SunTimes.Twilight.ASTRONOMICAL)
                .oneDay()                     // 24시간 안에서만 찾는다
                .execute();
        return toLocal(times.getSet());
    }

    /**
     * 그 시각의 달 상태.
     * 예: 보름달이 떠 있는 밤 21시 → 밝기 0.99, 높이 25°
     */
    public Moon moonAt(LocalDateTime time, double latitude, double longitude) {
        ZonedDateTime when = time.atZone(ZONE);

        double illumination = MoonIllumination.compute()
                .on(when)
                .execute()
                .getFraction();

        double altitude = MoonPosition.compute()
                .on(when)
                .at(latitude, longitude)
                .execute()
                .getAltitude();

        return new Moon(illumination, altitude);
    }

    /**
     * from 이후 24시간 안에 달이 지는 시각.
     *
     * @return 24시간 안에 달이 지지 않으면 null
     */
    public LocalDateTime nextMoonSet(LocalDateTime from, double latitude, double longitude) {
        MoonTimes times = MoonTimes.compute()
                .on(from.atZone(ZONE))
                .at(latitude, longitude)
                .oneDay()
                .execute();
        return toLocal(times.getSet());
    }

    /**
     * 그 시각 해의 방향(방위각). 북쪽 0°, 동쪽 90°, 남쪽 180°, 서쪽 270°.
     * commons-suncalc의 방위각이 이미 북쪽 기준이라 그대로 쓴다.
     * 예: 서울 일몰 때 11월 → 약 249°(서남서), 춘분 → 약 270°(서), 하지 → 약 301°(서북서)
     */
    public double sunAzimuth(LocalDateTime time, double latitude, double longitude) {
        return SunPosition.compute()
                .on(time.atZone(ZONE))
                .at(latitude, longitude)
                .execute()
                .getAzimuth();
    }

    /**
     * 방위각을 16방위 이름으로 바꾼다. 상세 화면의 "서북서 292°" 표시에 쓴다.
     * 예: 249° → "서남서", 292° → "서북서", 359° → "북"
     */
    public static String directionName(double azimuth) {
        int index = (int) Math.round(azimuth / 22.5) % DIRECTION_NAMES.length;
        return DIRECTION_NAMES[index];
    }

    private LocalDateTime toLocal(ZonedDateTime time) {
        if (time == null) {
            return null;
        }
        return time.withZoneSameInstant(ZONE).toLocalDateTime();
    }

    /**
     * 한 시각의 달 상태.
     *
     * @param illumination 달 밝기. 0.0 = 그믐(안 보임), 1.0 = 보름달
     * @param altitude     달의 높이(°). 0보다 크면 지평선 위에 떠 있다
     */
    public record Moon(double illumination, double altitude) {

        /** 달이 지평선 위에 떠 있는지 */
        public boolean isUp() {
            return altitude > 0;
        }
    }
}
