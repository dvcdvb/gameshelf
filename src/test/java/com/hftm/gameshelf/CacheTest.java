package com.hftm.gameshelf;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import com.hftm.gameshelf.config.CacheConfig;
import com.hftm.gameshelf.domain.Status;
import com.hftm.gameshelf.dto.SpielRequest;
import com.hftm.gameshelf.dto.StatusWechselnRequest;
import com.hftm.gameshelf.dto.auswertung.TopSpielResponse;
import com.hftm.gameshelf.service.AuswertungService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * T12: Cache der Top-Spiele-Auswertung.
 * Nachweis über die Caffeine-Statistik (Treffer / Fehlzugriffe) und über den gelieferten Inhalt.
 */
class CacheTest extends AbstractIntegrationTest {

    @Autowired private AuswertungService auswertungService;

    private Long genreId, spielA, spielB, p1, p2;

    @BeforeEach
    void testdaten() {
        genreId = genre("RPG");
        spielA = spiel("Spiel A", genreId);
        spielB = spiel("Spiel B", genreId);
        p1 = spieler("p1");
        p2 = spieler("p2");
        eintrag(p1, spielA, Status.GESPIELT, 8);
        eintrag(p1, spielB, Status.GESPIELT, 6);
        cacheManager.getCache(CacheConfig.TOP_SPIELE).clear();   // Testdaten haben den Cache bereits geleert
    }

    @SuppressWarnings("unchecked")
    private CacheStats statistik() {
        return ((Cache<Object, Object>) cacheManager.getCache(CacheConfig.TOP_SPIELE).getNativeCache()).stats();
    }

    private List<TopSpielResponse> top() {
        return auswertungService.topSpiele(1, genreId, 10);
    }

    @Test
    @DisplayName("Erster Zugriff lädt aus der DB, zweiter Zugriff ist ein Cache-Treffer")
    void ersterZugriffUndTreffer() {
        CacheStats vorher = statistik();

        List<TopSpielResponse> erster = top();
        List<TopSpielResponse> zweiter = top();

        CacheStats nachher = statistik().minus(vorher);
        assertThat(nachher.missCount()).as("1. Zugriff: nicht im Cache").isEqualTo(1);
        assertThat(nachher.hitCount()).as("2. Zugriff: Treffer").isEqualTo(1);
        assertThat(zweiter).isSameAs(erster);   // dasselbe Objekt aus dem Cache, keine neue Abfrage
        assertThat(erster).extracting(TopSpielResponse::spielId).containsExactly(spielA, spielB);
    }

    @Test
    @DisplayName("Cache-Treffer liefert wirklich den gespeicherten Stand (Änderung an der API vorbei bleibt unsichtbar)")
    void trefferKommtNichtAusDerDatenbank() {
        top();   // füllt den Cache: A = 8.00

        // Direkt in der DB ändern, an der Anwendung vorbei (wie das Testdaten-Skript)
        jdbc.sql("UPDATE eintrag SET rating = 1 WHERE spiel_id = ?").param(spielA).update();

        // Ergebnis kommt aus dem Cache -> alter Stand. Genau dafür gibt es die TTL von 10 Minuten.
        assertThat(top().getFirst().durchschnittsRating()).isEqualByComparingTo("8.00");
    }

    @Test
    @DisplayName("Relevante Änderung über die API (neue Bewertung) leert den Cache -> nächster Zugriff ist aktuell")
    void neueBewertungLeertCache() {
        assertThat(top().getFirst().durchschnittsRating()).isEqualByComparingTo("8.00");
        CacheStats vorher = statistik();

        eintrag(p2, spielA, Status.GESPIELT, 10);   // A: (8 + 10) / 2 = 9.00

        List<TopSpielResponse> nachAenderung = top();
        assertThat(statistik().minus(vorher).missCount()).as("nach Änderung neu geladen").isEqualTo(1);
        assertThat(nachAenderung.getFirst().spielId()).isEqualTo(spielA);
        assertThat(nachAenderung.getFirst().anzahlBewertungen()).isEqualTo(2);
        assertThat(nachAenderung.getFirst().durchschnittsRating()).isEqualByComparingTo("9.00");
    }

    @Test
    @DisplayName("Statuswechsel auf ABGEBROCHEN mit neuem Rating leert den Cache")
    void statuswechselLeertCache() {
        var e = eintrag(p2, spielB, Status.AM_SPIELEN, null);
        top();                                                     // B: 1 Bewertung (6.00)

        eintragService.statusWechseln(e.id(), new StatusWechselnRequest(Status.ABGEBROCHEN, (short) 2, null, e.version()));

        TopSpielResponse b = top().stream().filter(t -> t.spielId().equals(spielB)).findFirst().orElseThrow();
        assertThat(b.anzahlBewertungen()).isEqualTo(2);
        assertThat(b.durchschnittsRating()).isEqualByComparingTo("4.00");   // (6 + 2) / 2
    }

    @Test
    @DisplayName("Titeländerung eines Spiels leert den Cache (Titel ist Teil des Ergebnisses)")
    void titelaenderungLeertCache() {
        top();
        spielService.aendern(spielA, new SpielRequest("Spiel A – Remastered", genreId, null));

        assertThat(top().getFirst().titel()).isEqualTo("Spiel A – Remastered");
    }

    @Test
    @DisplayName("Änderung ohne Einfluss auf Bewertungen (GEPLANT) leert den Cache NICHT")
    void irrelevanteAenderungBehaeltCache() {
        top();
        CacheStats vorher = statistik();

        eintrag(p2, spielA, Status.GEPLANT, null);   // zählt nicht in die Top-Liste

        top();
        assertThat(statistik().minus(vorher).hitCount()).as("weiterhin Treffer").isEqualTo(1);
        assertThat(statistik().minus(vorher).missCount()).isZero();
    }

    @Test
    @DisplayName("Verschiedene Parameter sind verschiedene Cache-Schlüssel")
    void schluesselProParameter() {
        CacheStats vorher = statistik();

        auswertungService.topSpiele(1, genreId, 10);
        auswertungService.topSpiele(2, genreId, 10);   // andere Mindestanzahl
        auswertungService.topSpiele(1, null, 10);      // alle Genres
        auswertungService.topSpiele(1, genreId, 10);   // wie der erste -> Treffer

        CacheStats diff = statistik().minus(vorher);
        assertThat(diff.missCount()).isEqualTo(3);
        assertThat(diff.hitCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Bei einem Rollback wird der Cache nicht geleert (transaktionsbewusster Cache)")
    void rollbackLeertCacheNicht() {
        // Eintrag mit absichtlich inkonsistenter Statistik -> Statuswechsel scheitert im 3. Schreibzugriff (wie T4)
        Long spielC = spiel("Spiel C", genreId);
        Long eintragId = jdbc.sql("""
                INSERT INTO eintrag (spieler_id, spiel_id, status, rating, abgeschlossen_am)
                VALUES (?, ?, 'GESPIELT', 5, DATE '2026-01-31') RETURNING id
                """).params(p2, spielC).query(Long.class).single();
        cacheManager.getCache(CacheConfig.TOP_SPIELE).clear();
        top();
        CacheStats vorher = statistik();

        assertThatThrownBy(() -> eintragService.statusWechseln(eintragId,
                new StatusWechselnRequest(Status.AM_SPIELEN, null, null, 0L)))
                .isInstanceOf(DataIntegrityViolationException.class);

        top();
        assertThat(statistik().minus(vorher).hitCount()).as("Cache noch gefüllt").isEqualTo(1);
    }
}
