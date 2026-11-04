package com.hftm.gameshelf.exception;

/** Änderung kollidiert mit vorhandenen Daten (Duplikat, veraltete Version) -> HTTP 409. */
public class KonfliktException extends RuntimeException {
    public KonfliktException(String meldung) {
        super(meldung);
    }
}
