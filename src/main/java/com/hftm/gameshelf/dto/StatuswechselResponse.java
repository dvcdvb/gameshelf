package com.hftm.gameshelf.dto;

import com.hftm.gameshelf.domain.Status;
import com.hftm.gameshelf.domain.Statuswechsel;

import java.time.OffsetDateTime;

public record StatuswechselResponse(Long id, Status alterStatus, Status neuerStatus, OffsetDateTime geaendertAm) {

    public static StatuswechselResponse von(Statuswechsel s) {
        return new StatuswechselResponse(s.getId(), s.getAlterStatus(), s.getNeuerStatus(), s.getGeaendertAm());
    }
}
