package com.pasadita.api.utils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Utilidad para conversión de fechas/horas entre zonas horarias.
 * La base de datos almacena en UTC, esta utilidad convierte a la zona horaria de México.
 */
public final class DateTimeUtils {

    /**
     * Zona horaria de México (Ciudad de México - CST/CDT)
     */
    public static final ZoneId MEXICO_ZONE = ZoneId.of("America/Mexico_City");

    /**
     * Zona horaria UTC (base de datos)
     */
    public static final ZoneId UTC_ZONE = ZoneId.of("UTC");

    private DateTimeUtils() {
        // Clase utilitaria, no instantiable
    }

    /**
     * Convierte un LocalDateTime de UTC a hora de México.
     *
     * @param utcDateTime fecha/hora en UTC (como se almacena en la base de datos)
     * @return fecha/hora convertida a zona horaria de México, o null si el input es null
     */
    public static LocalDateTime toMexicoTime(LocalDateTime utcDateTime) {
        if (utcDateTime == null) {
            return null;
        }

        ZonedDateTime utcZoned = utcDateTime.atZone(UTC_ZONE);
        ZonedDateTime mexicoZoned = utcZoned.withZoneSameInstant(MEXICO_ZONE);
        return mexicoZoned.toLocalDateTime();
    }

    /**
     * Convierte un LocalDateTime de hora de México a UTC.
     * Útil para guardar datos que vienen del frontend en hora de México.
     *
     * @param mexicoDateTime fecha/hora en hora de México
     * @return fecha/hora convertida a UTC para almacenar en la base de datos, o null si el input es null
     */
    public static LocalDateTime toUtc(LocalDateTime mexicoDateTime) {
        if (mexicoDateTime == null) {
            return null;
        }

        ZonedDateTime mexicoZoned = mexicoDateTime.atZone(MEXICO_ZONE);
        ZonedDateTime utcZoned = mexicoZoned.withZoneSameInstant(UTC_ZONE);
        return utcZoned.toLocalDateTime();
    }

    /**
     * Obtiene la fecha/hora actual en UTC.
     * Usar este método en lugar de LocalDateTime.now() para mantener consistencia.
     *
     * @return fecha/hora actual en UTC
     */
    public static LocalDateTime nowUtc() {
        return LocalDateTime.now(UTC_ZONE);
    }

    /**
     * Obtiene la fecha/hora actual en hora de México.
     *
     * @return fecha/hora actual en hora de México
     */
    public static LocalDateTime nowMexico() {
        return LocalDateTime.now(MEXICO_ZONE);
    }
}
