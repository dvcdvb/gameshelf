package com.hftm.gameshelf.dto;

import com.hftm.gameshelf.domain.Eintrag;
import com.hftm.gameshelf.domain.Status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record EintragResponse(
        Long id,
        Long spielerId,
        Long spielId,
        String spielTitel,
        Status status,
        Short rating,
        String bewertungstext,
        BigDecimal spielzeitStd,
        LocalDate gestartetAm,
        LocalDate abgeschlossenAm,
        Long version,
        OffsetDateTime aktualisiertAm
) {

    public static EintragResponse von(Eintrag e) {
        return new EintragResponse(
                e.getId(),
                e.getSpieler().getId(),
                e.getSpiel().getId(),
                e.getSpiel().getTitel(),
                e.getStatus(),
                e.getRating(),
                e.getBewertungstext(),
                e.getSpielzeitStd(),
                e.getGestartetAm(),
                e.getAbgeschlossenAm(),
                e.getVersion(),
                e.getAktualisiertAm());
    }
}
