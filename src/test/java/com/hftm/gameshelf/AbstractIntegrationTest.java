package com.hftm.gameshelf;

import com.hftm.gameshelf.domain.Status;
import com.hftm.gameshelf.dto.*;
import com.hftm.gameshelf.service.EintragService;
import com.hftm.gameshelf.service.GenreService;
import com.hftm.gameshelf.service.SpielService;
import com.hftm.gameshelf.service.SpielerService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

/**
 * Basis aller Integrationstests:
 *  - echte Spring-Anwendung (@SpringBootTest) gegen PostgreSQL aus Testcontainers
 *  - vor jedem Test eine leere Datenbank (kleine, kontrollierte Testdaten, T8)
 *  - Hilfsmethoden zum Anlegen von Testdaten über die echten Services
 *
 * Die Tests sind bewusst NICHT @Transactional: Sonst liefe jeder Test in einer einzigen
 * Transaktion, die am Ende zurückgerollt wird – Rollback und gleichzeitige Zugriffe
 * könnten dann gar nicht realistisch geprüft werden.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
public abstract class AbstractIntegrationTest {

    @Autowired protected GenreService genreService;
    @Autowired protected SpielService spielService;
    @Autowired protected SpielerService spielerService;
    @Autowired protected EintragService eintragService;

    @Autowired protected CacheManager cacheManager;

    protected JdbcClient jdbc;

    @Autowired
    void setDataSource(DataSource dataSource) {
        this.jdbc = JdbcClient.create(dataSource);
    }

    @BeforeEach
    void datenbankLeeren() {
        jdbc.sql("TRUNCATE statuswechsel, eintrag, spiel, spieler, genre RESTART IDENTITY CASCADE").update();
        // TRUNCATE geht an der Anwendung vorbei -> Caches ebenfalls leeren, sonst lieferten sie Daten
        // aus einem früheren Test (die IDs beginnen nach RESTART IDENTITY wieder bei 1).
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
    }

    // ---------- Testdaten ----------

    protected Long genre(String name) {
        return genreService.erstellen(new GenreRequest(name)).id();
    }

    protected Long spiel(String titel, Long genreId) {
        return spielService.erstellen(new SpielRequest(titel, genreId, null)).id();
    }

    protected Long spieler(String name) {
        return spielerService.erstellen(new SpielerRequest(name)).id();
    }

    protected EintragResponse eintrag(Long spielerId, Long spielId, Status status, Integer rating) {
        return eintrag(spielerId, spielId, status, rating, null, null);
    }

    protected EintragResponse eintrag(Long spielerId, Long spielId, Status status, Integer rating,
                                      Integer spielzeitStd, LocalDate abgeschlossenAm) {
        return eintragService.erstellen(spielerId, new EintragErstellenRequest(
                spielId, status,
                rating == null ? null : rating.shortValue(),
                null,
                spielzeitStd == null ? null : BigDecimal.valueOf(spielzeitStd),
                null,
                abgeschlossenAm));
    }

    // ---------- Abfragen direkt in der DB (unabhängig vom Anwendungscode) ----------

    /** Nur mit festen Tabellennamen aus dem Testcode aufrufen. */
    protected long anzahl(String tabelle) {
        return jdbc.sql("SELECT COUNT(*) FROM " + tabelle).query(Long.class).single();
    }

    /** [anzahl_bewertungen, rating_summe] eines Spiels. */
    protected int[] statistik(Long spielId) {
        return jdbc.sql("SELECT anzahl_bewertungen, rating_summe FROM spiel WHERE id = ?")
                .param(spielId)
                .query((rs, i) -> new int[]{rs.getInt(1), rs.getInt(2)})
                .single();
    }

    // ---------- Gleichzeitigkeit ----------

    /**
     * Führt alle Aufgaben in eigenen Threads möglichst GLEICHZEITIG aus (gemeinsames Startsignal).
     * Liefert pro Aufgabe entweder das Ergebnis oder die geworfene Exception.
     */
    protected static List<Object> gleichzeitigAusfuehren(List<Callable<Object>> aufgaben) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(aufgaben.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (Callable<Object> aufgabe : aufgaben) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return aufgabe.call();
                }));
            }
            start.countDown();
            List<Object> ergebnisse = new ArrayList<>();
            for (Future<Object> f : futures) {
                try {
                    ergebnisse.add(f.get(30, TimeUnit.SECONDS));
                } catch (ExecutionException ex) {
                    ergebnisse.add(ex.getCause());
                }
            }
            return ergebnisse;
        } finally {
            pool.shutdownNow();
        }
    }
}
