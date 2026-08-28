package rpg.core.item;

import java.util.Objects;
import java.util.OptionalDouble;
import java.util.UUID;

import rpg.core.classes.LadderSlot;

/**
 * Der Zustand einer Leiter hat sich geändert (FR-051).
 *
 * <p><b>Warum ein Ereignis und nicht ein Aufruf.</b> Drei Blöcke wollen davon wissen und keiner
 * gehört hierher: B13 zeichnet den Haltbarkeitsbalken neu, B12 wertet aus, und die Warnung an den
 * Spieler ist Darstellung. Über den Bus bleibt jeder von ihnen abbestellbar, und keiner von ihnen
 * kann den Kampfpfad aufhalten.
 *
 * <p>{@code crossedWarning} ist gesetzt, wenn dieser Übergang eine Warnschwelle unterschritten hat
 * <em>und</em> die Ruhezeit dieser Schwelle abgelaufen war. Damit trägt das Ereignis die Entscheidung
 * „jetzt sagen" schon in sich — und niemand muss sie ein zweites Mal treffen, was die zuverlässigste
 * Art wäre, zwei Meldungen für einen Treffer zu erzeugen.
 *
 * @param characterId wessen Ausrüstung
 * @param slot welche Leiter
 * @param before der Zustand vorher
 * @param after der Zustand danach
 * @param crossedWarning die unterschrittene Warnschwelle, falls jetzt zu melden
 */
public record GearConditionChangedEvent(
        UUID characterId,
        LadderSlot slot,
        double before,
        double after,
        OptionalDouble crossedWarning) {

    public GearConditionChangedEvent {
        Objects.requireNonNull(characterId, "characterId");
        Objects.requireNonNull(slot, "slot");
        Objects.requireNonNull(crossedWarning, "crossedWarning");
    }

    /** Ob überhaupt etwas passiert ist — ein Treffer für null Schaden ändert nichts. */
    public boolean isChange() {
        return before != after;
    }
}
