-- B11: die gekauften Trimfarben eines Charakters (FR-067 bis FR-073).
--
-- Version space: V11_1 hat item_instance zurueckgebaut, V11_2 traegt den Verschleiss. Das hier ist
-- die dritte und letzte Migration dieses Blocks.
--
-- Warum MEHRERE Zeilen je Charakter, anders als beim Verschleiss:
-- wer drei Farben gekauft hat, besitzt drei. Der Besitz ist das, was den Grind ausmacht (Q2) - eine
-- Spalte "aktuelle Farbe" wuerde die anderen beiden vergessen, und ein Wechsel waere ein Kauf.
--
-- Warum KEIN Fremdschluessel auf die Konfiguration:
-- ein Vorlagenschluessel steht in items.yml, nicht in der Datenbank. Verschwindet eine Farbe aus der
-- Konfiguration, bleibt die Zeile stehen und das Aussehen faellt auf die Stufe zurueck (FR-073).
-- Die Zeile zu loeschen waere Datenverlust fuer einen Betreiberfehler.
--
-- Der TEILINDEX ist der eigentliche Punkt dieser Datei:
-- FR-071 sagt "hoechstens eine getragene Farbe je Charakter". Das steht auch in
-- CosmeticApplication - dort, damit der Spieler eine Antwort bekommt. Hier steht es, damit es
-- STIMMT. Eine Regel, die nur in der Anwendung lebt, gilt genau so lange, wie jeder Schreiber durch
-- die Anwendung geht; ein zweiter Weg - ein Betreiberbefehl, ein Reparaturskript, ein spaeterer
-- Block - haelt sich nicht daran, und der Fehler faellt erst auf, wenn zwei Farben getragen werden
-- und niemand sagen kann, welche gilt.

CREATE TABLE rpg.character_cosmetic (
    character_id UUID    NOT NULL
                         REFERENCES rpg.character (character_id) ON DELETE CASCADE,

    -- Der Schluessel aus items.yml. Ein Text und kein Fremdschluessel - siehe Kopf.
    template_key TEXT    NOT NULL,

    -- Ob sie gerade getragen wird. Hoechstens eine je Charakter; erzwungen vom Index unten.
    applied      BOOLEAN NOT NULL DEFAULT false,

    acquired_at  TIMESTAMPTZ NOT NULL DEFAULT now(),

    data_version INTEGER NOT NULL DEFAULT 1,
    revision     BIGINT  NOT NULL DEFAULT 0,
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- Eine Farbe wird nicht zweimal gekauft.
    PRIMARY KEY (character_id, template_key),

    CONSTRAINT chk_cosmetic_template_key CHECK (length(template_key) > 0)
);

-- FR-071 als DATENBANKREGEL statt als Absichtserklaerung. Ein partieller UNIQUE-Index: die
-- Bedingung greift nur fuer Zeilen mit applied = true, also duerfen beliebig viele Farben besessen
-- und genau eine getragen werden.
CREATE UNIQUE INDEX uq_cosmetic_one_applied
    ON rpg.character_cosmetic (character_id)
    WHERE applied;

-- ON DELETE CASCADE oben regelt zugleich die Anonymisierung: B02s Loeschpfad entfernt den Charakter,
-- und diese Zeilen gehen mit, ohne dass B02 von B11 wissen muss.

COMMENT ON TABLE rpg.character_cosmetic IS
    'Gekaufte Trimfarben je Charakter (B11). Anwendbar erst auf der Hoechststufe - siehe FR-069.';
