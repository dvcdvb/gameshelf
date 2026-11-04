package com.hftm.gameshelf.dto;

import com.hftm.gameshelf.domain.Status;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * A4: Status eines Eintrags wechseln.
 * rating:          optional; wird beim Abschliessen/Abbrechen gebraucht, falls noch keins gesetzt ist
 * abgeschlossenAm: optional; ohne Angabe wird beim Abschliessen/Abbrechen das heutige Datum gesetzt
 * version:         Pflicht; Version, die der Client zuletzt gesehen hat (Optimistic Locking)
 */
public record StatusWechselnRequest(
        @NotNull Status status,
        @Min(1) @Max(10) Short rating,
        LocalDate abgeschlossenAm,
        @NotNull Long version
) {}
