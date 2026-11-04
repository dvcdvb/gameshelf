package com.hftm.gameshelf.dto;

import com.hftm.gameshelf.domain.Spiel;

public record SpielResponse(
        Long id,
        String titel,
        Long genreId,
        String genreName,
        Short erscheinungsjahr,
        boolean aktiv,
        int anzahlBewertungen,
        Double durchschnittsRating
) {

    /** Erwartet, dass das Genre bereits geladen ist (siehe @EntityGraph im SpielRepository). */
    public static SpielResponse von(Spiel spiel) {
        return new SpielResponse(
                spiel.getId(),
                spiel.getTitel(),
                spiel.getGenre().getId(),
                spiel.getGenre().getName(),
                spiel.getErscheinungsjahr(),
                spiel.isAktiv(),
                spiel.getAnzahlBewertungen(),
                spiel.durchschnittsRating());
    }
}
