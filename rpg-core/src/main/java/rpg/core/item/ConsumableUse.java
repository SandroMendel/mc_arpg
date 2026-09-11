package rpg.core.item;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import rpg.core.session.CharacterClass;

/**
 * Ob ein Charakter dieses Verbrauchbare gerade benutzen darf — und warum nicht (FR-033 bis FR-037).
 *
 * <p><b>Vier Gründe, und jeder hat seine eigene Meldung.</b> „Geht nicht" ist keine Antwort; ein
 * Spieler, der nicht erfährt, warum sein Trank nicht wirkt, probiert es weiter und hält es dann für
 * kaputt (FR-037).
 *
 * <p><b>Der vierte Grund ist der, den niemand erwartet:</b> eine Benutzung, die <em>nichts
 * bewirken würde</em>, wird abgelehnt statt wirkungslos verbraucht (FR-036). Ein Heiltrank bei
 * vollem Leben ist der Fall — ihn zu schlucken kostet den Spieler einen Trank und bringt nichts, und
 * das merkt er erst, wenn er ihn braucht.
 *
 * <p><b>Bukkit-frei.</b> Ob geheilt werden <em>kann</em>, fragt diese Klasse über eine Naht ab; wie
 * geheilt wird, entscheidet die Plattformschicht.
 */
public final class ConsumableUse {

    /** Was aus einem Benutzungsversuch geworden ist. */
    public enum Outcome {
        /** Benutzt und verbraucht. */
        USED,
        /** Das Level reicht nicht (FR-015). */
        LEVEL_TOO_LOW,
        /** Falsche Klasse (FR-015). */
        WRONG_CLASS,
        /** Die Abklingzeit läuft noch (FR-035). */
        ON_COOLDOWN,
        /** Es würde nichts bewirken — Heilung bei vollem Leben (FR-036). */
        NO_EFFECT,
        /** Die Vorlage ist unbekannt: ein Exemplar, dessen Vorlage verschwunden ist (FR-007). */
        UNKNOWN_TEMPLATE
    }

    /**
     * Das Ergebnis.
     *
     * @param outcome was daraus wurde
     * @param remaining bei {@link Outcome#ON_COOLDOWN}: wie lange noch; sonst null
     */
    public record Result(Outcome outcome, Duration remaining) {

        public boolean isSuccess() {
            return outcome == Outcome.USED;
        }

        static Result of(Outcome outcome) {
            return new Result(outcome, null);
        }
    }

    /**
     * Ob dieser Charakter von diesem Trank überhaupt etwas hätte.
     *
     * <p><b>Die ganze Vorlage und nicht nur die Wirkung</b>, seit es Wurftränke gibt: bei einem
     * geworfenen Trank hängt die Antwort nicht am Werfer. Wer bei vollem Leben einen Heiltrank
     * auf einen verwundeten Mitspieler wirft, tut sehr wohl etwas — die Prüfung aus FR-036
     * würde ihn daran hindern, und zwar aus einem Grund, der für Wurftränke gar nicht gilt.
     *
     * <p>Ob ein Trank geworfen wird, entscheidet sein Material, und Materialien gehören der
     * Plattformschicht. Deshalb steht die Antwort dort und nicht hier: {@code rpg-core} würde
     * sonst Vanilla-Materialnamen lesen müssen (Prinzip III).
     */
    @FunctionalInterface
    public interface WouldDoSomething {
        boolean forTemplate(UUID characterId, ItemTemplate template);
    }

    private final ConsumableCooldown cooldowns;
    private final WouldDoSomething wouldDoSomething;

    public ConsumableUse(ConsumableCooldown cooldowns, WouldDoSomething wouldDoSomething) {
        this.cooldowns = Objects.requireNonNull(cooldowns, "cooldowns");
        this.wouldDoSomething = Objects.requireNonNull(wouldDoSomething, "wouldDoSomething");
    }

    /**
     * Prüft und vermerkt.
     *
     * <p><b>Die Reihenfolge ist die Anforderung:</b> erst alle Bedingungen, dann der Vermerk. Wird
     * abgelehnt, ist <b>nichts</b> verbraucht und die Abklingzeit läuft nicht an — sonst kostete ein
     * abgelehnter Versuch den Spieler die nächste Gelegenheit.
     */
    public Result use(
            Optional<ItemTemplate> template, UUID characterId, int level, CharacterClass characterClass) {
        Objects.requireNonNull(template, "template");
        Objects.requireNonNull(characterId, "characterId");

        if (template.isEmpty()) {
            return Result.of(Outcome.UNKNOWN_TEMPLATE);
        }
        ItemTemplate found = template.get();
        if (found.category() != ItemCategory.CONSUMABLE || found.effect() == null) {
            // Eine Kosmetik trinken zu wollen ist kein Fehler des Spielers, aber auch keine
            // Benutzung. Aus seiner Sicht ist es dasselbe wie eine unbekannte Vorlage.
            return Result.of(Outcome.UNKNOWN_TEMPLATE);
        }

        if (found.minLevel() != null && level < found.minLevel()) {
            return Result.of(Outcome.LEVEL_TOO_LOW);
        }
        if (found.boundClass() != null && found.boundClass() != characterClass) {
            return Result.of(Outcome.WRONG_CLASS);
        }

        ConsumableEffect effect = found.effect();
        Duration cooldown = effect.cooldown();
        if (!cooldowns.isReady(characterId, found.key(), cooldown)) {
            return new Result(
                    Outcome.ON_COOLDOWN, cooldowns.remaining(characterId, found.key(), cooldown));
        }

        if (!wouldDoSomething.forTemplate(characterId, found)) {
            // FR-036. Ein Heiltrank bei vollem Leben - abgelehnt statt wirkungslos verbraucht.
            return Result.of(Outcome.NO_EFFECT);
        }

        cooldowns.used(characterId, found.key());
        return Result.of(Outcome.USED);
    }
}
