package com.hftm.gameshelf.dto.auswertung;

import com.hftm.gameshelf.domain.Status;

import java.math.BigDecimal;

/** Eine Zeile der Spielerstatistik; status = null bedeutet "alle Status zusammen". */
public record StatusStatistik(
        Status status,
        long anzahl,
        BigDecimal durchschnittsRating,
        BigDecimal spielzeitStd
) {}
