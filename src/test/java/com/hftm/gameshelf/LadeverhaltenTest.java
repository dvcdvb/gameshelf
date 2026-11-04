package com.hftm.gameshelf;

import com.hftm.gameshelf.domain.Eintrag;
import com.hftm.gameshelf.domain.Status;
import com.hftm.gameshelf.dto.EintragFilter;
import com.hftm.gameshelf.dto.EintragSortierung;
import com.hftm.gameshelf.dto.SpielResponse;
import com.hftm.gameshelf.repository.SpielRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * T11: ORM-Ladeverhalten. Zählt die SQL-Statements (Hibernate Statistics) für Lesezugriffe mit
 * Beziehungen bei unterschiedlich grossen Ergebnislisten (5, 20, 50 Elemente).
 *
 * Die Ergebnisse werden als Tabelle ausgegeben:  ./mvnw test -Dtest=LadeverhaltenTest
 * SQL zusätzlich sichtbar mit:                    -Dlogging.level.org.hibernate.SQL=debug
 */
class LadeverhaltenTest extends AbstractIntegrationTest {

    private static final int[] GROESSEN = {5, 20, 50};

    @Autowired private EntityManagerFactory entityManagerFactory;
    @Autowired private EntityManager entityManager;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private SpielRepository spielRepository;

    private Statistics statistik;

    @BeforeEach
    void statistikEinschalten() {
        statistik = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistik.setStatisticsEnabled(true);
    }

    /** Ergebnis einer Messung: Anzahl SQL-Statements und Anzahl geladener Entities. */
    private record Messung(long statements, long entities) {
        @Override
        public String toString() {
            return statements + " SQL / " + entities + " Entities";
        }
    }

    /** Führt den Zugriff in einer eigenen Lese-Transaktion aus (wie ein Service) und misst ihn. */
    private Messung messen(Runnable zugriff) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setReadOnly(true);
        statistik.clear();
        tx.executeWithoutResult(status -> zugriff.run());
        return new Messung(statistik.getPrepareStatementCount(), statistik.getEntityLoadCount());
    }

    /** Ein Spieler mit n Einträgen; jedes Spiel hat ein eigenes Genre (schlimmster Fall für N+1). */
    private Long testdaten(int n) {
        datenbankLeeren();
        Long spielerId = spieler("fabio");
        for (int i = 1; i <= n; i++) {
            Long genreId = genre("Genre " + i);
            Long spielId = spiel("Spiel " + i, genreId);
            eintrag(spielerId, spielId, Status.GESPIELT, (i % 10) + 1);
        }
        return spielerId;
    }

    // ---------------------------------------------------------------------------------------------
    // 1. Spielsuche (GET /api/spiele): Spiel -> Genre (ManyToOne, LAZY)
    // ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName("Spielsuche: ohne @EntityGraph N+1 (1 + N Abfragen), mit @EntityGraph konstant 1 Abfrage")
    void spielsuche() {
        List<String> tabelle = new ArrayList<>();
        tabelle.add("| Spiele | ohne @EntityGraph (vorher) | mit @EntityGraph (umgesetzt) |");
        tabelle.add("|---|---|---|");

        for (int n : GROESSEN) {
            testdaten(n);
            PageRequest seite = PageRequest.of(0, 100, Sort.by("titel", "id"));

            // VORHER: normales findAll -> Genre ist ein LAZY-Proxy, der beim Zugriff einzeln geladen wird
            Messung ohne = messen(() -> spielRepository.findAll(seite).getContent()
                    .stream().map(SpielResponse::von).toList());

            // NACHHER: so wie SpielService.suchen -> @EntityGraph("genre") lädt per JOIN mit
            Messung mit = messen(() -> spielRepository.findByTitelContainingIgnoreCase("", seite).getContent()
                    .stream().map(SpielResponse::von).toList());

            tabelle.add("| " + n + " | " + ohne + " | " + mit + " |");

            assertThat(ohne.statements() - mit.statements()).as("N+1 bei %d Spielen", n).isEqualTo(n);
            assertThat(mit.statements()).as("mit EntityGraph konstant").isEqualTo(1);
        }
        ausgeben("Spielsuche (Spiel -> Genre)", tabelle);
    }

    // ---------------------------------------------------------------------------------------------
    // 2. Liste eines Spielers (A5): Eintrag -> Spiel -> Genre
    // ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName("Spielerliste: Entities naiv 1 + 2N, JOIN FETCH 1, DTO-Projektion konstant ohne Entities")
    void spielerliste() {
        List<String> tabelle = new ArrayList<>();
        tabelle.add("| Einträge | Entities naiv | Entities mit JOIN FETCH | DTO-Projektion (umgesetzt) |");
        tabelle.add("|---|---|---|---|");

        for (int n : GROESSEN) {
            Long spielerId = testdaten(n);

            // Variante A: Entities laden, Beziehungen beim Zugriff nachladen
            Messung naiv = messen(() -> entityManager
                    .createQuery("select e from Eintrag e where e.spieler.id = :id order by e.id", Eintrag.class)
                    .setParameter("id", spielerId)
                    .setMaxResults(100)
                    .getResultList()
                    .forEach(e -> e.getSpiel().getGenre().getName()));   // -> je 1 SELECT für Spiel und Genre

            // Variante B: Entities mit JOIN FETCH (alles in einer Abfrage, aber ganze Entities)
            Messung joinFetch = messen(() -> entityManager
                    .createQuery("""
                            select e from Eintrag e
                              join fetch e.spiel s
                              join fetch s.genre
                             where e.spieler.id = :id
                             order by e.id""", Eintrag.class)
                    .setParameter("id", spielerId)
                    .setMaxResults(100)
                    .getResultList()
                    .forEach(e -> e.getSpiel().getGenre().getName()));

            // Variante C: so wie umgesetzt (EintragListeAbfrage) -> DTO-Projektion
            Messung projektion = messen(() -> eintragService.liste(spielerId,
                    new EintragFilter(null, null, null, null),
                    EintragSortierung.AKTUALISIERT, Sort.Direction.DESC, 0, 100));

            tabelle.add("| " + n + " | " + naiv + " | " + joinFetch + " | " + projektion + " |");

            assertThat(naiv.statements() - joinFetch.statements()).as("N+1 (Spiel + Genre) bei %d", n).isEqualTo(2L * n);
            assertThat(joinFetch.statements()).isEqualTo(1);
            assertThat(projektion.entities()).as("Projektion lädt keine Entities").isZero();
            assertThat(projektion.statements()).as("Projektion konstant").isLessThanOrEqualTo(3);
        }
        ausgeben("Spielerliste (Eintrag -> Spiel -> Genre)", tabelle);
    }

    private static void ausgeben(String titel, List<String> tabelle) {
        System.out.println();
        System.out.println("===== T11: " + titel + " =====");
        tabelle.forEach(System.out::println);
        System.out.println();
    }
}
