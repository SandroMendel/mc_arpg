package rpg.core.item;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import rpg.core.stats.Attribute;

/**
 * Was ein Verbrauchbares bewirkt (FR-033, FR-034).
 *
 * <p><b>Drei Wirkungsarten, und keine vierte.</b> Sofortige Heilung, sofortige
 * Mana-Wiederherstellung, und ein zeitlich begrenzter Attributbeitrag. Mehr braucht das Schema
 * nicht: was ein Trank tun koennen soll, ist eine Inhaltsfrage, und Inhalt entsteht aus
 * Konfiguration (Prinzip V).
 *
 * <p><b>Der zeitliche Beitrag laeuft ueber {@code SourceKind.BUFF}</b> - dieselbe Naht, die B08 fuer
 * Faehigkeiten benutzt. B11 fuehrt <b>keine</b> eigene Buff-Verwaltung und <b>keine</b> eigene
 * Ablaufpruefung ein: die Laufzeit wird zeitstempelbasiert ausgewertet, nicht ueber eine
 * wiederkehrende Aufgabe (Prinzip II, FR-034).
 *
 * <p><b>Mindestens eine Wirkung muss gesetzt sein.</b> Ein Verbrauchbares ohne Wirkung waere eine
 * Vorlage, die FR-036 bei jeder Benutzung ablehnt - der Spieler koennte sie nie benutzen und
 * wuesste nicht warum. Das ist ein Konfigurationsfehler und wird beim Start abgelehnt.
 *
 * <p><b>Die Werte sind fest.</b> Kein Wuerfeln, keine Wertebereiche, keine Affixe (ADR-027). Zwei
 * Traenke desselben Typs sind identisch - {@code TwoCopiesAreIdenticalTest} haelt das fest.
 *
 * @param heal sofortige Lebensenergie, gedeckelt auf das Maximum des Charakters; {@code null} wenn
 *     dieser Trank nicht heilt
 * @param mana sofortige Mana-Wiederherstellung; {@code null} wenn dieser Trank kein Mana gibt
 * @param buff zeitlicher Beitrag je Attribut; leer wenn dieser Trank nicht wirkt
 * @param duration Laufzeit des Beitrags; {@code null} genau dann, wenn {@code buff} leer ist
 * @param cooldown Abklingzeit, innerhalb derer eine zweite Benutzung abgelehnt wird, ohne zu
 *     verbrauchen (FR-035); {@code null} heisst: keine
 */
public record ConsumableEffect(
        Double heal, Double mana, Map<Attribute, Double> buff, Duration duration, Duration cooldown) {

    public ConsumableEffect {
        buff = buff == null ? Map.of() : Map.copyOf(buff);

        if (heal != null && !(heal > 0.0)) {
            throw new IllegalArgumentException("heal must be positive when set, but was " + heal);
        }
        if (mana != null && !(mana > 0.0)) {
            throw new IllegalArgumentException("mana must be positive when set, but was " + mana);
        }
        // Beides oder keines. Ein Beitrag ohne Dauer liefe ewig, eine Dauer ohne Beitrag waere ein
        // Timer ohne Wirkung - beides ist ein Konfigurationsfehler und kein halber Trank.
        if (buff.isEmpty() != (duration == null)) {
            throw new IllegalArgumentException(
                    "buff and duration must be set together or not at all, but were "
                            + buff
                            + " / "
                            + duration);
        }
        if (duration != null && (duration.isZero() || duration.isNegative())) {
            throw new IllegalArgumentException("duration must be positive, but was " + duration);
        }
        if (cooldown != null && cooldown.isNegative()) {
            throw new IllegalArgumentException("cooldown must not be negative, but was " + cooldown);
        }
        if (heal == null && mana == null && buff.isEmpty()) {
            throw new IllegalArgumentException(
                    "a consumable needs at least one effect - heal, mana or buff. A template"
                            + " without one would be refused on every use (FR-036), and the player"
                            + " would never learn why");
        }
    }

    /** Ein Trank, der nur heilt - der haeufigste Fall. */
    public static ConsumableEffect healing(double amount, Duration cooldown) {
        return new ConsumableEffect(amount, null, Map.of(), null, cooldown);
    }

    /** Ein Trank, der nur Mana gibt. */
    public static ConsumableEffect mana(double amount, Duration cooldown) {
        return new ConsumableEffect(null, amount, Map.of(), null, cooldown);
    }

    /** Ein Trank mit zeitlicher Wirkung ueber {@code SourceKind.BUFF}. */
    public static ConsumableEffect buff(
            Map<Attribute, Double> contributions, Duration duration, Duration cooldown) {
        return new ConsumableEffect(
                null, null, new EnumMap<>(Objects.requireNonNull(contributions)), duration, cooldown);
    }

    public Optional<Double> healAmount() {
        return Optional.ofNullable(heal);
    }

    public Optional<Double> manaAmount() {
        return Optional.ofNullable(mana);
    }

    public Optional<Duration> buffDuration() {
        return Optional.ofNullable(duration);
    }

    public Optional<Duration> cooldownOrNone() {
        return Optional.ofNullable(cooldown);
    }

    /** Ob dieser Trank ueberhaupt einen zeitlichen Beitrag hat. */
    public boolean hasBuff() {
        return !buff.isEmpty();
    }
}
