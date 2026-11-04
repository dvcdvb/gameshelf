package com.hftm.gameshelf.service;

import com.hftm.gameshelf.config.CacheConfig;
import com.hftm.gameshelf.domain.*;
import com.hftm.gameshelf.dto.EintragAendernRequest;
import com.hftm.gameshelf.dto.EintragErstellenRequest;
import com.hftm.gameshelf.dto.EintragFilter;
import com.hftm.gameshelf.dto.EintragListenElement;
import com.hftm.gameshelf.dto.EintragSortierung;
import com.hftm.gameshelf.dto.SeiteResponse;
import com.hftm.gameshelf.dto.EintragResponse;
import com.hftm.gameshelf.dto.StatusWechselnRequest;
import com.hftm.gameshelf.dto.StatuswechselResponse;
import com.hftm.gameshelf.exception.GeschaeftsregelException;
import com.hftm.gameshelf.exception.KonfliktException;
import com.hftm.gameshelf.exception.NichtGefundenException;
import com.hftm.gameshelf.repository.EintragListeAbfrage;
import com.hftm.gameshelf.repository.EintragRepository;
import com.hftm.gameshelf.repository.SpielRepository;
import com.hftm.gameshelf.repository.SpielerRepository;
import com.hftm.gameshelf.repository.StatuswechselRepository;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;

@Service
public class EintragService {

    private final EintragRepository eintragRepository;
    private final SpielerRepository spielerRepository;
    private final SpielRepository spielRepository;
    private final StatuswechselRepository statuswechselRepository;
    private final EintragListeAbfrage eintragListeAbfrage;
    private final CacheManager cacheManager;

    public EintragService(EintragRepository eintragRepository,
                          SpielerRepository spielerRepository,
                          SpielRepository spielRepository,
                          StatuswechselRepository statuswechselRepository,
                          EintragListeAbfrage eintragListeAbfrage,
                          CacheManager cacheManager) {
        this.eintragRepository = eintragRepository;
        this.spielerRepository = spielerRepository;
        this.spielRepository = spielRepository;
        this.statuswechselRepository = statuswechselRepository;
        this.eintragListeAbfrage = eintragListeAbfrage;
        this.cacheManager = cacheManager;
    }

    /** A5: Liste eines Spielers – gefiltert, sortiert, seitenweise (alles in der DB). */
    @Transactional(readOnly = true)
    public SeiteResponse<EintragListenElement> liste(Long spielerId, EintragFilter filter,
                                                     EintragSortierung sortierung, Sort.Direction richtung,
                                                     int seite, int groesse) {
        if (!spielerRepository.existsById(spielerId)) {
            throw new NichtGefundenException("Spieler " + spielerId + " existiert nicht.");
        }
        if (filter.ratingMin() != null && filter.ratingMax() != null && filter.ratingMin() > filter.ratingMax()) {
            throw new GeschaeftsregelException("ratingMin darf nicht grösser als ratingMax sein.");
        }
        if ((filter.ratingMin() != null && (filter.ratingMin() < 1 || filter.ratingMin() > 10))
                || (filter.ratingMax() != null && (filter.ratingMax() < 1 || filter.ratingMax() > 10))) {
            throw new GeschaeftsregelException("ratingMin und ratingMax müssen zwischen 1 und 10 liegen.");
        }
        return SeiteResponse.von(
                eintragListeAbfrage.suchen(spielerId, filter, sortierung, richtung, seite, groesse),
                Function.identity());
    }

    /** A2: Spiel in die eigene Liste aufnehmen. Eintrag + erster Verlaufseintrag + Statistik in EINER Transaktion. */
    @Transactional
    public EintragResponse erstellen(Long spielerId, EintragErstellenRequest r) {
        Spieler spieler = spielerRepository.findById(spielerId)
                .orElseThrow(() -> new NichtGefundenException("Spieler " + spielerId + " existiert nicht."));
        Spiel spiel = spielRepository.findById(r.spielId())
                .orElseThrow(() -> new NichtGefundenException("Spiel " + r.spielId() + " existiert nicht."));

        if (!spiel.isAktiv()) {
            throw new GeschaeftsregelException("Das Spiel '" + spiel.getTitel() + "' ist deaktiviert und kann nicht aufgenommen werden.");
        }
        if (eintragRepository.existsBySpielerIdAndSpielId(spielerId, r.spielId())) {
            throw new KonfliktException("Das Spiel '" + spiel.getTitel() + "' ist bereits in der Liste.");
        }
        pruefeRegeln(r.status(), r.rating(), r.gestartetAm(), r.abgeschlossenAm());

        Eintrag eintrag = new Eintrag(spieler, spiel, r.status());
        eintrag.setRating(r.rating());
        eintrag.setBewertungstext(r.bewertungstext());
        eintrag.setSpielzeitStd(r.spielzeitStd());
        eintrag.setGestartetAm(r.gestartetAm());
        eintrag.setAbgeschlossenAm(r.abgeschlossenAm());
        eintragRepository.save(eintrag);

        statuswechselRepository.save(new Statuswechsel(eintrag, null, r.status()));
        statistikAnpassen(spiel.getId(), null, null, r.status(), r.rating());

        eintragRepository.flush(); // DB-Constraints jetzt prüfen und version/Zeitstempel für die Antwort setzen
        return EintragResponse.von(eintrag);
    }

    @Transactional(readOnly = true)
    public EintragResponse laden(Long id) {
        return EintragResponse.von(eintragLaden(id));
    }

    /** A3: Angaben ändern (ohne Status). */
    @Transactional
    public EintragResponse aendern(Long id, EintragAendernRequest r) {
        Eintrag eintrag = eintragLaden(id);
        if (!eintrag.getVersion().equals(r.version())) {
            throw new KonfliktException("Der Eintrag wurde inzwischen geändert (aktuelle Version "
                    + eintrag.getVersion() + ", gesendet " + r.version() + "). Bitte neu laden.");
        }
        pruefeRegeln(eintrag.getStatus(), r.rating(), r.gestartetAm(), r.abgeschlossenAm());

        Short altesRating = eintrag.getRating();
        eintrag.setRating(r.rating());
        eintrag.setBewertungstext(r.bewertungstext());
        eintrag.setSpielzeitStd(r.spielzeitStd());
        eintrag.setGestartetAm(r.gestartetAm());
        eintrag.setAbgeschlossenAm(r.abgeschlossenAm());

        statistikAnpassen(eintrag.getSpiel().getId(), eintrag.getStatus(), altesRating, eintrag.getStatus(), r.rating());

        eintragRepository.flush(); // Hibernate prüft hier auch @Version (gleichzeitige Änderung -> Exception)
        return EintragResponse.von(eintrag);
    }

    /**
     * A4 / T4: Kritische Geschäftsoperation "Status wechseln".
     *
     * Drei zusammengehörende Schreibzugriffe in EINER Transaktion:
     *   1. Eintrag aktualisieren (Status, Rating, Daten)
     *   2. Statuswechsel im Verlauf einfügen
     *   3. Spielstatistik anpassen
     * Schlägt einer davon fehl (z. B. ein CHECK-Constraint beim Anpassen der Statistik), wirft Spring
     * eine RuntimeException und rollt ALLES zurück, auch die bereits ausgeführten Schritte 1 und 2.
     */
    @Transactional
    public EintragResponse statusWechseln(Long id, StatusWechselnRequest r) {
        Eintrag eintrag = eintragLaden(id);
        if (!eintrag.getVersion().equals(r.version())) {
            throw new KonfliktException("Der Eintrag wurde inzwischen geändert (aktuelle Version "
                    + eintrag.getVersion() + ", gesendet " + r.version() + "). Bitte neu laden.");
        }

        Status alterStatus = eintrag.getStatus();
        Short altesRating = eintrag.getRating();
        Status neuerStatus = r.status();
        if (neuerStatus == alterStatus) {
            throw new GeschaeftsregelException("Der Eintrag hat bereits den Status " + alterStatus + ".");
        }

        // Neue Werte bestimmen
        Short neuesRating = r.rating() != null ? r.rating() : altesRating;
        LocalDate gestartetAm = eintrag.getGestartetAm();
        if (neuerStatus == Status.AM_SPIELEN && gestartetAm == null) {
            gestartetAm = LocalDate.now();            // Start wird automatisch erfasst
        }
        LocalDate abgeschlossenAm = null;             // bei GEPLANT / AM_SPIELEN kein Abschlussdatum (A3)
        if (neuerStatus.istBeendet()) {
            if (r.abgeschlossenAm() != null) {
                abgeschlossenAm = r.abgeschlossenAm();
            } else if (eintrag.getAbgeschlossenAm() != null) {
                abgeschlossenAm = eintrag.getAbgeschlossenAm();   // z. B. GESPIELT -> ABGEBROCHEN: Datum bleibt
            } else {
                abgeschlossenAm = LocalDate.now();
            }
        }
        pruefeRegeln(neuerStatus, neuesRating, gestartetAm, abgeschlossenAm);

        // Schreibzugriff 1: Eintrag. flush() schickt das UPDATE sofort an die DB, damit es wirklich
        // VOR den weiteren Schritten ausgeführt ist (wichtig für den Rollback-Nachweis in T4).
        eintrag.setStatus(neuerStatus);
        eintrag.setRating(neuesRating);
        eintrag.setGestartetAm(gestartetAm);
        eintrag.setAbgeschlossenAm(abgeschlossenAm);
        eintragRepository.flush();

        // Schreibzugriff 2: Verlauf
        statuswechselRepository.saveAndFlush(new Statuswechsel(eintrag, alterStatus, neuerStatus));

        // Schreibzugriff 3: Statistik (atomares UPDATE in der DB)
        statistikAnpassen(eintrag.getSpiel().getId(), alterStatus, altesRating, neuerStatus, neuesRating);

        return EintragResponse.von(eintrag);
    }

    /** A4: Verlauf eines Eintrags, chronologisch. */
    @Transactional(readOnly = true)
    public List<StatuswechselResponse> verlauf(Long eintragId) {
        if (!eintragRepository.existsById(eintragId)) {
            throw new NichtGefundenException("Eintrag " + eintragId + " existiert nicht.");
        }
        return statuswechselRepository.findByEintragIdOrderByGeaendertAmAscIdAsc(eintragId).stream()
                .map(StatuswechselResponse::von)
                .toList();
    }

    /** A3: Eintrag entfernen. Der Verlauf wird per ON DELETE CASCADE in der DB mitgelöscht. */
    @Transactional
    public void loeschen(Long id) {
        Eintrag eintrag = eintragLaden(id);
        statistikAnpassen(eintrag.getSpiel().getId(), eintrag.getStatus(), eintrag.getRating(), null, null);
        eintragRepository.delete(eintrag);
    }

    // ------------------------------------------------------------------

    private Eintrag eintragLaden(Long id) {
        return eintragRepository.findById(id)
                .orElseThrow(() -> new NichtGefundenException("Eintrag " + id + " existiert nicht."));
    }

    /**
     * Fachregeln mit verständlichen Meldungen. Die DB prüft dieselben Regeln nochmals per CHECK-Constraint,
     * falls jemand den Service umgeht.
     */
    static void pruefeRegeln(Status status, Short rating, LocalDate gestartetAm, LocalDate abgeschlossenAm) {
        if (status.istBeendet() && rating == null) {
            throw new GeschaeftsregelException("Bei Status " + status + " ist ein Rating von 1 bis 10 Pflicht.");
        }
        if (abgeschlossenAm != null && !status.istBeendet()) {
            throw new GeschaeftsregelException("Ein Abschlussdatum ist nur bei GESPIELT oder ABGEBROCHEN erlaubt.");
        }
        if (gestartetAm != null && abgeschlossenAm != null && abgeschlossenAm.isBefore(gestartetAm)) {
            throw new GeschaeftsregelException("Das Abschlussdatum darf nicht vor dem Startdatum liegen.");
        }
    }

    /**
     * Berechnet, wie sich die Spielstatistik durch die Änderung verschiebt (vorher -> nachher), und
     * schreibt nur die Differenz. In die Statistik zählen nur beendete Einträge (A6).
     * Ein Status von null bedeutet "Eintrag existiert (noch/nicht mehr) nicht".
     */
    private void statistikAnpassen(Long spielId, Status alterStatus, Short altesRating,
                                   Status neuerStatus, Short neuesRating) {
        boolean zaehlteVorher = alterStatus != null && alterStatus.istBeendet();
        boolean zaehltNachher = neuerStatus != null && neuerStatus.istBeendet();

        int anzahlDelta = (zaehltNachher ? 1 : 0) - (zaehlteVorher ? 1 : 0);
        int summeDelta = (zaehltNachher ? neuesRating : 0) - (zaehlteVorher ? altesRating : 0);

        if (anzahlDelta != 0 || summeDelta != 0) {
            // T12: Bewertungen ändern sich -> Top-Liste ist veraltet. Der Cache ist transaktionsbewusst:
            // geleert wird erst nach dem Commit, bei einem Rollback gar nicht.
            // Änderungen ohne Einfluss auf Bewertungen (z. B. GEPLANT -> AM_SPIELEN) leeren ihn nicht.
            cacheManager.getCache(CacheConfig.TOP_SPIELE).clear();
            spielRepository.statistikAnpassen(spielId, anzahlDelta, summeDelta);
        }
    }
}
