package com.hftm.gameshelf.dto;

/**
 * Erlaubte Sortierungen der Spielerliste. Bewusst eine feste Liste ("Whitelist"):
 * Der Client wählt einen Namen, der JPQL-Ausdruck kommt aus dem Code. So kann über den
 * Sortierparameter kein beliebiger Text in die Abfrage gelangen.
 */
public enum EintragSortierung {
    AKTUALISIERT("e.aktualisiertAm"),
    RATING("e.rating"),
    TITEL("lower(s.titel)"),
    ABGESCHLOSSEN("e.abgeschlossenAm");

    private final String jpqlAusdruck;

    EintragSortierung(String jpqlAusdruck) {
        this.jpqlAusdruck = jpqlAusdruck;
    }

    public String jpqlAusdruck() {
        return jpqlAusdruck;
    }
}
