package com.hftm.gameshelf.web;

import com.hftm.gameshelf.dto.auswertung.*;
import com.hftm.gameshelf.service.AuswertungService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/** A7: Auswertungen. */
@RestController
@RequestMapping("/api/auswertungen")
public class AuswertungController {

    private static final int MAX_LIMIT = 100;

    private final AuswertungService auswertungService;

    public AuswertungController(AuswertungService auswertungService) {
        this.auswertungService = auswertungService;
    }

    @GetMapping("/top-spiele")
    public List<TopSpielResponse> topSpiele(@RequestParam(defaultValue = "1") int minBewertungen,
                                            @RequestParam(required = false) Long genreId,
                                            @RequestParam(defaultValue = "10") int limit) {
        // limit hier normalisieren, damit gleiche Ergebnisse denselben Cache-Schlüssel haben
        return auswertungService.topSpiele(minBewertungen, genreId, Math.clamp(limit, 1, MAX_LIMIT));
    }

    @GetMapping("/abbruchquote-genres")
    public List<GenreAbbruchResponse> abbruchquoteJeGenre(@RequestParam(defaultValue = "1") int minBeendet) {
        return auswertungService.abbruchquoteJeGenre(minBeendet);
    }

    @GetMapping("/spieler/{spielerId}/statistik")
    public SpielerStatistikResponse spielerStatistik(@PathVariable Long spielerId) {
        return auswertungService.spielerStatistik(spielerId);
    }

    @GetMapping("/abschluesse-pro-monat")
    public List<MonatsAbschlussResponse> abschluesseProMonat(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate von,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate bis,
            @RequestParam(required = false) Long spielerId) {
        return auswertungService.abschluesseProMonat(von, bis, spielerId);
    }
}
