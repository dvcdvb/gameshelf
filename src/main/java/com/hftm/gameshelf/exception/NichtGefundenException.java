package com.hftm.gameshelf.exception;

/** Gesuchter Datensatz existiert nicht -> HTTP 404. */
public class NichtGefundenException extends RuntimeException {
    public NichtGefundenException(String meldung) {
        super(meldung);
    }
}
