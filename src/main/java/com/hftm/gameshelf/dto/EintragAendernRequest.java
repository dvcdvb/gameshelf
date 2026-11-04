package com.hftm.gameshelf.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Ändert die Angaben eines Eintrags (nicht den Status, dafür gibt es in Schritt 4 eine eigene Operation).
 * Die version muss mitgeschickt werden: So erkennt der Server, ob der Client mit veralteten Daten arbeitet.
 */
public record EintragAendernRequest(
        @Min(1) @Max(10) Short rating,
        @Size(max = 2000) String bewertungstext,
        @DecimalMin("0.0") @Digits(integer = 5, fraction = 1) BigDecimal spielzeitStd,
        LocalDate gestartetAm,
        LocalDate abgeschlossenAm,
        @NotNull Long version
) {}
