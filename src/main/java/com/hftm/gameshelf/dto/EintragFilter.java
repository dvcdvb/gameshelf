package com.hftm.gameshelf.dto;

import com.hftm.gameshelf.domain.Status;

import java.util.List;

/** Filter für die Spielerliste (A5). Jedes Feld ist optional; null = nicht filtern. */
public record EintragFilter(
        List<Status> status,
        Short ratingMin,
        Short ratingMax,
        String titel
) {}
