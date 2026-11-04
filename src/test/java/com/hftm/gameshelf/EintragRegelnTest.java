package com.hftm.gameshelf;

import com.hftm.gameshelf.domain.Status;
import com.hftm.gameshelf.dto.EintragAendernRequest;
import com.hftm.gameshelf.dto.EintragErstellenRequest;
import com.hftm.gameshelf.dto.EintragResponse;
import com.hftm.gameshelf.exception.GeschaeftsregelException;
import com.hftm.gameshelf.exception.KonfliktException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;

/** A1–A3: Geschäftsregeln mit gültigen und ungültigen Eingaben (T8). */
class EintragRegelnTest extends AbstractIntegrationTest {

    private Long genreId;
    private Long spielId;
    private Long spielerId;

    @BeforeEach
    void testdaten() {
        genreId = genre("RPG");
        spielId = spiel("Hades", genreId);
        spielerId = spieler("fabio");
    }

    @Test
    @DisplayName("Gültiger Eintrag wird mit erstem Verlaufseintrag und Statistik gespeichert")
    void gueltigerEintrag() {
        EintragResponse e = eintragService.erstellen(spielerId, new EintragErstellenRequest(
                spielId, Status.GESPIELT, (short) 8, "Top", new BigDecimal("12.5"),
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 2, 1)));

        assertThat(e.id()).isNotNull();
        assertThat(e.version()).isZero();
        assertThat(anzahl("eintrag")).isEqualTo(1);
        assertThat(anzahl("statuswechsel")).isEqualTo(1);
        assertThat(statistik(spielId)).containsExactly(1, 8);
    }

    @Test
    @DisplayName("Geplanter Eintrag ohne Rating ist erlaubt und zählt nicht in die Statistik")
    void geplantOhneRating() {
        eintrag(spielerId, spielId, Status.GEPLANT, null);

        assertThat(anzahl("eintrag")).isEqualTo(1);
        assertThat(statistik(spielId)).containsExactly(0, 0);
    }

    @Test
    @DisplayName("Dasselbe Spiel kann pro Spieler nur einmal aufgenommen werden")
    void doppelterEintrag() {
        eintrag(spielerId, spielId, Status.GEPLANT, null);

        assertThatThrownBy(() -> eintrag(spielerId, spielId, Status.AM_SPIELEN, null))
                .isInstanceOf(KonfliktException.class);
        assertThat(anzahl("eintrag")).isEqualTo(1);
    }

    @Test
    @DisplayName("GESPIELT ohne Rating wird abgelehnt")
    void gespieltOhneRating() {
        assertThatThrownBy(() -> eintrag(spielerId, spielId, Status.GESPIELT, null))
                .isInstanceOf(GeschaeftsregelException.class)
                .hasMessageContaining("Rating");
        assertThat(anzahl("eintrag")).isZero();
    }

    @Test
    @DisplayName("Abschlussdatum vor Startdatum wird abgelehnt")
    void abschlussVorStart() {
        assertThatThrownBy(() -> eintragService.erstellen(spielerId, new EintragErstellenRequest(
                spielId, Status.GESPIELT, (short) 7, null, null,
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 4, 1))))
                .isInstanceOf(GeschaeftsregelException.class);
        assertThat(anzahl("eintrag")).isZero();
    }

    @Test
    @DisplayName("Abschlussdatum bei GEPLANT wird abgelehnt")
    void abschlussBeiGeplant() {
        assertThatThrownBy(() -> eintrag(spielerId, spielId, Status.GEPLANT, null, null, LocalDate.of(2026, 1, 1)))
                .isInstanceOf(GeschaeftsregelException.class);
    }

    @Test
    @DisplayName("Deaktivierte Spiele können nicht aufgenommen werden")
    void deaktiviertesSpiel() {
        spielService.deaktivieren(spielId);

        assertThatThrownBy(() -> eintrag(spielerId, spielId, Status.GEPLANT, null))
                .isInstanceOf(GeschaeftsregelException.class);
    }

    @Test
    @DisplayName("Doppelter Spieltitel (andere Schreibweise) wird abgelehnt")
    void doppelterTitel() {
        assertThatThrownBy(() -> spiel("HADES", genreId)).isInstanceOf(KonfliktException.class);
    }

    @Test
    @DisplayName("Die Datenbank erzwingt die Regeln auch ohne Service (T1)")
    void datenbankConstraints() {
        // Direkt per SQL, an jeder Anwendungslogik vorbei
        assertThatThrownBy(() -> jdbc.sql(
                "INSERT INTO eintrag (spieler_id, spiel_id, status) VALUES (?, ?, 'GESPIELT')")
                .params(spielerId, spielId).update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_eintrag_rating_pflicht");

        assertThatThrownBy(() -> jdbc.sql(
                "INSERT INTO eintrag (spieler_id, spiel_id, status, rating) VALUES (?, ?, 'GESPIELT', 11)")
                .params(spielerId, spielId).update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_eintrag_rating");

        assertThatThrownBy(() -> jdbc.sql(
                "INSERT INTO eintrag (spieler_id, spiel_id, status) VALUES (?, ?, 'FERTIG')")
                .params(spielerId, spielId).update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_eintrag_status");

        assertThatThrownBy(() -> jdbc.sql(
                "INSERT INTO eintrag (spieler_id, spiel_id, status, gestartet_am, abgeschlossen_am, rating) "
                        + "VALUES (?, ?, 'GESPIELT', DATE '2026-05-01', DATE '2026-04-01', 5)")
                .params(spielerId, spielId).update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_eintrag_datum");

        assertThat(anzahl("eintrag")).isZero();
    }

    @Test
    @DisplayName("Rating ändern passt die Statistik an und erhöht die Version")
    void ratingAendern() {
        EintragResponse e = eintrag(spielerId, spielId, Status.GESPIELT, 8);

        EintragResponse geaendert = eintragService.aendern(e.id(),
                new EintragAendernRequest((short) 4, "doch nicht so gut", null, null, null, 0L));

        assertThat(geaendert.rating()).isEqualTo((short) 4);
        assertThat(geaendert.version()).isEqualTo(1L);
        assertThat(statistik(spielId)).containsExactly(1, 4);
    }

    @Test
    @DisplayName("Änderung mit veralteter Version wird abgelehnt und ändert nichts")
    void veralteteVersion() {
        EintragResponse e = eintrag(spielerId, spielId, Status.GESPIELT, 8);
        eintragService.aendern(e.id(), new EintragAendernRequest((short) 6, null, null, null, null, 0L));

        assertThatThrownBy(() -> eintragService.aendern(e.id(),
                new EintragAendernRequest((short) 2, null, null, null, null, 0L)))
                .isInstanceOf(KonfliktException.class);
        assertThat(eintragService.laden(e.id()).rating()).isEqualTo((short) 6);
    }

    @Test
    @DisplayName("Löschen entfernt Eintrag, Verlauf (CASCADE) und Beitrag zur Statistik")
    void loeschen() {
        EintragResponse e = eintrag(spielerId, spielId, Status.GESPIELT, 9);

        eintragService.loeschen(e.id());

        assertThat(anzahl("eintrag")).isZero();
        assertThat(anzahl("statuswechsel")).isZero();
        assertThat(statistik(spielId)).containsExactly(0, 0);
    }
}
