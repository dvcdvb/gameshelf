package com.hftm.gameshelf.dto.auswertung;

import java.util.List;

/** Auswertung 3: Verteilung der Liste eines Spielers. */
public record SpielerStatistikResponse(
        Long spielerId,
        List<StatusStatistik> proStatus,
        StatusStatistik gesamt
) {}
