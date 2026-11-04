package com.hftm.gameshelf.dto;

import com.hftm.gameshelf.domain.Status;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Pflicht: spielId und status. Rating ist bei GESPIELT/ABGEBROCHEN Pflicht (prüft der Service). */
public record EintragErstellenRequest(
        @NotNull Long spielId,
        @NotNull Status status,
        @Min(1) @Max(10) Short rating,
        @Size(max = 2000) String bewertungstext,
        @DecimalMin("0.0") @Digits(integer = 5, fraction = 1) BigDecimal spielzeitStd,
        LocalDate gestartetAm,
        LocalDate abgeschlossenAm
) {}
