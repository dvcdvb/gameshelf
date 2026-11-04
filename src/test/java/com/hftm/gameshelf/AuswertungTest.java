package com.hftm.gameshelf;

import com.hftm.gameshelf.domain.Status;
import com.hftm.gameshelf.dto.EintragAendernRequest;
import com.hftm.gameshelf.dto.StatusWechselnRequest;
import com.hftm.gameshelf.dto.auswertung.*;
import com.hftm.gameshelf.service.AuswertungService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * A7 / T6: Auswertungen (JDBC + View) mit fachlich bekannten Ergebnissen.
 *
 *   Genre G1: Spiel A, Spiel B      Genre G2: Spiel C, Spiel D
 *   P1: A GESPIELT 8 (40h, 15.01.)  B ABGEBROCHEN 2 (5h, 20.01.)  C GEPLANT  D GESPIELT 6 (10h, 05.03.)
 *   P2: A GESPIELT 10 (30h, 01.02.) C AM_SPIELEN
 */
class AuswertungTest extends AbstractIntegrationTest {

    @Autowired private AuswertungService auswertungService;

    private Long g1, g2, a, b, c, d, p1, p2;

    @BeforeEach
    void testdaten() {
        g1 = genre("G1");
        g2 = genre("G2");
        a = spiel("A", g1);
        b = spiel("B", g1);
        c = spiel("C", g2);
        d = spiel("D", g2);
        p1 = spieler("p1");
        p2 = spieler("p2");

        eintrag(p1, a, Status.GESPIELT, 8, 40, LocalDate.of(2026, 1, 15));
        eintrag(p1, b, Status.ABGEBROCHEN, 2, 5, LocalDate.of(2026, 1, 20));
        eintrag(p1, c, Status.GEPLANT, null);
        eintrag(p1, d, Status.GESPIELT, 6, 10, LocalDate.of(2026, 3, 5));
        eintrag(p2, a, Status.GESPIELT, 10, 30, LocalDate.of(2026, 2, 1));
        eintrag(p2, c, Status.AM_SPIELEN, null);
    }

    @Test
    @DisplayName("Top-Spiele: A mit 2 Bewertungen (Ø 9.00) vor B (Ø 2.00); Mindestanzahl wirkt")
    void topSpiele() {
        List<TopSpielResponse> top = auswertungService.topSpiele(1, g1, 10);

        assertThat(top).hasSize(2);
        assertThat(top.get(0).spielId()).isEqualTo(a);
        assertThat(top.get(0).anzahlBewertungen()).isEqualTo(2);
        assertThat(top.get(0).durchschnittsRating()).isEqualByComparingTo("9.00");
        assertThat(top.get(1).spielId()).isEqualTo(b);
        assertThat(top.get(1).durchschnittsRating()).isEqualByComparingTo("2.00");

        assertThat(auswertungService.topSpiele(2, g1, 10)).extracting(TopSpielResponse::spielId).containsExactly(a);
        // C (nur GEPLANT/AM_SPIELEN) kommt in keiner Top-Liste vor
        assertThat(auswertungService.topSpiele(1, null, 10)).extracting(TopSpielResponse::spielId).doesNotContain(c);
    }

    @Test
    @DisplayName("Abbruchquote: Spiel A mit 2 Einträgen zählt als EIN Spiel, aber ZWEI beendete Einträge")
    void abbruchquote() {
        List<GenreAbbruchResponse> quoten = auswertungService.abbruchquoteJeGenre(1);

        GenreAbbruchResponse quoteG1 = quoten.stream().filter(q -> q.genreId().equals(g1)).findFirst().orElseThrow();
        assertThat(quoteG1.anzahlSpiele()).isEqualTo(2);          // A, B  (nicht 3!)
        assertThat(quoteG1.anzahlBeendet()).isEqualTo(3);         // A(P1), A(P2), B(P1)
        assertThat(quoteG1.anzahlAbgebrochen()).isEqualTo(1);
        assertThat(quoteG1.abbruchquoteProzent()).isEqualByComparingTo("33.3");

        GenreAbbruchResponse quoteG2 = quoten.stream().filter(q -> q.genreId().equals(g2)).findFirst().orElseThrow();
        assertThat(quoteG2.anzahlSpiele()).isEqualTo(1);          // nur D ist beendet
        assertThat(quoteG2.abbruchquoteProzent()).isEqualByComparingTo("0.0");

        assertThat(quoten).extracting(GenreAbbruchResponse::genreId).containsExactly(g1, g2);   // sortiert nach Quote
    }

    @Test
    @DisplayName("Spielerstatistik: Gesamtdurchschnitt über alle Ratings (5.33), nicht Durchschnitt der Durchschnitte")
    void spielerStatistik() {
        SpielerStatistikResponse s = auswertungService.spielerStatistik(p1);

        assertThat(s.gesamt().anzahl()).isEqualTo(4);
        assertThat(s.gesamt().durchschnittsRating()).isEqualByComparingTo("5.33");   // (8+2+6)/3
        assertThat(s.gesamt().spielzeitStd()).isEqualByComparingTo("55");

        StatusStatistik gespielt = s.proStatus().stream()
                .filter(z -> z.status() == Status.GESPIELT).findFirst().orElseThrow();
        assertThat(gespielt.anzahl()).isEqualTo(2);
        assertThat(gespielt.durchschnittsRating()).isEqualByComparingTo("7.00");
        assertThat(gespielt.spielzeitStd()).isEqualByComparingTo("50");
        assertThat(s.proStatus()).extracting(StatusStatistik::status)
                .containsExactlyInAnyOrder(Status.GESPIELT, Status.ABGEBROCHEN, Status.GEPLANT);
    }

    @Test
    @DisplayName("Abschlüsse pro Monat: nur GESPIELT, 'bis' inklusive")
    void abschluesseProMonat() {
        List<MonatsAbschlussResponse> monate = auswertungService.abschluesseProMonat(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), p1);

        assertThat(monate).extracting(MonatsAbschlussResponse::monat)
                .containsExactly(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 1));
        assertThat(monate.get(0).anzahl()).isEqualTo(1);   // B ist abgebrochen und zählt nicht
        assertThat(monate.get(0).durchschnittSpielzeitStd()).isEqualByComparingTo("40.0");

        // 'bis' = 05.03. schliesst den Abschluss am 05.03. ein
        assertThat(auswertungService.abschluesseProMonat(
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 5), p1)).hasSize(1);
        assertThat(auswertungService.abschluesseProMonat(
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 4), p1)).isEmpty();
    }

    @Test
    @DisplayName("Redundante Statistik in 'spiel' stimmt nach vielen Operationen mit einer Neuberechnung überein")
    void redundanteStatistikKonsistent() {
        // weitere Operationen auf Spiel A: Rating ändern, Status zurücksetzen, löschen, neu aufnehmen
        Long p3 = spieler("p3");
        var e = eintrag(p3, a, Status.AM_SPIELEN, null);
        e = eintragService.statusWechseln(e.id(), new StatusWechselnRequest(Status.GESPIELT, (short) 7, null, e.version()));
        e = eintragService.aendern(e.id(), new EintragAendernRequest((short) 3, null, null, null, e.abgeschlossenAm(), e.version()));
        e = eintragService.statusWechseln(e.id(), new StatusWechselnRequest(Status.AM_SPIELEN, null, null, e.version()));
        e = eintragService.statusWechseln(e.id(), new StatusWechselnRequest(Status.ABGEBROCHEN, (short) 1, null, e.version()));
        eintragService.loeschen(e.id());

        for (Long spiel : List.of(a, b, c, d)) {
            int[] gespeichert = statistik(spiel);
            int[] neuBerechnet = jdbc.sql("""
                    SELECT COUNT(*), COALESCE(SUM(rating), 0) FROM v_beendeter_eintrag WHERE spiel_id = ?
                    """).param(spiel).query((rs, i) -> new int[]{rs.getInt(1), rs.getInt(2)}).single();
            assertThat(gespeichert).as("Spiel %d", spiel).containsExactly(neuBerechnet);
        }
    }
}
