package com.hftm.gameshelf;

import com.hftm.gameshelf.domain.Eintrag;
import com.hftm.gameshelf.domain.Status;
import com.hftm.gameshelf.dto.EintragAendernRequest;
import com.hftm.gameshelf.dto.EintragResponse;
import com.hftm.gameshelf.dto.StatusWechselnRequest;
import com.hftm.gameshelf.exception.KonfliktException;
import com.hftm.gameshelf.repository.EintragRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

/**
 * T5: Konkurrierende Änderungen mit zwei (bzw. mehreren) UNABHÄNGIGEN, gleichzeitig laufenden Zugriffen.
 * Jeder Zugriff läuft in einem eigenen Thread mit eigener DB-Verbindung und eigener Transaktion.
 */
class KonkurrenzTest extends AbstractIntegrationTest {

    @Autowired private EintragRepository eintragRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    private Long spielId;
    private Long spielerId;

    @BeforeEach
    void testdaten() {
        spielId = spiel("Hollow Knight", genre("Metroidvania"));
        spielerId = spieler("fabio");
    }

    @Test
    @DisplayName("Zwei Transaktionen lesen dieselbe Version und ändern gleichzeitig: genau eine gewinnt (@Version)")
    void optimisticLockingMitZweiTransaktionen() throws Exception {
        Long eintragId = eintrag(spielerId, spielId, Status.GESPIELT, 5).id();
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        CyclicBarrier beideHabenGelesen = new CyclicBarrier(2);

        // Handy und Laptop: beide laden Version 0, warten aufeinander, dann ändern beide
        List<Callable<Object>> geraete = List.of(
                () -> aendereRating(tx, beideHabenGelesen, eintragId, (short) 9),
                () -> aendereRating(tx, beideHabenGelesen, eintragId, (short) 2));

        List<Object> ergebnisse = gleichzeitigAusfuehren(geraete);

        List<Object> erfolge = ergebnisse.stream().filter(r -> r instanceof Short).toList();
        List<Object> konflikte = ergebnisse.stream().filter(r -> r instanceof OptimisticLockingFailureException).toList();
        assertThat(erfolge).as("genau eine Änderung gewinnt: %s", ergebnisse).hasSize(1);
        assertThat(konflikte).as("die andere wird als Konflikt erkannt: %s", ergebnisse).hasSize(1);

        // Endzustand: Wert des Gewinners, Version genau einmal erhöht -> nichts unbemerkt überschrieben
        EintragResponse endzustand = eintragService.laden(eintragId);
        assertThat(endzustand.rating()).isEqualTo(erfolge.getFirst());
        assertThat(endzustand.version()).isEqualTo(1L);
    }

    private Short aendereRating(TransactionTemplate tx, CyclicBarrier barriere, Long eintragId, short rating) {
        return tx.execute(status -> {
            Eintrag eintrag = eintragRepository.findById(eintragId).orElseThrow();   // liest Version 0
            try {
                barriere.await(10, TimeUnit.SECONDS);                                  // beide haben gelesen
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            eintrag.setRating(rating);
            eintragRepository.flush();   // UPDATE ... WHERE id = ? AND version = 0
            return rating;
        });
    }

    @Test
    @DisplayName("Zwei gleichzeitige API-Änderungen mit derselben Version: eine Erfolg, eine Konflikt")
    void gleichzeitigeAenderungUeberService() throws Exception {
        Long eintragId = eintrag(spielerId, spielId, Status.GESPIELT, 5).id();

        List<Object> ergebnisse = gleichzeitigAusfuehren(List.of(
                () -> eintragService.aendern(eintragId, new EintragAendernRequest((short) 9, null, null, null, null, 0L)),
                () -> eintragService.aendern(eintragId, new EintragAendernRequest((short) 2, null, null, null, null, 0L))));

        List<EintragResponse> erfolge = ergebnisse.stream()
                .filter(r -> r instanceof EintragResponse).map(r -> (EintragResponse) r).toList();
        // Je nach Timing erkennt der Versionsvergleich im Service (KonfliktException)
        // oder Hibernate beim UPDATE (OptimisticLockingFailureException) den Konflikt.
        long konflikte = ergebnisse.stream()
                .filter(r -> r instanceof KonfliktException || r instanceof OptimisticLockingFailureException).count();
        assertThat(erfolge).as("Ergebnisse: %s", ergebnisse).hasSize(1);
        assertThat(konflikte).isEqualTo(1);

        EintragResponse endzustand = eintragService.laden(eintragId);
        assertThat(endzustand.rating()).isEqualTo(erfolge.getFirst().rating());
        assertThat(endzustand.version()).isEqualTo(1L);
        assertThat(statistik(spielId)).containsExactly(1, endzustand.rating());   // Statistik passt zum Gewinner
    }

    @Test
    @DisplayName("8 Spieler schliessen gleichzeitig dasselbe Spiel ab: keine Bewertung geht verloren")
    void gleichzeitigeStatistikUpdates() throws Exception {
        int anzahlSpieler = 8;
        List<Long> eintragIds = new ArrayList<>();
        for (int i = 1; i <= anzahlSpieler; i++) {
            eintragIds.add(eintrag(spieler("spieler" + i), spielId, Status.AM_SPIELEN, null).id());
        }

        List<Callable<Object>> abschluesse = new ArrayList<>();
        for (int i = 0; i < anzahlSpieler; i++) {
            Long id = eintragIds.get(i);
            short rating = (short) (i + 1);   // Ratings 1..8
            abschluesse.add(() -> eintragService.statusWechseln(id,
                    new StatusWechselnRequest(Status.GESPIELT, rating, null, 0L)));
        }

        List<Object> ergebnisse = gleichzeitigAusfuehren(abschluesse);

        assertThat(ergebnisse).allMatch(r -> r instanceof EintragResponse, "alle erfolgreich");
        // 1+2+...+8 = 36. Mit "lesen, rechnen, speichern" könnten hier Bewertungen verloren gehen.
        assertThat(statistik(spielId)).containsExactly(8, 36);
    }

    @Test
    @DisplayName("Gleichzeitiges doppeltes Aufnehmen: Unique-Constraint verhindert zweiten Eintrag")
    void gleichzeitigesDoppeltesAufnehmen() throws Exception {
        List<Object> ergebnisse = gleichzeitigAusfuehren(List.of(
                () -> eintrag(spielerId, spielId, Status.GEPLANT, null),
                () -> eintrag(spielerId, spielId, Status.GEPLANT, null)));

        long erfolge = ergebnisse.stream().filter(r -> r instanceof EintragResponse).count();
        // Entweder erkennt die Vorabprüfung das Duplikat (KonfliktException) oder – wenn beide
        // gleichzeitig prüfen – der Unique-Constraint der Datenbank (DataIntegrityViolationException).
        long abgelehnt = ergebnisse.stream()
                .filter(r -> r instanceof KonfliktException || r instanceof DataIntegrityViolationException).count();
        assertThat(erfolge).as("Ergebnisse: %s", ergebnisse).isEqualTo(1);
        assertThat(abgelehnt).isEqualTo(1);
        assertThat(anzahl("eintrag")).isEqualTo(1);
    }
}
