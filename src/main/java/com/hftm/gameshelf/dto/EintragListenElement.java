package com.hftm.gameshelf.dto;

import com.hftm.gameshelf.domain.Status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Zeile der Spielerliste (A5). Wird direkt per JPQL-Konstruktor-Projektion befüllt
 * ("select new ..."), es werden also keine Entities geladen (T6).
 * Die Reihenfolge der Parameter muss exakt zur Abfrage in EintragListeAbfrage passen.
 */
public record EintragListenElement(
        Long eintragId,
        Long spielId,
        String spielTitel,
        String genre,
        Status status,
        Short rating,
        BigDecimal spielzeitStd,
        LocalDate gestartetAm,
        LocalDate abgeschlossenAm,
        OffsetDateTime aktualisiertAm
) {}
