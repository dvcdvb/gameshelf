package com.hftm.gameshelf.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SpielerRequest(
        @NotBlank @Size(max = 50) String benutzername
) {}
