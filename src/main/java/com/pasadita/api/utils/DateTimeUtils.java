package com.pasadita.api.utils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public final class DateTimeUtils {

    public static final ZoneId MEXICO_ZONE = ZoneId.of("America/Mexico_City");

    public static final ZoneId UTC_ZONE = ZoneId.of("UTC");

    private DateTimeUtils() {
    }

    public static LocalDateTime toMexicoTime(LocalDateTime utcDateTime) {
        if (utcDateTime == null) {
            return null;
        }

        ZonedDateTime utcZoned = utcDateTime.atZone(UTC_ZONE);
        ZonedDateTime mexicoZoned = utcZoned.withZoneSameInstant(MEXICO_ZONE);
        return mexicoZoned.toLocalDateTime();
    }

    public static LocalDateTime toUtc(LocalDateTime mexicoDateTime) {
        if (mexicoDateTime == null) {
            return null;
        }

        ZonedDateTime mexicoZoned = mexicoDateTime.atZone(MEXICO_ZONE);
        ZonedDateTime utcZoned = mexicoZoned.withZoneSameInstant(UTC_ZONE);
        return utcZoned.toLocalDateTime();
    }

    public static LocalDateTime nowUtc() {
        return LocalDateTime.now(UTC_ZONE);
    }

    // Same as nowUtc() but from an injected Clock, so time-dependent rules can be tested with a fixed one
    public static LocalDateTime nowUtc(Clock clock) {
        return LocalDateTime.ofInstant(clock.instant(), UTC_ZONE);
    }

    public static LocalDateTime nowMexico() {
        return LocalDateTime.now(MEXICO_ZONE);
    }
}
