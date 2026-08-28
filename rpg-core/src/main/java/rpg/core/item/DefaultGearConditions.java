package rpg.core.item;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import rpg.core.classes.LadderSlot;
import rpg.core.combat.DamageOrigin;
import rpg.core.combat.DeathCause;
import rpg.core.event.EventBus;

/**
 * Der lebende Ausrüstungszustand (FR-039 bis FR-051).
 *
 * <p><b>Im Speicher, nicht in der Datenbank</b> (Prinzip IV): der Zustand ändert sich bei jedem
 * Treffer, und ein Schreibvorgang je Treffer wäre Datenbankverkehr im Kampfpfad. Geschrieben wird
 * über B02s Write-Behind, und diese Klasse merkt nur vor.
 *
 * <p><b>Ein unbekannter Charakter ist voll ausgerüstet, nicht kaputt.</b> Wer noch nicht geladen
 * ist, bekommt {@code 100} und den Faktor {@code 1.0}. Die Alternative — einen Standardwert unter
 * 100 anzunehmen — würde einen Ladefehler in eine stille Schwächung übersetzen, und niemand käme auf
 * die Idee, dort zu suchen.
 *
 * <p><b>Die Warnung wird hier entschieden, nicht beim Empfänger.</b> Höchstens eine je Schwelle und
 * Ruhezeit (FR-051): eine Meldung bei jedem Treffer wäre eine Meldung, die niemand mehr liest — und
 * dann fällt die eine auf, auf die es ankam, auch nicht mehr auf.
 */
public final class DefaultGearConditions implements GearConditions {

    private final Supplier<WearCurve> curve;
    private final GearConditionRepository repository;
    private final EventBus events;
    private final Clock clock;

    /** Der maßgebliche Stand, solange der Charakter da ist. */
    private final Map<UUID, GearCondition> live = new ConcurrentHashMap<>();

    /** Wann zuletzt vor welcher Schwelle gewarnt wurde. */
    private final Map<WarnKey, Long> lastWarned = new ConcurrentHashMap<>();

    private record WarnKey(UUID characterId, LadderSlot slot, double threshold) {}

    public DefaultGearConditions(
            Supplier<WearCurve> curve,
            GearConditionRepository repository,
            EventBus events,
            Clock clock) {
        this.curve = Objects.requireNonNull(curve, "curve");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.events = Objects.requireNonNull(events, "events");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    // --- Lesen -----------------------------------------------------------------------

    @Override
    public double conditionOf(UUID characterId, LadderSlot slot) {
        GearCondition current = live.get(characterId);
        return current == null ? WearCurve.FULL : current.of(slot);
    }

    @Override
    public double factorOf(UUID characterId, LadderSlot slot) {
        return factorForCondition(conditionOf(characterId, slot));
    }

    @Override
    public double factorForCondition(double condition) {
        return curve.get().factorFor(condition);
    }

    /** Der ganze Stand eines Charakters — für das Schreiben und für die Anzeige. */
    public Optional<GearCondition> of(UUID characterId) {
        return Optional.ofNullable(live.get(characterId));
    }

    // --- Sitzungsgrenzen -------------------------------------------------------------

    /** Beim Eintritt: der geladene Stand, oder ein voller für einen Charakter ohne Zeile. */
    public void put(GearCondition condition) {
        live.put(condition.characterId(), condition);
    }

    /** Beim Austritt. Die Warnvermerke gehen mit — sonst wüchsen sie die ganze Laufzeit lang. */
    public void forget(UUID characterId) {
        live.remove(characterId);
        lastWarned.keySet().removeIf(key -> key.characterId().equals(characterId));
    }

    public int loadedCount() {
        return live.size();
    }

    // --- Verschleiß ------------------------------------------------------------------

    /**
     * Erlittener Schaden.
     *
     * @param incoming der <b>ankommende</b> Schaden vor der Abwehr (FR-040a). Am durchgekommenen
     *     gemessen entstünde eine Abwärtsspirale: verschlissene Rüstung lässt mehr durch, das nutzt
     *     sie schneller ab, und gute Rüstung wäre doppelt belohnt.
     */
    public void onDamageTaken(
            UUID characterId, DamageOrigin origin, boolean victimIsSummon, double incoming) {
        WearCurve current = curve.get();
        apply(
                characterId,
                WearRules.onDamageTaken(origin, victimIsSummon),
                before -> current.afterDamageTaken(before, incoming));
    }

    /** Ausgeteilter Schaden — nur Autoattacks, und nur auf die Waffe (FR-041). */
    public void onDamageDealt(
            UUID characterId, DamageOrigin origin, boolean attackerIsSummon, double dealt) {
        WearCurve current = curve.get();
        apply(
                characterId,
                WearRules.onDamageDealt(origin, attackerIsSummon),
                before -> current.afterDamageDealt(before, dealt));
    }

    /** Der Tod — beide Leitern, und um ein Vielfaches (FR-042, FR-043). */
    public void onDeath(UUID characterId, DeathCause cause) {
        WearCurve current = curve.get();
        apply(characterId, WearRules.onDeath(cause), current::afterDeath);
    }

    /**
     * Setzt eine Leiter wieder auf voll — die bezahlte Reparatur (FR-052).
     *
     * @return {@code false}, wenn nichts zu reparieren war; der Aufrufer bucht dann nicht (FR-054)
     */
    public boolean repair(UUID characterId, LadderSlot slot) {
        GearCondition before = live.get(characterId);
        if (before == null || !before.isWorn(slot)) {
            return false;
        }
        double was = before.of(slot);
        live.put(characterId, before.with(slot, WearCurve.FULL));
        repository.markDirty(characterId);
        // Auch das ist eine Aenderung, die B13 zeichnen und B12 auswerten will - und der einzige
        // Uebergang nach OBEN. Ohne ihn zeigte der Balken nach einer Reparatur den alten Stand.
        events.publish(
                new GearConditionChangedEvent(
                        characterId, slot, was, WearCurve.FULL, OptionalDouble.empty()));
        // Und der Warnvermerk faellt weg: unterschreitet der Zustand die Schwelle spaeter erneut,
        // ist das eine neue Nachricht und keine wiederholte.
        lastWarned.keySet().removeIf(key -> key.characterId().equals(characterId) && key.slot() == slot);
        return true;
    }

    /**
     * Der gemeinsame Weg jeder Änderung.
     *
     * <p>Ein Vorgang, eine Berechnung je Slot, ein Ereignis je wirklich geänderter Leiter. Nichts
     * wird veröffentlicht, was sich nicht geändert hat: ein Treffer für null Schaden ist kein
     * Verschleiß, und ein Ereignis darüber wäre Arbeit für jeden Zuhörer ohne Anlass.
     */
    private void apply(
            UUID characterId, Set<LadderSlot> slots, java.util.function.DoubleUnaryOperator reduce) {
        if (slots.isEmpty()) {
            return;
        }
        GearCondition before = live.get(characterId);
        if (before == null) {
            // Nicht geladen. Kein Zustand, den man verschleissen koennte - und einen anzulegen
            // hiesse, ihn ohne den gespeicherten Stand zu erfinden.
            return;
        }

        GearCondition after = before;
        Map<LadderSlot, double[]> changes = new HashMap<>(2);
        for (LadderSlot slot : slots) {
            double was = before.of(slot);
            double now = reduce.applyAsDouble(was);
            if (now == was) {
                continue;
            }
            after = after.with(slot, now);
            changes.put(slot, new double[] {was, now});
        }
        if (changes.isEmpty()) {
            return;
        }

        live.put(characterId, after);
        repository.markDirty(characterId);

        WearCurve current = curve.get();
        for (Map.Entry<LadderSlot, double[]> change : changes.entrySet()) {
            double was = change.getValue()[0];
            double now = change.getValue()[1];
            events.publish(
                    new GearConditionChangedEvent(
                            characterId,
                            change.getKey(),
                            was,
                            now,
                            warningToTell(characterId, change.getKey(), current, was, now)));
        }
    }

    /**
     * Welche Warnschwelle jetzt zu melden ist — oder keine.
     *
     * <p>Zwei Bedingungen, und beide sind nötig: der Übergang muss die Schwelle unterschritten haben,
     * und die Ruhezeit dieser Schwelle muss abgelaufen sein. Ohne die zweite käme die Meldung bei
     * jedem Treffer, der die Schwelle streift.
     */
    private OptionalDouble warningToTell(
            UUID characterId, LadderSlot slot, WearCurve current, double before, double after) {
        OptionalDouble crossed = current.crossedWarning(before, after);
        if (crossed.isEmpty()) {
            return OptionalDouble.empty();
        }
        WarnKey key = new WarnKey(characterId, slot, crossed.getAsDouble());
        long now = clock.millis();
        Long last = lastWarned.get(key);
        if (last != null && now - last < current.warnCooldown().toMillis()) {
            return OptionalDouble.empty();
        }
        lastWarned.put(key, now);
        return crossed;
    }
}
