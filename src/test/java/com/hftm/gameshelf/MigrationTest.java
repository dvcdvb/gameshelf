package com.hftm.gameshelf;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.*;

/**
 * T2: Neuaufbau ab leerer Datenbank und Übergang vom früheren (V1) zum erweiterten Schema.
 * Jeder Test legt im Test-Container eine eigene, frische Datenbank an.
 */
class MigrationTest extends AbstractIntegrationTest {

    @Autowired private PostgreSQLContainer postgres;

    @Test
    @DisplayName("Neuaufbau: leere Datenbank wird vollständig migriert, keine Migration bleibt offen")
    void neuaufbauAbLeererDatenbank() {
        String url = neueDatenbank();
        Flyway flyway = flyway(url, null);

        flyway.migrate();

        assertThat(flyway.info().pending()).isEmpty();
        assertThat(flyway.info().applied().length).isGreaterThanOrEqualTo(2);
        JdbcClient db = verbindung(url);
        assertThat(anzahlObjekte(db, "tables", "eintrag")).isEqualTo(1);
        assertThat(anzahlObjekte(db, "views", "v_beendeter_eintrag")).isEqualTo(1);
    }

    @Test
    @DisplayName("Schemaentwicklung: bestehende V1-Datenbank mit Daten wird auf die neueste Version gehoben")
    void uebergangVonV1() {
        String url = neueDatenbank();

        // 1. Früheres Schema: nur V1
        flyway(url, "1").migrate();
        JdbcClient db = verbindung(url);
        assertThat(anzahlObjekte(db, "views", "v_beendeter_eintrag")).isZero();

        // 2. Daten im alten Schema anlegen
        db.sql("INSERT INTO genre (name) VALUES ('RPG')").update();
        db.sql("INSERT INTO spiel (genre_id, titel) VALUES (1, 'Alt-Spiel')").update();
        db.sql("INSERT INTO spieler (benutzername) VALUES ('alt')").update();
        db.sql("INSERT INTO eintrag (spieler_id, spiel_id, status, rating) VALUES (1, 1, 'GESPIELT', 7)").update();

        // 3. Auf die neueste Version migrieren
        Flyway neu = flyway(url, null);
        int ausgefuehrt = neu.migrate().migrationsExecuted;

        assertThat(ausgefuehrt).isGreaterThanOrEqualTo(1);
        assertThat(neu.info().pending()).isEmpty();
        // Bestehende Daten sind erhalten und über die neue View sichtbar
        assertThat(db.sql("SELECT rating FROM v_beendeter_eintrag WHERE spiel_titel = 'Alt-Spiel'")
                .query(Integer.class).single()).isEqualTo(7);
    }

    // ---------- Hilfsmethoden ----------

    private String neueDatenbank() {
        String name = "migration_" + System.nanoTime();
        jdbc.sql("CREATE DATABASE " + name).update();
        return "jdbc:postgresql://" + postgres.getHost() + ":" + postgres.getMappedPort(5432) + "/" + name;
    }

    private Flyway flyway(String url, String zielVersion) {
        var konfiguration = Flyway.configure()
                .dataSource(url, postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration");
        if (zielVersion != null) {
            konfiguration.target(zielVersion);
        }
        return konfiguration.load();
    }

    private JdbcClient verbindung(String url) {
        return JdbcClient.create(new DriverManagerDataSource(url, postgres.getUsername(), postgres.getPassword()));
    }

    private static long anzahlObjekte(JdbcClient db, String art, String name) {
        return db.sql("SELECT COUNT(*) FROM information_schema." + art + " WHERE table_schema = 'public' AND table_name = ?")
                .param(name).query(Long.class).single();
    }
}
