package com.hftm.gameshelf.dto.auswertung;

import java.math.BigDecimal;

/** Auswertung 1: bestbewertete Spiele. */
public record TopSpielResponse(
        Long spielId,
        String titel,
        String genre,
        long anzahlBewertungen,
        BigDecimal durchschnittsRating
) {}
