package com.hftm.gameshelf.repository;

import com.hftm.gameshelf.domain.Statuswechsel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StatuswechselRepository extends JpaRepository<Statuswechsel, Long> {

    /** Verlauf eines Eintrags, chronologisch; id als zweites Kriterium für stabile Reihenfolge. */
    List<Statuswechsel> findByEintragIdOrderByGeaendertAmAscIdAsc(Long eintragId);
}
