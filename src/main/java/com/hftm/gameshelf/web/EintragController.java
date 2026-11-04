package com.hftm.gameshelf.web;

import com.hftm.gameshelf.dto.EintragAendernRequest;
import com.hftm.gameshelf.dto.EintragErstellenRequest;
import com.hftm.gameshelf.dto.EintragFilter;
import com.hftm.gameshelf.dto.EintragListenElement;
import com.hftm.gameshelf.dto.EintragSortierung;
import com.hftm.gameshelf.dto.SeiteResponse;
import com.hftm.gameshelf.domain.Status;
import com.hftm.gameshelf.dto.EintragResponse;
import com.hftm.gameshelf.dto.StatusWechselnRequest;
import com.hftm.gameshelf.dto.StatuswechselResponse;
import com.hftm.gameshelf.service.EintragService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class EintragController {

    private final EintragService eintragService;

    public EintragController(EintragService eintragService) {
        this.eintragService = eintragService;
    }

    /** Ein Eintrag gehört immer zu einem Spieler, daher verschachtelte URL. */
    @PostMapping("/spieler/{spielerId}/eintraege")
    @ResponseStatus(HttpStatus.CREATED)
    public EintragResponse erstellen(@PathVariable Long spielerId,
                                     @Valid @RequestBody EintragErstellenRequest request) {
        return eintragService.erstellen(spielerId, request);
    }

    /**
     * A5: Liste eines Spielers.
     * Beispiel: /api/spieler/1/eintraege?status=GESPIELT&status=ABGEBROCHEN&ratingMin=7&titel=ring
     *           &sortierung=RATING&richtung=DESC&seite=0&groesse=20
     */
    @GetMapping("/spieler/{spielerId}/eintraege")
    public SeiteResponse<EintragListenElement> liste(
            @PathVariable Long spielerId,
            @RequestParam(required = false) List<Status> status,
            @RequestParam(required = false) Short ratingMin,
            @RequestParam(required = false) Short ratingMax,
            @RequestParam(required = false) String titel,
            @RequestParam(defaultValue = "AKTUALISIERT") EintragSortierung sortierung,
            @RequestParam(defaultValue = "DESC") Sort.Direction richtung,
            @RequestParam(defaultValue = "0") int seite,
            @RequestParam(defaultValue = "20") int groesse) {
        EintragFilter filter = new EintragFilter(status, ratingMin, ratingMax, titel);
        return eintragService.liste(spielerId, filter, sortierung, richtung,
                Math.max(seite, 0), Math.clamp(groesse, 1, Seitenparameter.MAX_GROESSE));
    }

    @GetMapping("/eintraege/{id}")
    public EintragResponse laden(@PathVariable Long id) {
        return eintragService.laden(id);
    }

    @PutMapping("/eintraege/{id}")
    public EintragResponse aendern(@PathVariable Long id, @Valid @RequestBody EintragAendernRequest request) {
        return eintragService.aendern(id, request);
    }

    /** A4: Status wechseln (eigene Operation, weil mehrere Tabellen betroffen sind). */
    @PostMapping("/eintraege/{id}/status")
    public EintragResponse statusWechseln(@PathVariable Long id, @Valid @RequestBody StatusWechselnRequest request) {
        return eintragService.statusWechseln(id, request);
    }

    /** A4: Verlauf eines Eintrags. */
    @GetMapping("/eintraege/{id}/verlauf")
    public List<StatuswechselResponse> verlauf(@PathVariable Long id) {
        return eintragService.verlauf(id);
    }

    @DeleteMapping("/eintraege/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void loeschen(@PathVariable Long id) {
        eintragService.loeschen(id);
    }
}
