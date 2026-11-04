-- =====================================================================
-- V3: Index für die Auswertung "Abschlüsse pro Monat" (A7, T10)
-- Begründung und Messungen: docs/nachweise/t10-performance.md
-- =====================================================================

-- Partieller Index: enthält nur Einträge mit Status GESPIELT, sortiert nach Abschlussdatum.
--  * Die Auswertung filtert genau auf status = 'GESPIELT' und einen Datumsbereich.
--  * Partiell statt vollständig: ca. 45 % der Zeilen -> kleiner Index, und Änderungen an
--    GEPLANT/AM_SPIELEN-Einträgen müssen ihn gar nicht pflegen.
CREATE INDEX IF NOT EXISTS ix_eintrag_gespielt_abschluss
    ON eintrag (abgeschlossen_am)
    WHERE status = 'GESPIELT';
