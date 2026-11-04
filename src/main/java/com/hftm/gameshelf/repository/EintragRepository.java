package com.hftm.gameshelf.repository;

import com.hftm.gameshelf.domain.Eintrag;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EintragRepository extends JpaRepository<Eintrag, Long> {

    boolean existsBySpielerIdAndSpielId(Long spielerId, Long spielId);
}
