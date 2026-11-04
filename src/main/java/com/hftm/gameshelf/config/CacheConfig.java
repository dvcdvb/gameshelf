package com.hftm.gameshelf.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.transaction.TransactionAwareCacheManagerProxy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * T12: Cache für die Auswertung "Top-Spiele" (A7).
 *
 * Aktualitätsregel:
 *  - Jede Änderung über die API, die eine Bewertung beeinflusst, leert den Cache  -> sofort aktuell.
 *  - Änderungen an der API vorbei (z. B. Testdaten-Skript) sind spätestens nach 10 Minuten sichtbar (TTL).
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String TOP_SPIELE = "topSpiele";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager caffeine = new CaffeineCacheManager(TOP_SPIELE);
        caffeine.setCaffeine(Caffeine.newBuilder()
                .maximumSize(500)                           // Obergrenze für verschiedene Parameter-Kombinationen
                .expireAfterWrite(Duration.ofMinutes(10))   // Sicherheitsnetz für Änderungen an der API vorbei
                .recordStats());                            // Treffer/Fehlzugriffe zählen (Nachweis)

        // Transaktionsbewusst: Leeren/Einfügen erst NACH dem Commit. Würde der Cache schon vor dem Commit
        // geleert, könnte ein paralleler Lesezugriff noch den alten DB-Stand laden und wieder cachen.
        // Bei einem Rollback wird der Cache gar nicht geleert (die Daten haben sich ja nicht geändert).
        return new TransactionAwareCacheManagerProxy(caffeine);
    }
}
