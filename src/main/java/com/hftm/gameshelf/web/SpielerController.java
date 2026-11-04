package com.hftm.gameshelf.web;

import com.hftm.gameshelf.dto.SpielerRequest;
import com.hftm.gameshelf.dto.SpielerResponse;
import com.hftm.gameshelf.service.SpielerService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/spieler")
public class SpielerController {

    private final SpielerService spielerService;

    public SpielerController(SpielerService spielerService) {
        this.spielerService = spielerService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SpielerResponse erstellen(@Valid @RequestBody SpielerRequest request) {
        return spielerService.erstellen(request);
    }

    @GetMapping("/{id}")
    public SpielerResponse laden(@PathVariable Long id) {
        return spielerService.laden(id);
    }

    @PutMapping("/{id}")
    public SpielerResponse umbenennen(@PathVariable Long id, @Valid @RequestBody SpielerRequest request) {
        return spielerService.umbenennen(id, request);
    }
}
