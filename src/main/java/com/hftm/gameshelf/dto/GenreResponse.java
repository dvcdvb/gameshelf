package com.hftm.gameshelf.dto;

import com.hftm.gameshelf.domain.Genre;

public record GenreResponse(Long id, String name) {

    public static GenreResponse von(Genre genre) {
        return new GenreResponse(genre.getId(), genre.getName());
    }
}
