package com.hftm.gameshelf.dto;

import com.hftm.gameshelf.domain.Spieler;

import java.time.OffsetDateTime;

public record SpielerResponse(Long id, String benutzername, OffsetDateTime erstelltAm) {

    public static SpielerResponse von(Spieler spieler) {
        return new SpielerResponse(spieler.getId(), spieler.getBenutzername(), spieler.getErstelltAm());
    }
}
