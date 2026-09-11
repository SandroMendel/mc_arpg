/**
 * B11s Datenbankseite: der Verschleißzustand der beiden Ausrüstungsleitern.
 *
 * <p><b>Nur das.</b> Ein B11-Gegenstand hat keine eigene Zeile — er trägt seine Vorlagen-ID im
 * {@code PersistentDataContainer} und liegt damit in B07s {@code character_inventory}
 * (ADR-039, research.md R2). Die Tabelle {@code item_instance}, die es einmal gab, wurde mit
 * {@code V11_1} zurückgebaut: sie wurde bei jedem Sitzungsstart geladen und nie geschrieben.
 *
 * <p>Was hier liegt, liegt hier, weil es sich <em>ändert</em>: der Zustand fällt bei jedem Treffer.
 * Das ist zugleich der Grund, aus dem er nie direkt geschrieben wird — markiert wird im Kampfpfad,
 * geschrieben in einem Stapel (Prinzip II).
 */
package rpg.persistence.item;
