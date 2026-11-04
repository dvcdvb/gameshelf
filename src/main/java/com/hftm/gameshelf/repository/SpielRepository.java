package com.hftm.gameshelf.repository;

import com.hftm.gameshelf.domain.Spiel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SpielRepository extends JpaRepository<Spiel, Long> {

    boolean existsByTitelIgnoreCase(String titel);

    /** Einzelnes Spiel inkl. Genre in EINER Abfrage (JOIN) laden. */
    @EntityGraph(attributePaths = "genre")
    Optional<Spiel> findWithGenreById(Long id);

    /** Titelsuche (A1), seitenweise; Genre wird per JOIN mitgeladen -> kein N+1. */
    @EntityGraph(attributePaths = "genre")
    Page<Spiel> findByTitelContainingIgnoreCase(String titel, Pageable pageable);

    /**
     * Passt die redundante Statistik ATOMAR in der Datenbank an.
     * "x = x + delta" statt "lesen, in Java rechnen, zurückschreiben": So gehen keine
     * Bewertungen verloren, wenn mehrere Spieler gleichzeitig dasselbe Spiel bewerten.
     */
    @Modifying(flushAutomatically = true)
    @Query("""
            update Spiel s
               set s.anzahlBewertungen = s.anzahlBewertungen + :anzahlDelta,
                   s.ratingSumme       = s.ratingSumme + :summeDelta
             where s.id = :spielId
            """)
    int statistikAnpassen(@Param("spielId") Long spielId,
                          @Param("anzahlDelta") int anzahlDelta,
                          @Param("summeDelta") int summeDelta);
}
