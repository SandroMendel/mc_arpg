package rpg.core.item;

import java.time.Clock;
import java.time.Instant;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import rpg.core.stats.Attribute;
import rpg.core.stats.ModifierSet;
import rpg.core.stats.SourceId;
import rpg.core.stats.SourceKind;
import rpg.core.stats.StatModifier;

/**
 * Die zeitliche Wirkung eines Tranks — über <b>B08s vorhandene Naht</b> (FR-034).
 *
 * <p><b>B11 führt keine eigene Buff-Verwaltung ein, und das ist wörtlich gemeint:</b> der Beitrag
 * geht als {@code ModifierSet} mit {@code SourceKind.BUFF} an {@code StatEngine} — genau wie B08s
 * {@code BuffEffect} —, und das Ablaufen wird von <b>demselben Durchlauf</b> getrieben, der auch
 * dessen {@code expire()} aufruft. Es entsteht keine zweite Taktung und keine wiederkehrende
 * Aufgabe je Trank (Prinzip II).
 *
 * <p>Was hier steht, ist die kleinste Buchhaltung, die eine zeitliche Quelle überhaupt braucht: wem
 * was bis wann gehört. Ohne sie wüsste niemand, was wieder abzuziehen ist — auch B08 hat sie, und
 * aus demselben Grund.
 *
 * <p><b>Zeitstempelbasiert, nicht heruntergezählt.</b> Ein Trank, der um 12:00:30 endet, endet dann,
 * ob jemand hinsieht oder nicht; der Durchlauf räumt nur auf, was schon abgelaufen <em>ist</em>.
 */
public final class ConsumableBuffs {

    private record Applied(UUID holderId, SourceId source, Instant until) {}

    /**
     * Die zwei Fragen, die dieser Block an B04 stellt — als Naht, nicht als Abhängigkeit auf die
     * ganze {@code StatEngine}.
     *
     * <p>Dieselbe Überlegung, die B08b bei {@code CharacterLookup} angestellt hat: <em>„Taking the
     * whole engine would have given B08b a dependency on B04 that ADR-027 never listed."</em> Die
     * Engine hat neunzehn Methoden; gebraucht werden zwei. Eine Abhängigkeit, die man aus Bequemlichkeit
     * erwirbt, ist trotzdem eine.
     *
     * <p>Die Verdrahtung bindet das an {@code StatEngine::apply} und {@code StatEngine::remove}.
     */
    public interface BuffSink {

        /** Legt einen Beitrag an — oder ersetzt den unter derselben Quelle. */
        void apply(UUID holderId, ModifierSet set);

        /** Nimmt ihn wieder weg. */
        void remove(UUID holderId, SourceId source);
    }

    private final Map<SourceId, Applied> active = new ConcurrentHashMap<>();
    private final BuffSink stats;
    private final Clock clock;

    public ConsumableBuffs(BuffSink stats, Clock clock) {
        this.stats = Objects.requireNonNull(stats, "stats");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Legt den zeitlichen Beitrag an.
     *
     * <p>Ein zweiter Trank derselben Vorlage <b>verlängert nicht</b>, er ersetzt: dieselbe
     * {@link SourceId}, also derselbe Eintrag. Stapeln wäre der Weg, mit zehn Tränken jeden Kampf zu
     * gewinnen, und die Abklingzeit gäbe es dann umsonst.
     */
    public void apply(UUID holderId, String templateKey, ConsumableEffect effect) {
        Objects.requireNonNull(holderId, "holderId");
        Objects.requireNonNull(templateKey, "templateKey");
        if (!effect.hasBuff()) {
            return;
        }
        SourceId source = sourceOf(templateKey, holderId);
        List<StatModifier> modifiers = new java.util.ArrayList<>(effect.buff().size());
        for (Map.Entry<Attribute, Double> entry : effect.buff().entrySet()) {
            modifiers.add(StatModifier.flat(entry.getKey(), entry.getValue()));
        }
        stats.apply(holderId, new ModifierSet(source, modifiers));
        active.put(source, new Applied(holderId, source, clock.instant().plus(effect.duration())));
    }

    /**
     * Entfernt, was abgelaufen ist.
     *
     * <p><b>Von außen getrieben</b>, auf demselben Durchlauf wie B08s Buffs — das ist es, was
     * hundert Tränke davon abhält, hundert Aufgaben zu werden.
     *
     * @return wie viele entfernt wurden
     */
    public int expire() {
        Instant now = clock.instant();
        int removed = 0;
        for (Iterator<Map.Entry<SourceId, Applied>> it = active.entrySet().iterator();
                it.hasNext(); ) {
            Applied applied = it.next().getValue();
            if (applied.until().isAfter(now)) {
                continue;
            }
            stats.remove(applied.holderId(), applied.source());
            it.remove();
            removed++;
        }
        return removed;
    }

    /** Nimmt einem Halter alles ab — Tod, Logout, Charakterwechsel. */
    public void forget(UUID holderId) {
        active.entrySet()
                .removeIf(
                        entry -> {
                            if (!entry.getValue().holderId().equals(holderId)) {
                                return false;
                            }
                            stats.remove(holderId, entry.getKey());
                            return true;
                        });
    }

    /** Wie viele gerade laufen — für die Lecksuche. */
    public int activeCount() {
        return active.size();
    }

    private static SourceId sourceOf(String templateKey, UUID holderId) {
        // Vorlage UND Halter im Schluessel: zwei Spieler mit demselben Trank sind zwei Quellen,
        // derselbe Spieler mit demselben Trank ist eine.
        return SourceId.of(SourceKind.BUFF, "item:" + templateKey + ":" + holderId);
    }
}
