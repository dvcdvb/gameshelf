package com.hftm.gameshelf.service;

import com.hftm.gameshelf.domain.Spieler;
import com.hftm.gameshelf.dto.SpielerRequest;
import com.hftm.gameshelf.dto.SpielerResponse;
import com.hftm.gameshelf.exception.KonfliktException;
import com.hftm.gameshelf.exception.NichtGefundenException;
import com.hftm.gameshelf.repository.SpielerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SpielerService {

    private final SpielerRepository spielerRepository;

    public SpielerService(SpielerRepository spielerRepository) {
        this.spielerRepository = spielerRepository;
    }

    @Transactional
    public SpielerResponse erstellen(SpielerRequest request) {
        String name = request.benutzername().trim();
        if (spielerRepository.existsByBenutzernameIgnoreCase(name)) {
            throw new KonfliktException("Der Benutzername '" + name + "' ist bereits vergeben.");
        }
        return SpielerResponse.von(spielerRepository.save(new Spieler(name)));
    }

    @Transactional(readOnly = true)
    public SpielerResponse laden(Long id) {
        return SpielerResponse.von(spielerLaden(id));
    }

    @Transactional
    public SpielerResponse umbenennen(Long id, SpielerRequest request) {
        Spieler spieler = spielerLaden(id);
        String neuerName = request.benutzername().trim();
        if (!spieler.getBenutzername().equalsIgnoreCase(neuerName)
                && spielerRepository.existsByBenutzernameIgnoreCase(neuerName)) {
            throw new KonfliktException("Der Benutzername '" + neuerName + "' ist bereits vergeben.");
        }
        spieler.setBenutzername(neuerName);
        return SpielerResponse.von(spieler);
    }

    private Spieler spielerLaden(Long id) {
        return spielerRepository.findById(id)
                .orElseThrow(() -> new NichtGefundenException("Spieler " + id + " existiert nicht."));
    }
}
