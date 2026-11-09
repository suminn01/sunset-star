package com.sunsetstar.forecast;

import org.shredzone.commons.suncalc.MoonIllumination;
import org.shredzone.commons.suncalc.MoonPosition;
import org.shredzone.commons.suncalc.MoonTimes;
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
 *
 * Open-Meteo는 일몰 시각만 주고 천문박명이나 달 정보는 주지 않아서 이 계산이 따로 필요하다.
 * 모든 시각은 한국 시간(Asia/Seoul)으로 주고받는다.
 */
@Component
public class SkyCalculator {

    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

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
