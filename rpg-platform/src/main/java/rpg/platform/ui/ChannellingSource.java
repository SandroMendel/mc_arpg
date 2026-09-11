package rpg.platform.ui;

import java.time.Clock;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import rpg.core.ability.AbilityRegistry;
import rpg.core.ability.AbilityRuntime;
import rpg.core.ability.RunningAbility;
import rpg.core.ui.BarProgress;
import rpg.core.ui.BossBarOccasion;
import rpg.core.ui.UiMessageKeys;

/**
 * Der Fortschritt einer kanalisierten Fähigkeit (FR-003).
 *
 * <h2>Er ist eine Rechnung, keine Aufgabe</h2>
 *
 * <p>{@link RunningAbility} führt {@code startedAt} und {@code dueAt} — daraus folgt der Füllstand
 * zu jedem Zeitpunkt. Es gibt <b>nichts einzuplanen</b> und nichts zu abonnieren: der Takt fragt
 * ohnehin jede Sekunde, und {@link BarProgress} rechnet gegen die Uhr (Constitution II.2,
 * research.md R4).
 *
 * <p>Diese Klasse hat deshalb als einzige der drei Anlassquellen <b>keinen eigenen Zustand</b>. Sie
 * liest, sie merkt nichts, und sie kann folglich nichts lecken.
 *
 * <h2>Sie ist der einzige Anlass, der andere verdrängen darf</h2>
 *
 * <p>Rang 1 (FR-004): eine Kanalisierung dauert Sekunden und hält den Spieler <em>gerade fest</em> —
 * ohne Balken weiß er nicht, wie lange er noch stillstehen muss. Ein Bosskampf dauert Minuten und
 * kommt danach von selbst zurück.
 *
 * <h2>Nur {@code WINDING_UP} zeigt einen Balken</h2>
 *
 * <p>{@code RUNNING} ist die Phase, in der die Fähigkeit bereits <em>wirkt</em>. Ein Balken dort
 * wäre die Anzeige einer Wartezeit, die es nicht mehr gibt — der Spieler kann sich längst wieder
 * bewegen.
 *
 * <h2>Charakter herein, Spieler heraus</h2>
 *
 * <p>{@code AbilityRuntime.running} nimmt die <b>Charakter</b>kennung; die Anzeige hängt am
 * <b>Spieler</b>. Die Übersetzung passiert an dieser einen Stelle. Mit der falschen Kennung
 * antwortet die Naht nicht mit einem Fehler, sondern mit einem leeren {@code Optional} — und das
 * sieht aus wie „kanalisiert gerade nicht". Dieselbe Falle, die B08 und B11 je einmal getroffen hat.
 */
public final class ChannellingSource implements HudRefresh.BossBarSource {

    private final AbilityRuntime runtime;
    private final AbilityRegistry abilities;
    private final Function<UUID, Optional<UUID>> characterOfPlayer;
    private final Clock clock;

    /**
     * @param characterOfPlayer übersetzt die Spielerkennung in die Kennung des aktiven Charakters
     */
    public ChannellingSource(
            AbilityRuntime runtime,
            AbilityRegistry abilities,
            Function<UUID, Optional<UUID>> characterOfPlayer,
            Clock clock) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.abilities = Objects.requireNonNull(abilities, "abilities");
        this.characterOfPlayer = Objects.requireNonNull(characterOfPlayer, "characterOfPlayer");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public Optional<HudRefresh.BossBarContent> contentFor(UUID playerId) {
        Optional<UUID> characterId = characterOfPlayer.apply(playerId);
        if (characterId.isEmpty()) {
            return Optional.empty();
        }
        Optional<RunningAbility> running = runtime.running(characterId.get());
        if (running.isEmpty()) {
            return Optional.empty();
        }
        RunningAbility current = running.get();
        if (current.phase() != RunningAbility.Phase.WINDING_UP) {
            // RUNNING heisst: sie wirkt bereits. Ein Balken waere die Anzeige einer Wartezeit, die
            // es nicht mehr gibt.
            return Optional.empty();
        }
        BarProgress progress = new BarProgress(current.startedAt(), current.dueAt());
        double fraction = progress.fraction(clock.instant());
        if (fraction >= 1.0) {
            // Durchgelaufen, aber der Zustand ist noch nicht aufgeraeumt. Kein Balken mehr: ein
            // voller Balken, der stehen bleibt, sieht aus wie ein haengender Server.
            return Optional.empty();
        }
        return Optional.of(
                new HudRefresh.BossBarContent(
                        BossBarOccasion.CHANNELLING,
                        UiMessageKeys.BOSSBAR_CHANNELLING,
                        Map.of("ability", nameOf(current.abilityId())),
                        fraction));
    }

    /**
     * Der Anzeigename der Fähigkeit.
     *
     * <p>Fällt auf die Kennung zurück, wenn B08 sie nicht kennt — sichtbar, aber nicht kaputt. Eine
     * Ausnahme hier wäre ein Fehler in einem Pfad, der jede Sekunde läuft.
     */
    private String nameOf(String abilityId) {
        return abilities.find(abilityId).map(ability -> ability.id()).orElse(abilityId);
    }
}
