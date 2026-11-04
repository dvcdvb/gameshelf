package com.hftm.gameshelf.dto.auswertung;

import java.math.BigDecimal;

/** Auswertung 2: Abbruchquote je Genre. */
public record GenreAbbruchResponse(
        Long genreId,
        String genre,
        long anzahlSpiele,        // verschiedene Spiele mit mind. einem beendeten Eintrag
        long anzahlBeendet,       // beendete Einträge (gespielt + abgebrochen)
        long anzahlAbgebrochen,
        BigDecimal abbruchquoteProzent
) {}
