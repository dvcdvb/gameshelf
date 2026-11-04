package com.hftm.gameshelf.web;

/** Gemeinsame Obergrenze für die Seitengrösse (A5, A1): verhindert, dass ein Client eine riesige Seite anfordert. */
final class Seitenparameter {

    static final int MAX_GROESSE = 100;

    private Seitenparameter() {
    }
}
