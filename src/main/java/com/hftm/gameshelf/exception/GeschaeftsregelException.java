package com.hftm.gameshelf.exception;

/** Eingabe ist formal korrekt, verletzt aber eine fachliche Regel -> HTTP 400. */
public class GeschaeftsregelException extends RuntimeException {
    public GeschaeftsregelException(String meldung) {
        super(meldung);
    }
}
