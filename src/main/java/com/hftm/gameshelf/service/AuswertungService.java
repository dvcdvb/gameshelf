package com.hftm.gameshelf.service;

import com.hftm.gameshelf.config.CacheConfig;
import com.hftm.gameshelf.dto.auswertung.*;
import com.hftm.gameshelf.exception.GeschaeftsregelException;
import com.hftm.gameshelf.exception.NichtGefundenException;
import com.hftm.gameshelf.repository.AuswertungAbfrage;
import com.hftm.gameshelf.repository.SpielerRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class AuswertungService {

    private final AuswertungAbfrage auswertungAbfrage;
    private final SpielerRepository spielerRepository;

    public AuswertungService(AuswertungAbfrage auswertungAbfrage, SpielerRepository spielerRepository) {
        this.auswertungAbfrage = auswertungAbfrage;
        this.spielerRepository = spielerRepository;
    }

    /**
     * T12: Ergebnis wird pro Parameter-Kombination gecacht.
     * Schlüssel: (minBewertungen, genreId, limit) – genau die Werte, die das Ergebnis bestimmen.
     * limit wird bereits im Controller auf 1..100 begrenzt, damit z. B. 500 und 100 denselben Eintrag nutzen.
     * Ungültige Parameter werfen eine Exception und werden deshalb nicht gecacht.
     */
    @Cacheable(cacheNames = CacheConfig.TOP_SPIELE, key = "{#minBewertungen, #genreId, #limit}")
    public List<TopSpielResponse> topSpiele(int minBewertungen, Long genreId, int limit) {
        if (minBewertungen < 1) {
            throw new GeschaeftsregelException("minBewertungen muss mindestens 1 sein.");
        }
        // unveränderliche Kopie: der gecachte Wert darf von Aufrufern nicht verändert werden können
        return List.copyOf(auswertungAbfrage.topSpiele(minBewertungen, genreId, limit));
    }

    public List<GenreAbbruchResponse> abbruchquoteJeGenre(int minBeendet) {
        if (minBeendet < 1) {
            throw new GeschaeftsregelException("minBeendet muss mindestens 1 sein.");
        }
        return auswertungAbfrage.abbruchquoteJeGenre(minBeendet);
    }

    public SpielerStatistikResponse spielerStatistik(Long spielerId) {
        pruefeSpieler(spielerId);
        List<StatusStatistik> zeilen = auswertungAbfrage.statusVerteilung(spielerId);
        StatusStatistik gesamt = zeilen.stream().filter(z -> z.status() == null).findFirst().orElse(null);
        List<StatusStatistik> proStatus = zeilen.stream().filter(z -> z.status() != null).toList();
        return new SpielerStatistikResponse(spielerId, proStatus, gesamt);
    }

    public List<MonatsAbschlussResponse> abschluesseProMonat(LocalDate von, LocalDate bis, Long spielerId) {
        if (von.isAfter(bis)) {
            throw new GeschaeftsregelException("'von' darf nicht nach 'bis' liegen.");
        }
        if (spielerId != null) {
            pruefeSpieler(spielerId);
        }
        return auswertungAbfrage.abschluesseProMonat(von, bis, spielerId);
    }

    private void pruefeSpieler(Long spielerId) {
        if (!spielerRepository.existsById(spielerId)) {
            throw new NichtGefundenException("Spieler " + spielerId + " existiert nicht.");
        }
    }
}
