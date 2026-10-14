package com.evaluacion01.tecsup.audit;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

// Los instantes se guardan siempre en UTC; solo la presentación y los filtros por día usan Lima.
public final class ZonaHoraria {

    public static final ZoneId LIMA = ZoneId.of("America/Lima");

    private ZonaHoraria() {
    }

    public static LocalDateTime utcALima(LocalDateTime utc) {
        return utc == null ? null
                : utc.atOffset(ZoneOffset.UTC).atZoneSameInstant(LIMA).toLocalDateTime();
    }

    // Medianoche de ese día en Lima, expresada en UTC (para comparar con lo guardado).
    public static LocalDateTime inicioDelDiaEnUtc(LocalDate diaEnLima) {
        return diaEnLima.atStartOfDay(LIMA).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }
}
