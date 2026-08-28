-- B11 / ADR-039: item_instance wird zurueckgebaut, statt gefuellt zu werden.
--
-- Die Tabelle steht seit dem B02-Baseline und wartete auf diesen Block. B03 hat sie mit V3_2 auf
-- den Charakter umgehaengt (ADR-011), SessionBundle laedt sie bei jedem Sitzungsstart - und
-- NIEMAND liest das Ergebnis. Sie ist nie in einen Spielpfad gelangt.
--
-- Warum sie nicht doch benutzt wird (research.md R2):
--
--   B03 speichert Rucksack und Enderchest als undurchsichtige Bukkit-Blobs
--   (character_inventory.contents / .ender_chest). Ein Gegenstand liegt also bereits dort. Eine
--   zweite Zeile je Item muesste mit dem Blob synchron gehalten werden - bei jedem Aufheben,
--   Ablegen, Verbrauchen, Verkaufen, Vernichten. Zwei Darstellungen desselben Gegenstands, die
--   genau bis zum ersten Fehler uebereinstimmen.
--
-- Dazu kommt: die Spalte rolled_values ist seit ADR-027 gegenstandslos. Ihr Kommentar zitiert
-- ADR-004 in der Fassung VOR dem Neuzuschnitt ("template id + rolls only"). Der Roll-Mechanismus
-- ist abgeschafft; ein Item traegt seit B11 nur noch die Vorlagen-ID, und zwar im
-- PersistentDataContainer (FR-001, FR-004).
--
-- Eine Tabelle, die geladen und nie geschrieben wird, ist eine Falle fuer den naechsten Block:
-- irgendwann schreibt jemand hinein, und dann gibt es zwei Wahrheiten ueber dasselbe Inventar. Der
-- Rueckbau kostet heute eine Migration; nach B12 kostet er einen Umbau von zwei Bloecken. Das ist
-- dasselbe Argument, mit dem V3_2 seinerzeit den Umzug begruendet hat.
--
-- Versionsraum: V11_1 und nicht V11_3, wie der Plan es vorsah. Flyway laeuft hier ohne
-- outOfOrder, also muessen die Nummern in der Reihenfolge stehen, in der sie entstehen. Der
-- Ausruestungszustand und die Kosmetik kommen mit ihren eigenen Geschichten und bekommen V11_2
-- und V11_3.

-- ---------------------------------------------------------------------------------------------
-- Erst nachsehen, dann loeschen.
--
-- Vorhandene Zeilen brechen die Migration ab, statt still zu verschwinden - dasselbe Vorgehen,
-- mit dem V3_2 einen nicht zuordenbaren Gegenstand lieber die ganze Migration abbrechen laesst.
-- Ein Betreiber kann sich einen abgebrochenen Start ansehen; ein klammheimlich geleerter Bestand
-- faellt niemandem auf, bis ein Spieler fragt, wo seine Sachen sind.
--
-- In der Praxis ist die Tabelle leer, weil nie ein Spielpfad hineingeschrieben hat. Die Pruefung
-- kostet also nichts und ist genau fuer den Fall da, in dem diese Annahme falsch war.
-- ---------------------------------------------------------------------------------------------
DO $$
DECLARE
    remaining BIGINT;
BEGIN
    SELECT count(*) INTO remaining FROM rpg.item_instance;

    IF remaining > 0 THEN
        RAISE EXCEPTION
            'rpg.item_instance still holds % row(s). B11 does not use this table - an item lives '
            'in the PersistentDataContainer inside B03''s inventory blob (ADR-004/ADR-027, '
            'ADR-039). Dropping it now would discard those rows silently, so this migration '
            'refuses instead. Inspect the rows, then either remove them deliberately or keep this '
            'migration out until you have.',
            remaining;
    END IF;
END $$;

DROP TABLE rpg.item_instance;
