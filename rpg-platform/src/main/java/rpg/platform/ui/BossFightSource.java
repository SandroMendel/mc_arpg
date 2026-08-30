package rpg.platform.ui;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import rpg.core.combat.DamageDealtEvent;
import rpg.core.event.EventBus;
import rpg.core.message.MessageKey;
import rpg.core.mob.MobKind;
import rpg.core.mob.MobKinds;
import rpg.core.ui.BossBarOccasion;
import rpg.core.ui.UiMessageKeys;
import rpg.platform.hud.CombatStatusSource;

/**
 * Das Leben eines Bosses, während man ihn schlägt (FR-003).
 *
 * <h2>Es braucht keine neue Abfrage</h2>
 *
 * <p>Drei vorhandene Nähte reichen (research.md R3):
 *
 * <ol>
 *   <li>{@link DamageDealtEvent#targetId()} sagt, <em>wen</em> der Spieler schlägt
 *   <li>{@link MobKinds#ofEntity} sagt, ob es ein Boss ist
 *   <li>{@link CombatStatusSource#statusOf} liefert den Füllstand — <b>für eine Kreatur genauso wie
 *       für einen Spieler</b>, weil beide durch dieselbe Engine gehen
 * </ol>
 *
 * <p>Der dritte Punkt ist der, den man leicht übersieht: {@code CombatStatusSource} sieht nach einer
 * Spielerschnittstelle aus, ist aber keine. B13 hätte hier sonst eine zweite Lebensabfrage gebaut —
 * und zwei Antworten auf dieselbe Frage driften auseinander.
 *
 * <h2>Warum der Kampf abläuft statt zu enden</h2>
 *
 * <p>Es gibt kein „Bosskampf vorbei"-Ereignis. Der Balken bleibt deshalb, solange der Spieler
 * <em>weiter zuschlägt</em>, und läuft danach ab — dieselbe Zeitstempelrechnung wie beim Zonennamen
 * und aus demselben Grund: keine Aufgabe je Spieler (Constitution II.2).
 *
 * <p><b>Ein toter Boss verschwindet von selbst</b>, weil {@code statusOf} für ihn dann leer
 * antwortet. Das ist kein Zufall, sondern der Grund, aus dem der Füllstand bei jedem Takt neu
 * gelesen und nicht gemerkt wird.
 *
 * <h2>Er kehrt nach einer Verdrängung zurück</h2>
 *
 * <p>Ohne dass ihn jemand neu meldet — sein <b>Zustand</b> besteht fort, das Ereignis war nur
 * verdeckt (FR-004). Genau deshalb braucht {@code BossBarPriority} keine Warteschlange.
 */
public final class BossFightSource implements HudRefresh.BossBarSource {

    /**
     * Wie lange der Balken nach dem letzten Treffer noch steht.
     *
     * <p>Im Code und nicht in {@code ui.yml}: es ist keine Zahl, an der ein Betreiber drehen will,
     * sondern die Antwort auf „wann ist ein Kampf vorbei", und die gehört zu B05s Kampfbegriff.
     * Fünf Sekunden sind länger als jede Angriffsfolge und kürzer als ein Ortswechsel.
     */
    private static final Duration LINGER = Duration.ofSeconds(5);

    private final MobKinds mobKinds;
    private final CombatStatusSource status;
    private final Clock clock;

    private final Map<UUID, Fight> fights = new ConcurrentHashMap<>();

    private record Fight(UUID bossId, MessageKey nameKey, String name, Instant until) {}

    public BossFightSource(MobKinds mobKinds, CombatStatusSource status, Clock clock) {
        this.mobKinds = Objects.requireNonNull(mobKinds, "mobKinds");
        this.status = Objects.requireNonNull(status, "status");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Hört auf zugefügten Schaden. */
    public void subscribeTo(EventBus eventBus) {
        Objects.requireNonNull(eventBus, "eventBus");
        eventBus.subscribe(DamageDealtEvent.class, this::onDamageDealt);
    }

    private void onDamageDealt(DamageDealtEvent event) {
        Optional<UUID> attacker = event.attacker();
        if (attacker.isEmpty()) {
            return;
        }
        Optional<MobKind> kind = mobKinds.ofEntity(event.targetId());
        if (kind.isEmpty() || !kind.get().boss()) {
            // Kein Boss. Der weit haeufigere Fall, und er kostet genau eine Abfrage - deshalb steht
            // sie vor allem anderen.
            return;
        }
        fights.put(
                attacker.get(),
                new Fight(
                        event.targetId(),
                        UiMessageKeys.BOSSBAR_BOSS_FIGHT,
                        kind.get().key(),
                        clock.instant().plus(LINGER)));
    }

    @Override
    public Optional<HudRefresh.BossBarContent> contentFor(UUID playerId) {
        Fight fight = fights.get(playerId);
        if (fight == null) {
            return Optional.empty();
        }
        if (!clock.instant().isBefore(fight.until())) {
            fights.remove(playerId);
            return Optional.empty();
        }
        // Der Fuellstand wird bei JEDEM Takt neu gelesen und nirgends gemerkt. Deshalb verschwindet
        // ein toter Boss von selbst: statusOf antwortet dann leer.
        Optional<CombatStatusSource.Status> current = status.statusOf(fight.bossId());
        if (current.isEmpty()) {
            fights.remove(playerId);
            return Optional.empty();
        }
        return Optional.of(
                new HudRefresh.BossBarContent(
                        BossBarOccasion.BOSS_FIGHT,
                        fight.nameKey(),
                        Map.of("name", fight.name()),
                        Math.clamp(current.get().fraction(), 0.0, 1.0)));
    }

    /** Vergisst diesen Spieler — beim Abmelden (FR-004c). */
    public void forget(UUID playerId) {
        fights.remove(playerId);
    }

    /** Wie viele Kämpfe gerade laufen — für den Test gegen ein Leck. */
    int tracked() {
        return fights.size();
    }
}
