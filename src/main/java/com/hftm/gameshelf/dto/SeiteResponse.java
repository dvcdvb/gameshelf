package com.hftm.gameshelf.dto;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Eigene, stabile JSON-Struktur für seitenweise Ergebnisse
 * (statt Springs internes PageImpl direkt zu serialisieren).
 */
public record SeiteResponse<T>(
        List<T> inhalt,
        int seite,
        int groesse,
        long totalElemente,
        int totalSeiten
) {

    public static <E, T> SeiteResponse<T> von(Page<E> page, Function<E, T> mapper) {
        return new SeiteResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
