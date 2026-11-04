package com.hftm.gameshelf.repository;

import com.hftm.gameshelf.domain.Spieler;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpielerRepository extends JpaRepository<Spieler, Long> {

    boolean existsByBenutzernameIgnoreCase(String benutzername);
}
