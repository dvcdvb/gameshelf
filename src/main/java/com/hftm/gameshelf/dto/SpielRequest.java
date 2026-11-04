package com.hftm.gameshelf.dto;

import jakarta.validation.constraints.*;

public record SpielRequest(
        @NotBlank @Size(max = 200) String titel,
        @NotNull Long genreId,
        @Min(1950) @Max(2100) Short erscheinungsjahr
) {}
