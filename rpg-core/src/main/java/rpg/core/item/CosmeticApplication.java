package rpg.core.item;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import rpg.core.classes.LadderSlot;
import rpg.core.event.EventBus;

/**
 * Besitz und Anwendung der Trimfarben (FR-067 bis FR-073).
 *
 * <p><b>Erst auf der Höchststufe anwendbar</b> (FR-069), und der Grund steht in B07s eigener
 * Anforderung: für Schurke und Krieger ist der Trim das <em>einzige</em> Unterscheidungsmerkmal ihrer
 * oberen Stufen (B07/FR-016). Eine frei anwendbare Farbe würde ihn überschreiben — Schurkenstufe 4
 * und 6 sähen gleich aus, und die Sichtbarkeit des Fortschritts, für die {@code TierAppearance}
 * überhaupt existiert, wäre weg. Auf der Höchststufe gibt es nichts mehr zu verwechseln.
 *
 * <p><b>Der Besitz bleibt trotzdem.</b> Eine Ablehnung nimmt nichts weg: wer auf Stufe 3 kauft, hat
 * die Farbe, und sie wartet. Das ist der Grind aus Q2 — sonst gäbe es auf Stufe 3 keinen Grund, in
 * den Bestand zu sehen.
 *
 * <p><b>Höchstens eine getragen</b> (FR-071). Eine zweite ersetzt die erste; ein Wechsel kostet
 * nichts und nimmt nichts. Die Datenbank erzwingt dieselbe Regel über einen Teilindex — hier steht
 * sie, damit der Spieler eine Antwort bekommt, dort, damit sie auch stimmt, wenn jemand anders
 * schreibt.
 *
 * <p><b>Kein einziger Wert ändert sich</b> (FR-070, SC-014). Diese Klasse berührt keine Attribute
 * und keine Stufe; sie kennt {@code StatEngine} nicht einmal. Das ist die Zusage, und sie ist hier
 * eine Eigenschaft der Bauart, nicht eine Behauptung.
 */
public final class CosmeticApplication {

    /** Was aus einem Versuch geworden ist. */
    public enum Outcome {
        /** Angewandt. */
        DONE,
        /** Diese Farbe gehört dem Charakter nicht. */
        NOT_OWNED,
        /** Noch nicht auf der Höchststufe — der Besitz bleibt (FR-069). */
        NOT_TOP_TIER,
        /** Die Vorlage ist unbekannt oder keine Kosmetik. */
        UNKNOWN_TEMPLATE,
        /** Sie wird bereits getragen. */
        ALREADY_APPLIED
    }

    /** Ob dieser Charakter in <b>beiden</b> Leitern die Höchststufe erreicht hat — B07s Antwort. */
    @FunctionalInterface
    public interface TopTierCheck {
        boolean isAtTop(UUID characterId, LadderSlot slot);
    }

    private final Supplier<ItemConfig> config;
    private final CosmeticRepository repository;
    private final TopTierCheck topTier;
    private final EventBus events;
    private final Clock clock;

    /** Was jeder anwesende Charakter besitzt, in Kaufreihenfolge. */
    private final Map<UUID, Map<String, CosmeticUnlock>> live = new ConcurrentHashMap<>();

    public CosmeticApplication(
            Supplier<ItemConfig> config,
            CosmeticRepository repository,
            TopTierCheck topTier,
            EventBus events,
            Clock clock) {
        this.config = Objects.requireNonNull(config, "config");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.topTier = Objects.requireNonNull(topTier, "topTier");
        this.events = Objects.requireNonNull(events, "events");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    // --- Sitzungsgrenzen -------------------------------------------------------------

    public void put(UUID characterId, List<CosmeticUnlock> unlocks) {
        Map<String, CosmeticUnlock> owned = new LinkedHashMap<>();
        for (CosmeticUnlock unlock : unlocks) {
            owned.put(unlock.templateKey(), unlock);
        }
        live.put(characterId, owned);
    }

    public void forget(UUID characterId) {
        live.remove(characterId);
    }

    // --- Lesen -----------------------------------------------------------------------

    /** Was dieser Charakter besitzt, in Kaufreihenfolge. */
    public List<CosmeticUnlock> ownedBy(UUID characterId) {
        Map<String, CosmeticUnlock> owned = live.get(characterId);
        return owned == null ? List.of() : List.copyOf(owned.values());
    }

    public boolean owns(UUID characterId, String templateKey) {
        Map<String, CosmeticUnlock> owned = live.get(characterId);
        return owned != null && owned.containsKey(templateKey);
    }

    /** Welche Farbe getragen wird, oder leer. */
    public Optional<CosmeticUnlock> appliedOf(UUID characterId) {
        return ownedBy(characterId).stream().filter(CosmeticUnlock::applied).findFirst();
    }

    /**
     * Wie die getragene Farbe aussieht — die Frage, die das Aussehen stellt.
     *
     * <p><b>Leer für eine Farbe, die die Konfiguration nicht mehr kennt</b> (FR-073). Der
     * Besitzvermerk bleibt bestehen; nur das Aussehen fällt auf die Stufe zurück. Die Zeile zu
     * löschen wäre Datenverlust für einen Betreiberfehler, und ein Startabbruch die Strafe für den
     * Betreiber statt für den Fehler.
     */
    public Optional<CosmeticAppearance> appearanceOf(UUID characterId) {
        return appliedOf(characterId)
                .map(CosmeticUnlock::templateKey)
                .flatMap(key -> config.get().template(key))
                .flatMap(ItemTemplate::appearanceOrNone);
    }

    // --- Ändern ----------------------------------------------------------------------

    /** Vermerkt einen Kauf. Der Händler bucht; hier wird nur eingetragen. */
    public void grant(UUID characterId, String templateKey) {
        Map<String, CosmeticUnlock> owned =
                live.computeIfAbsent(characterId, id -> new LinkedHashMap<>());
        if (owned.containsKey(templateKey)) {
            return;
        }
        owned.put(templateKey, CosmeticUnlock.acquired(characterId, templateKey, clock.instant()));
        repository.markDirty(characterId);
    }

    /**
     * Trägt eine Farbe.
     *
     * <p>Vier Prüfungen, und keine davon nimmt bei einer Ablehnung etwas weg: die Vorlage ist eine
     * Kosmetik, der Charakter besitzt sie, er ist auf der Höchststufe, und sie ist nicht schon
     * getragen.
     */
    public Outcome apply(UUID characterId, String templateKey) {
        Objects.requireNonNull(characterId, "characterId");
        Objects.requireNonNull(templateKey, "templateKey");

        Optional<ItemTemplate> template = config.get().template(templateKey);
        if (template.isEmpty() || template.get().category() != ItemCategory.COSMETIC) {
            return Outcome.UNKNOWN_TEMPLATE;
        }
        Map<String, CosmeticUnlock> owned = live.get(characterId);
        if (owned == null || !owned.containsKey(templateKey)) {
            return Outcome.NOT_OWNED;
        }
        if (!isAtTop(characterId)) {
            // Der Besitz bleibt. Genau das ist der Grind aus Q2 - wer auf Stufe 3 kauft, hat sie,
            // und sie wartet auf Stufe 60.
            return Outcome.NOT_TOP_TIER;
        }
        if (owned.get(templateKey).applied()) {
            return Outcome.ALREADY_APPLIED;
        }

        // Hoechstens eine (FR-071): die vorige wird abgelegt, nicht entfernt.
        replaceApplied(owned, templateKey);
        repository.markDirty(characterId);
        events.publish(
                new CosmeticAppliedEvent(
                        characterId,
                        Optional.of(templateKey),
                        template.get().appearanceOrNone()));
        return Outcome.DONE;
    }

    /** Legt die getragene Farbe ab — das Aussehen fällt auf die Stufe zurück. */
    public boolean clear(UUID characterId) {
        Map<String, CosmeticUnlock> owned = live.get(characterId);
        if (owned == null) {
            return false;
        }
        List<String> applied = new ArrayList<>();
        owned.forEach(
                (key, unlock) -> {
                    if (unlock.applied()) {
                        applied.add(key);
                    }
                });
        if (applied.isEmpty()) {
            return false;
        }
        applied.forEach(key -> owned.put(key, owned.get(key).withApplied(false)));
        repository.markDirty(characterId);
        events.publish(CosmeticAppliedEvent.cleared(characterId));
        return true;
    }

    /**
     * Beide Leitern auf der Höchststufe.
     *
     * <p><b>Beide, nicht eine.</b> Die Farbe überschreibt den Rüstungstrim; wer die Waffenleiter noch
     * vor sich hat, ist nicht am Ende seines Fortschritts, und der Trim hätte dort noch etwas zu
     * sagen.
     */
    private boolean isAtTop(UUID characterId) {
        for (LadderSlot slot : LadderSlot.values()) {
            if (!topTier.isAtTop(characterId, slot)) {
                return false;
            }
        }
        return true;
    }

    private void replaceApplied(Map<String, CosmeticUnlock> owned, String templateKey) {
        owned.replaceAll((key, unlock) -> unlock.withApplied(key.equals(templateKey)));
    }
}
