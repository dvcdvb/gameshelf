package com.hftm.gameshelf;

import com.hftm.gameshelf.domain.Status;
import com.hftm.gameshelf.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/** A5 / T6 / T7: Liste eines Spielers – Filter, stabile Sortierung und Pagination in der DB. */
class EintragListeTest extends AbstractIntegrationTest {

    private Long spielerId;
    private Long eintragAlpha, eintragDelta;

    @BeforeEach
    void testdaten() {
        Long genreId = genre("Liste");
        spielerId = spieler("fabio");
        Long anderer = spieler("andere");

        eintragAlpha = eintrag(spielerId, spiel("Alpha", genreId), Status.GESPIELT, 9).id();
        eintrag(spielerId, spiel("Beta", genreId), Status.ABGEBROCHEN, 3);
        Long gamma = spiel("Gamma", genreId);
        eintrag(spielerId, gamma, Status.GEPLANT, null);
        eintragDelta = eintrag(spielerId, spiel("Delta", genreId), Status.GESPIELT, 9).id();
        eintrag(anderer, gamma, Status.GESPIELT, 10);   // gehört einem anderen Spieler -> darf nie erscheinen
    }

    private SeiteResponse<EintragListenElement> liste(EintragFilter filter, EintragSortierung sortierung,
                                                      Sort.Direction richtung, int seite, int groesse) {
        return eintragService.liste(spielerId, filter, sortierung, richtung, seite, groesse);
    }

    private static EintragFilter ohneFilter() {
        return new EintragFilter(null, null, null, null);
    }

    @Test
    @DisplayName("Pagination: zwei Seiten ohne Überschneidung, korrekte Gesamtzahl")
    void pagination() {
        var seite0 = liste(ohneFilter(), EintragSortierung.TITEL, Sort.Direction.ASC, 0, 2);
        var seite1 = liste(ohneFilter(), EintragSortierung.TITEL, Sort.Direction.ASC, 1, 2);

        assertThat(seite0.totalElemente()).isEqualTo(4);
        assertThat(seite0.totalSeiten()).isEqualTo(2);
        assertThat(seite0.inhalt()).extracting(EintragListenElement::spielTitel).containsExactly("Alpha", "Beta");
        assertThat(seite1.inhalt()).extracting(EintragListenElement::spielTitel).containsExactly("Delta", "Gamma");
    }

    @Test
    @DisplayName("Filter nach Status, mehreren Status, Rating-Bereich und Titel")
    void filter() {
        assertThat(liste(new EintragFilter(List.of(Status.GESPIELT), null, null, null),
                EintragSortierung.TITEL, Sort.Direction.ASC, 0, 20).inhalt())
                .extracting(EintragListenElement::spielTitel).containsExactly("Alpha", "Delta");

        assertThat(liste(new EintragFilter(List.of(Status.GESPIELT, Status.ABGEBROCHEN), null, null, null),
                EintragSortierung.TITEL, Sort.Direction.ASC, 0, 20).totalElemente()).isEqualTo(3);

        assertThat(liste(new EintragFilter(null, (short) 2, (short) 5, null),
                EintragSortierung.TITEL, Sort.Direction.ASC, 0, 20).inhalt())
                .extracting(EintragListenElement::spielTitel).containsExactly("Beta");

        assertThat(liste(new EintragFilter(null, null, null, "AMM"),
                EintragSortierung.TITEL, Sort.Direction.ASC, 0, 20).inhalt())
                .extracting(EintragListenElement::spielTitel).containsExactly("Gamma");
    }

    @Test
    @DisplayName("Platzhalter % in der Titelsuche wird wörtlich gesucht")
    void likeEscaping() {
        assertThat(liste(new EintragFilter(null, null, null, "%"),
                EintragSortierung.TITEL, Sort.Direction.ASC, 0, 20).totalElemente()).isZero();
    }

    @Test
    @DisplayName("Stabile Sortierung: gleiches Rating nach id, ohne Rating zuletzt")
    void stabileSortierung() {
        var r = liste(ohneFilter(), EintragSortierung.RATING, Sort.Direction.DESC, 0, 20).inhalt();

        assertThat(r).extracting(EintragListenElement::rating)
                .containsExactly((short) 9, (short) 9, (short) 3, null);
        assertThat(r.get(0).eintragId()).isEqualTo(eintragDelta);   // höhere id zuerst (DESC)
        assertThat(r.get(1).eintragId()).isEqualTo(eintragAlpha);

        // Wiederholte Abfrage liefert exakt dieselbe Reihenfolge
        var nochmals = liste(ohneFilter(), EintragSortierung.RATING, Sort.Direction.DESC, 0, 20).inhalt();
        assertThat(nochmals).extracting(EintragListenElement::eintragId)
                .containsExactlyElementsOf(r.stream().map(EintragListenElement::eintragId).toList());
    }

    @Test
    @DisplayName("Projektion enthält Felder aus Eintrag, Spiel und Genre")
    void projektion() {
        var element = liste(new EintragFilter(null, null, null, "alpha"),
                EintragSortierung.TITEL, Sort.Direction.ASC, 0, 20).inhalt().getFirst();

        assertThat(element.spielTitel()).isEqualTo("Alpha");
        assertThat(element.genre()).isEqualTo("Liste");
        assertThat(element.status()).isEqualTo(Status.GESPIELT);
        assertThat(element.rating()).isEqualTo((short) 9);
    }
}
