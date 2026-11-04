package com.hftm.gameshelf.domain;

/**
 * Status eines Eintrags. Die Namen entsprechen exakt den Werten im
 * CHECK-Constraint ck_eintrag_status (V1).
 */
public enum Status {
    GEPLANT,
    AM_SPIELEN,
    GESPIELT,
    ABGEBROCHEN;

    /** Nur diese Status beruhen auf echter Spielerfahrung (A6) und brauchen ein Rating (A2). */
    public boolean istBeendet() {
        return this == GESPIELT || this == ABGEBROCHEN;
    }
}
