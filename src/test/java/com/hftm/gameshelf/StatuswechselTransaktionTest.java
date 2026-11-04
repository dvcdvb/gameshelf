package com.hftm.gameshelf;

import com.hftm.gameshelf.domain.Status;
import com.hftm.gameshelf.dto.EintragResponse;
import com.hftm.gameshelf.dto.StatusWechselnRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;

/**
 * A4 / T4: Die kritische Geschäftsoperation "Status wechseln" ist atomar.
 * Erfolgsfall und Fehlerfall NACH bereits erfolgten Schreibzugriffen.
 */
class StatuswechselTransaktionTest extends AbstractIntegrationTest {

    private Long spielId;
    private Long spielerId;

    @BeforeEach
    void testdaten() {
        spielId = spiel("Celeste", genre("Plattformer"));
        spielerId = spieler("fabio");
    }

    @Test
    @DisplayName("Erfolgsfall: Eintrag, Verlauf und Statistik werden gemeinsam geändert")
    void erfolgsfall() {
        EintragResponse e = eintrag(spielerId, spielId, Status.AM_SPIELEN, null);

        EintragResponse nachher = eintragService.statusWechseln(e.id(),
                new StatusWechselnRequest(Status.GESPIELT, (short) 9, null, 0L));

        assertThat(nachher.status()).isEqualTo(Status.GESPIELT);
        assertThat(nachher.abgeschlossenAm()).isEqualTo(LocalDate.now());
        assertThat(nachher.version()).isEqualTo(1L);
        assertThat(eintragService.verlauf(e.id())).hasSize(2);   // null->AM_SPIELEN, AM_SPIELEN->GESPIELT
        assertThat(statistik(spielId)).containsExactly(1, 9);
    }

    @Test
    @DisplayName("Fehlerfall: Fehler im 3. Schreibzugriff nimmt auch Schritt 1 und 2 vollständig zurück")
    void rollbackNachSchreibzugriff() {
        // Ausgangslage mit absichtlich inkonsistenter Statistik: Eintrag ist GESPIELT (Rating 8),
        // die Statistik des Spiels steht aber auf 0/0 (direkt per SQL, am Service vorbei).
        Long eintragId = jdbc.sql("""
                INSERT INTO eintrag (spieler_id, spiel_id, status, rating, abgeschlossen_am)
                VALUES (?, ?, 'GESPIELT', 8, DATE '2026-01-31') RETURNING id
                """).params(spielerId, spielId).query(Long.class).single();
        long sequenzVorher = letzteStatuswechselId();

        // GESPIELT -> AM_SPIELEN:
        //   1. UPDATE eintrag          -> wird ausgeführt (flush)
        //   2. INSERT statuswechsel    -> wird ausgeführt (flush)
        //   3. UPDATE spiel: anzahl 0 - 1 = -1 -> verletzt ck_spiel_anzahl -> Exception
        assertThatThrownBy(() -> eintragService.statusWechseln(eintragId,
                new StatusWechselnRequest(Status.AM_SPIELEN, null, null, 0L)))
                .isInstanceOf(DataIntegrityViolationException.class);

        // Schritt 1 ist zurückgerollt: Eintrag unverändert
        var eintrag = jdbc.sql("SELECT status, abgeschlossen_am, version FROM eintrag WHERE id = ?")
                .param(eintragId)
                .query((rs, i) -> new Object[]{rs.getString(1), rs.getObject(2, LocalDate.class), rs.getLong(3)})
                .single();
        assertThat(eintrag[0]).isEqualTo("GESPIELT");
        assertThat(eintrag[1]).isEqualTo(LocalDate.of(2026, 1, 31));
        assertThat(eintrag[2]).isEqualTo(0L);

        // Schritt 2 ist zurückgerollt: kein Verlaufseintrag
        assertThat(anzahl("statuswechsel")).isZero();

        // Schritt 3 hat nichts verändert
        assertThat(statistik(spielId)).containsExactly(0, 0);

        // Beweis, dass Schritt 2 WIRKLICH in der DB ausgeführt wurde (nicht nur geprüft):
        // Die Identity-Sequenz von statuswechsel wurde weitergezählt. Sequenzen werden bei einem
        // Rollback in PostgreSQL bewusst nicht zurückgesetzt – die Zeile selbst ist aber weg.
        assertThat(letzteStatuswechselId()).isGreaterThan(sequenzVorher);
    }

    private long letzteStatuswechselId() {
        return jdbc.sql("""
                SELECT COALESCE(pg_sequence_last_value(pg_get_serial_sequence('statuswechsel', 'id')::regclass), 0)
                """).query(Long.class).single();
    }
}
