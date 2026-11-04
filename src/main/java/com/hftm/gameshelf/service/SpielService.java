package com.hftm.gameshelf.service;

import com.hftm.gameshelf.config.CacheConfig;
import com.hftm.gameshelf.domain.Genre;
import com.hftm.gameshelf.domain.Spiel;
import com.hftm.gameshelf.dto.SeiteResponse;
import com.hftm.gameshelf.dto.SpielRequest;
import com.hftm.gameshelf.dto.SpielResponse;
import com.hftm.gameshelf.exception.KonfliktException;
import com.hftm.gameshelf.exception.NichtGefundenException;
import com.hftm.gameshelf.repository.GenreRepository;
import com.hftm.gameshelf.repository.SpielRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SpielService {

    private final SpielRepository spielRepository;
    private final GenreRepository genreRepository;

    public SpielService(SpielRepository spielRepository, GenreRepository genreRepository) {
        this.spielRepository = spielRepository;
        this.genreRepository = genreRepository;
    }

    @Transactional
    public SpielResponse erstellen(SpielRequest request) {
        String titel = request.titel().trim();
        if (spielRepository.existsByTitelIgnoreCase(titel)) {
            throw new KonfliktException("Ein Spiel mit dem Titel '" + titel + "' existiert bereits.");
        }
        Spiel spiel = new Spiel(genreLaden(request.genreId()), titel, request.erscheinungsjahr());
        return SpielResponse.von(spielRepository.save(spiel));
    }

    @Transactional(readOnly = true)
    public SpielResponse laden(Long id) {
        return SpielResponse.von(spielMitGenre(id));
    }

    @Transactional(readOnly = true)
    public SeiteResponse<SpielResponse> suchen(String titel, Pageable pageable) {
        String suchtext = titel == null ? "" : titel.trim();
        return SeiteResponse.von(spielRepository.findByTitelContainingIgnoreCase(suchtext, pageable), SpielResponse::von);
    }

    /** Titel und Genre erscheinen in der Top-Liste (T12) -> Cache nach dem Commit leeren. */
    @Transactional
    @CacheEvict(cacheNames = CacheConfig.TOP_SPIELE, allEntries = true)
    public SpielResponse aendern(Long id, SpielRequest request) {
        Spiel spiel = spielMitGenre(id);
        String neuerTitel = request.titel().trim();
        boolean titelGeaendert = !spiel.getTitel().equalsIgnoreCase(neuerTitel);
        if (titelGeaendert && spielRepository.existsByTitelIgnoreCase(neuerTitel)) {
            throw new KonfliktException("Ein Spiel mit dem Titel '" + neuerTitel + "' existiert bereits.");
        }
        spiel.setTitel(neuerTitel);
        spiel.setGenre(genreLaden(request.genreId()));
        spiel.setErscheinungsjahr(request.erscheinungsjahr());
        return SpielResponse.von(spiel); // kein save() nötig: geänderte Entity wird beim Commit gespeichert
    }

    /** Fachlich sinnvolles "Löschen" (T3): Spiele werden deaktiviert, Einträge bleiben erhalten. */
    @Transactional
    public SpielResponse deaktivieren(Long id) {
        Spiel spiel = spielMitGenre(id);
        spiel.deaktivieren();
        return SpielResponse.von(spiel);
    }

    @Transactional
    public SpielResponse aktivieren(Long id) {
        Spiel spiel = spielMitGenre(id);
        spiel.aktivieren();
        return SpielResponse.von(spiel);
    }

    private Spiel spielMitGenre(Long id) {
        return spielRepository.findWithGenreById(id)
                .orElseThrow(() -> new NichtGefundenException("Spiel " + id + " existiert nicht."));
    }

    private Genre genreLaden(Long genreId) {
        return genreRepository.findById(genreId)
                .orElseThrow(() -> new NichtGefundenException("Genre " + genreId + " existiert nicht."));
    }
}
