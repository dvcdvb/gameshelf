-- =====================================================================
-- V2: View für die Auswertungen (A7, T6)
-- Nachfolgende Schemaänderung (T2): V1 bleibt unverändert, Flyway wendet V2
-- auf bestehende Datenbanken (V1 -> V2) und beim Neuaufbau (leer -> V1 -> V2) an.
-- =====================================================================

-- Alle BEENDETEN Einträge (gespielt oder abgebrochen) mit den Spieldaten.
-- Zentrale fachliche Definition: "Was zählt in eine Auswertung?" ist hier EINMAL festgelegt,
-- statt in jeder Abfrage die Status-Bedingung und den Join zu wiederholen.
CREATE VIEW v_beendeter_eintrag AS
SELECT e.id               AS eintrag_id,
       e.spieler_id,
       e.spiel_id,
       s.titel            AS spiel_titel,
       s.genre_id,
       e.status,
       e.rating,
       e.spielzeit_std,
       e.abgeschlossen_am
  FROM eintrag e
  JOIN spiel s ON s.id = e.spiel_id
 WHERE e.status IN ('GESPIELT', 'ABGEBROCHEN');

COMMENT ON VIEW v_beendeter_eintrag IS
    'Beendete Einträge (GESPIELT/ABGEBROCHEN) mit Spieldaten; Grundlage der Auswertungen A7';
