package com.hftm.gameshelf.web;

import com.hftm.gameshelf.dto.SeiteResponse;
import com.hftm.gameshelf.dto.SpielRequest;
import com.hftm.gameshelf.dto.SpielResponse;
import com.hftm.gameshelf.service.SpielService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/spiele")
public class SpielController {

    private final SpielService spielService;

    public SpielController(SpielService spielService) {
        this.spielService = spielService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SpielResponse erstellen(@Valid @RequestBody SpielRequest request) {
        return spielService.erstellen(request);
    }

    /** A1: Titelsuche, seitenweise, stabil sortiert nach Titel und bei Gleichstand nach id. */
    @GetMapping
    public SeiteResponse<SpielResponse> suchen(@RequestParam(required = false) String titel,
                                               @RequestParam(defaultValue = "0") int seite,
                                               @RequestParam(defaultValue = "20") int groesse) {
        PageRequest pageRequest = PageRequest.of(Math.max(seite, 0),
                Math.clamp(groesse, 1, Seitenparameter.MAX_GROESSE),
                Sort.by("titel").ascending().and(Sort.by("id").ascending()));
        return spielService.suchen(titel, pageRequest);
    }

    @GetMapping("/{id}")
    public SpielResponse laden(@PathVariable Long id) {
        return spielService.laden(id);
    }

    @PutMapping("/{id}")
    public SpielResponse aendern(@PathVariable Long id, @Valid @RequestBody SpielRequest request) {
        return spielService.aendern(id, request);
    }

    @PostMapping("/{id}/deaktivieren")
    public SpielResponse deaktivieren(@PathVariable Long id) {
        return spielService.deaktivieren(id);
    }

    @PostMapping("/{id}/aktivieren")
    public SpielResponse aktivieren(@PathVariable Long id) {
        return spielService.aktivieren(id);
    }
}
