package com.hftm.gameshelf.service;

import com.hftm.gameshelf.domain.Genre;
import com.hftm.gameshelf.dto.GenreRequest;
import com.hftm.gameshelf.dto.GenreResponse;
import com.hftm.gameshelf.exception.KonfliktException;
import com.hftm.gameshelf.repository.GenreRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class GenreService {

    private final GenreRepository genreRepository;

    public GenreService(GenreRepository genreRepository) {
        this.genreRepository = genreRepository;
    }

    @Transactional
    public GenreResponse erstellen(GenreRequest request) {
        String name = request.name().trim();
        if (genreRepository.existsByNameIgnoreCase(name)) {
            throw new KonfliktException("Das Genre '" + name + "' existiert bereits.");
        }
        return GenreResponse.von(genreRepository.save(new Genre(name)));
    }

    /** Genres sind wenige Stammdaten, daher ohne Pagination. */
    @Transactional(readOnly = true)
    public List<GenreResponse> alle() {
        return genreRepository.findAll(Sort.by("name")).stream().map(GenreResponse::von).toList();
    }
}
