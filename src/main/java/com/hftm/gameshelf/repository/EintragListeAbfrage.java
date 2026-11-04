package com.hftm.gameshelf.repository;

import com.hftm.gameshelf.dto.EintragFilter;
import com.hftm.gameshelf.dto.EintragListenElement;
import com.hftm.gameshelf.dto.EintragSortierung;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A5 / T6 / T7: Liste eines Spielers – gefiltert, stabil sortiert und seitenweise,
 * vollständig in der Datenbank. Ergebnis ist eine DTO-Projektion statt Entities.
 *
 * Die WHERE-Klausel wird dynamisch aus den gesetzten Filtern zusammengebaut.
 * Benutzereingaben gelangen dabei NUR über benannte Parameter in die Abfrage (kein SQL-Injection-Risiko).
 */
@Repository
public class EintragListeAbfrage {

    private static final String SELECT = """
            select new com.hftm.gameshelf.dto.EintragListenElement(
                       e.id, s.id, s.titel, g.name, e.status, e.rating,
                       e.spielzeitStd, e.gestartetAm, e.abgeschlossenAm, e.aktualisiertAm)
              from Eintrag e
              join e.spiel s
              join s.genre g
            """;

    private static final String COUNT = """
            select count(e)
              from Eintrag e
              join e.spiel s
            """;

    @PersistenceContext
    private EntityManager entityManager;

    public Page<EintragListenElement> suchen(Long spielerId, EintragFilter filter,
                                             EintragSortierung sortierung, Sort.Direction richtung,
                                             int seite, int groesse) {
        Map<String, Object> parameter = new LinkedHashMap<>();
        StringBuilder where = new StringBuilder(" where e.spieler.id = :spielerId");
        parameter.put("spielerId", spielerId);

        if (filter.status() != null && !filter.status().isEmpty()) {
            where.append(" and e.status in :status");
            parameter.put("status", filter.status());
        }
        if (filter.ratingMin() != null) {
            where.append(" and e.rating >= :ratingMin");
            parameter.put("ratingMin", filter.ratingMin());
        }
        if (filter.ratingMax() != null) {
            where.append(" and e.rating <= :ratingMax");
            parameter.put("ratingMax", filter.ratingMax());
        }
        if (filter.titel() != null && !filter.titel().isBlank()) {
            where.append(" and lower(s.titel) like :titel escape '!'");
            parameter.put("titel", "%" + likeEscapen(filter.titel().trim().toLowerCase()) + "%");
        }

        // Stabile Sortierung: gewähltes Feld + id als eindeutiges Zweitkriterium.
        // Ohne die id wäre die Reihenfolge gleicher Werte (z. B. zwei Mal Rating 9) zufällig,
        // und Einträge könnten beim Blättern doppelt erscheinen oder fehlen.
        String dir = richtung == Sort.Direction.ASC ? "asc" : "desc";
        String orderBy = " order by " + sortierung.jpqlAusdruck() + " " + dir + " nulls last, e.id " + dir;

        TypedQuery<EintragListenElement> abfrage =
                entityManager.createQuery(SELECT + where + orderBy, EintragListenElement.class);
        TypedQuery<Long> zaehlung = entityManager.createQuery(COUNT + where, Long.class);
        parameter.forEach((name, wert) -> {
            abfrage.setParameter(name, wert);
            zaehlung.setParameter(name, wert);
        });

        // Pagination in der DB: Hibernate erzeugt "offset ? rows fetch first ? rows only"
        abfrage.setFirstResult(seite * groesse);
        abfrage.setMaxResults(groesse);

        List<EintragListenElement> inhalt = abfrage.getResultList();
        long total = zaehlung.getSingleResult();
        return new PageImpl<>(inhalt, PageRequest.of(seite, groesse), total);
    }

    /** % und _ aus der Benutzereingabe sollen wörtlich gesucht werden, nicht als Platzhalter wirken. */
    private static String likeEscapen(String text) {
        return text.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }
}
