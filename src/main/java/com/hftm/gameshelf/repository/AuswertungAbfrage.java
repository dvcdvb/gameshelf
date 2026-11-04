package com.hftm.gameshelf.repository;

import com.hftm.gameshelf.domain.Status;
import com.hftm.gameshelf.dto.auswertung.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A7 / T6: Auswertungen mit parametrisiertem SQL über JDBC.
 *
 * Warum JDBC statt JPA? Die Ergebnisse sind keine Entities, sondern Kennzahlen.
 * PostgreSQL-Funktionen wie FILTER, ROLLUP oder date_trunc lassen sich in reinem SQL
 * direkt und lesbar verwenden; das SQL hier ist 1:1 das, was die Datenbank ausführt
 * (wichtig für EXPLAIN ANALYZE in T10).
 *
 * Grundlage ist die View v_beendeter_eintrag (V2).
 */
@Repository
public class AuswertungAbfrage {

    private final JdbcClient jdbc;

    public AuswertungAbfrage(DataSource dataSource) {
        this.jdbc = JdbcClient.create(dataSource);
    }

    /** Auswertung 1: bestbewertete Spiele, optional nur ein Genre. */
    public List<TopSpielResponse> topSpiele(int minBewertungen, Long genreId, int limit) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("minBewertungen", minBewertungen);
        p.put("limit", limit);
        String genreFilter = "";
        if (genreId != null) {
            genreFilter = "WHERE v.genre_id = :genreId";
            p.put("genreId", genreId);
        }
        String sql = """
                SELECT v.spiel_id,
                       v.spiel_titel,
                       g.name                   AS genre_name,
                       COUNT(*)                 AS anzahl_bewertungen,
                       ROUND(AVG(v.rating), 2)  AS durchschnitt
                  FROM v_beendeter_eintrag v
                  JOIN genre g ON g.id = v.genre_id
                  %s
                 GROUP BY v.spiel_id, v.spiel_titel, g.name
                HAVING COUNT(*) >= :minBewertungen
                 ORDER BY durchschnitt DESC, anzahl_bewertungen DESC, v.spiel_id
                 LIMIT :limit
                """.formatted(genreFilter);

        return jdbc.sql(sql).params(p)
                .query((rs, i) -> new TopSpielResponse(
                        rs.getLong("spiel_id"),
                        rs.getString("spiel_titel"),
                        rs.getString("genre_name"),
                        rs.getLong("anzahl_bewertungen"),
                        rs.getBigDecimal("durchschnitt")))
                .list();
    }

    /**
     * Auswertung 2: Abbruchquote je Genre.
     * COUNT(DISTINCT spiel_id) zählt Spiele, COUNT(*) zählt Einträge: Ein Spiel mit drei
     * Einträgen ist EIN Spiel, aber DREI beendete Einträge.
     */
    public List<GenreAbbruchResponse> abbruchquoteJeGenre(int minBeendet) {
        String sql = """
                SELECT g.id                                             AS genre_id,
                       g.name                                           AS genre_name,
                       COUNT(DISTINCT v.spiel_id)                       AS anzahl_spiele,
                       COUNT(*)                                         AS anzahl_beendet,
                       COUNT(*) FILTER (WHERE v.status = 'ABGEBROCHEN') AS anzahl_abgebrochen,
                       ROUND(100.0 * COUNT(*) FILTER (WHERE v.status = 'ABGEBROCHEN') / COUNT(*), 1)
                                                                        AS abbruchquote_prozent
                  FROM v_beendeter_eintrag v
                  JOIN genre g ON g.id = v.genre_id
                 GROUP BY g.id, g.name
                HAVING COUNT(*) >= :minBeendet
                 ORDER BY abbruchquote_prozent DESC, g.id
                """;
        return jdbc.sql(sql).param("minBeendet", minBeendet)
                .query((rs, i) -> new GenreAbbruchResponse(
                        rs.getLong("genre_id"),
                        rs.getString("genre_name"),
                        rs.getLong("anzahl_spiele"),
                        rs.getLong("anzahl_beendet"),
                        rs.getLong("anzahl_abgebrochen"),
                        rs.getBigDecimal("abbruchquote_prozent")))
                .list();
    }

    /**
     * Auswertung 3: Liste eines Spielers je Status plus Gesamtzeile.
     * GROUP BY ROLLUP liefert zusätzlich eine Zeile mit status = NULL für "alle zusammen".
     * Vorteil: Der Gesamtdurchschnitt wird korrekt über alle Ratings berechnet
     * (nicht als Durchschnitt der Durchschnitte).
     */
    public List<StatusStatistik> statusVerteilung(Long spielerId) {
        String sql = """
                SELECT e.status,
                       COUNT(*)                          AS anzahl,
                       ROUND(AVG(e.rating), 2)           AS durchschnitt_rating,
                       COALESCE(SUM(e.spielzeit_std), 0) AS spielzeit_std
                  FROM eintrag e
                 WHERE e.spieler_id = :spielerId
                 GROUP BY ROLLUP (e.status)
                 ORDER BY GROUPING(e.status), e.status
                """;
        return jdbc.sql(sql).param("spielerId", spielerId)
                .query((rs, i) -> {
                    String status = rs.getString("status");
                    return new StatusStatistik(
                            status == null ? null : Status.valueOf(status),
                            rs.getLong("anzahl"),
                            rs.getBigDecimal("durchschnitt_rating"),
                            rs.getBigDecimal("spielzeit_std"));
                })
                .list();
    }

    /** Auswertung 4: abgeschlossene (GESPIELT) Spiele pro Monat im Zeitraum, optional für einen Spieler. */
    public List<MonatsAbschlussResponse> abschluesseProMonat(LocalDate von, LocalDate bis, Long spielerId) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("von", von);
        p.put("bisExklusiv", bis.plusDays(1));   // "bis" ist inklusive gemeint
        String spielerFilter = "";
        if (spielerId != null) {
            spielerFilter = "AND v.spieler_id = :spielerId";
            p.put("spielerId", spielerId);
        }
        String sql = """
                SELECT CAST(date_trunc('month', v.abgeschlossen_am) AS date) AS monat,
                       COUNT(*)                                                AS anzahl,
                       ROUND(AVG(v.spielzeit_std), 1)                          AS durchschnitt_spielzeit
                  FROM v_beendeter_eintrag v
                 WHERE v.status = 'GESPIELT'
                   AND v.abgeschlossen_am >= :von
                   AND v.abgeschlossen_am <  :bisExklusiv
                   %s
                 GROUP BY 1
                 ORDER BY 1
                """.formatted(spielerFilter);
        return jdbc.sql(sql).params(p)
                .query((rs, i) -> new MonatsAbschlussResponse(
                        rs.getObject("monat", LocalDate.class),
                        rs.getLong("anzahl"),
                        rs.getBigDecimal("durchschnitt_spielzeit")))
                .list();
    }
}
