package com.hftm.gameshelf.web;

import com.hftm.gameshelf.exception.GeschaeftsregelException;
import com.hftm.gameshelf.exception.KonfliktException;
import com.hftm.gameshelf.exception.NichtGefundenException;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Übersetzt Exceptions zentral in verständliche HTTP-Antworten im Format RFC 9457 (ProblemDetail).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** Verständliche Meldungen für die benannten Constraints aus V1__initial_schema.sql. */
    private static final Map<String, String> CONSTRAINT_MELDUNGEN = Map.ofEntries(
            Map.entry("uq_genre_name", "Dieses Genre existiert bereits."),
            Map.entry("uq_spiel_titel_lower", "Ein Spiel mit diesem Titel existiert bereits."),
            Map.entry("uq_spieler_benutzername", "Dieser Benutzername ist bereits vergeben."),
            Map.entry("uq_eintrag_spieler_spiel", "Dieses Spiel ist bereits in der Liste."),
            Map.entry("ck_eintrag_rating_pflicht", "Bei GESPIELT oder ABGEBROCHEN ist ein Rating Pflicht."),
            Map.entry("ck_eintrag_rating", "Das Rating muss zwischen 1 und 10 liegen."),
            Map.entry("ck_eintrag_datum", "Das Abschlussdatum darf nicht vor dem Startdatum liegen."),
            Map.entry("ck_eintrag_abschluss_status", "Ein Abschlussdatum ist nur bei GESPIELT oder ABGEBROCHEN erlaubt."),
            Map.entry("ck_eintrag_spielzeit", "Die Spielzeit darf nicht negativ sein."),
            Map.entry("ck_spiel_jahr", "Das Erscheinungsjahr muss zwischen 1950 und 2100 liegen."),
            Map.entry("ck_spiel_anzahl", "Die Spielstatistik wäre inkonsistent geworden."),
            Map.entry("ck_spiel_summe_plausibel", "Die Spielstatistik wäre inkonsistent geworden."),
            Map.entry("ck_sw_unterschiedlich", "Der neue Status muss sich vom aktuellen unterscheiden.")
    );

    @ExceptionHandler(NichtGefundenException.class)
    ProblemDetail nichtGefunden(NichtGefundenException ex) {
        return problem(HttpStatus.NOT_FOUND, "Nicht gefunden", ex.getMessage());
    }

    @ExceptionHandler(KonfliktException.class)
    ProblemDetail konflikt(KonfliktException ex) {
        return problem(HttpStatus.CONFLICT, "Konflikt", ex.getMessage());
    }

    @ExceptionHandler(GeschaeftsregelException.class)
    ProblemDetail geschaeftsregel(GeschaeftsregelException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Geschäftsregel verletzt", ex.getMessage());
    }

    /** Gleichzeitige Änderung, von Hibernate über @Version erkannt (T5). */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail optimisticLock(OptimisticLockingFailureException ex) {
        return problem(HttpStatus.CONFLICT, "Gleichzeitige Änderung",
                "Der Datensatz wurde gleichzeitig von jemand anderem geändert. Bitte neu laden und erneut versuchen.");
    }

    /** Bean Validation auf den Request-DTOs (@NotNull, @Size, ...). */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail ungueltigeEingabe(MethodArgumentNotValidException ex) {
        ProblemDetail p = problem(HttpStatus.BAD_REQUEST, "Ungültige Eingabe", "Mindestens ein Feld ist ungültig.");
        Map<String, String> felder = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(f -> felder.putIfAbsent(f.getField(), f.getDefaultMessage()));
        p.setProperty("felder", felder);
        return p;
    }

    /** Kaputtes JSON oder unbekannter Enum-Wert, z. B. "status": "FERTIG". */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail unlesbar(HttpMessageNotReadableException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Ungültiger Request",
                "Der Request-Body ist kein gültiges JSON oder enthält unbekannte Werte "
                        + "(Status: GEPLANT, AM_SPIELEN, GESPIELT, ABGEBROCHEN).");
    }

    /** Query-Parameter mit falschem Typ, z. B. sortierung=PREIS oder seite=abc. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail falscherParameter(MethodArgumentTypeMismatchException ex) {
        return problem(HttpStatus.BAD_REQUEST, "Ungültiger Parameter",
                "Der Wert '" + ex.getValue() + "' ist für den Parameter '" + ex.getName() + "' nicht gültig.");
    }

    /** Die Datenbank hat einen Constraint verletzt gesehen, z. B. bei zwei gleichzeitigen Requests. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail datenintegritaet(DataIntegrityViolationException ex) {
        String constraint = constraintName(ex);
        String meldung = constraint == null ? null : CONSTRAINT_MELDUNGEN.get(constraint);
        HttpStatus status = constraint != null && constraint.startsWith("uq_") ? HttpStatus.CONFLICT : HttpStatus.BAD_REQUEST;
        ProblemDetail p = problem(status, "Datenbankregel verletzt",
                meldung != null ? meldung : "Die Änderung verletzt eine Regel der Datenbank.");
        if (constraint != null) {
            p.setProperty("constraint", constraint);
        }
        return p;
    }

    private static String constraintName(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof ConstraintViolationException cve && cve.getConstraintName() != null) {
                return cve.getConstraintName().toLowerCase();
            }
        }
        return null;
    }

    private static ProblemDetail problem(HttpStatus status, String titel, String meldung) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(status, meldung);
        p.setTitle(titel);
        return p;
    }
}
