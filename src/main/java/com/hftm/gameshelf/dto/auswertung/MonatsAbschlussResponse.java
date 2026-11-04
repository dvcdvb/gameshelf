package com.hftm.gameshelf.dto.auswertung;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Auswertung 4: abgeschlossene Spiele pro Monat. monat = erster Tag des Monats. */
public record MonatsAbschlussResponse(
        LocalDate monat,
        long anzahl,
        BigDecimal durchschnittSpielzeitStd
) {}
