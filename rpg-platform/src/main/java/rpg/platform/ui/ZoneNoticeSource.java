package rpg.platform.ui;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;

import rpg.core.event.EventBus;
import rpg.core.ui.BossBarOccasion;
import rpg.core.ui.UiConfig;
import rpg.core.ui.UiMessageKeys;
import rpg.core.zone.ZoneChangedEvent;

/**
 * Der Zonenname beim Betreten (FR-003).
 *
 * <h2>Er läuft ab, statt entfernt zu werden</h2>
 *
 * <p>Ein Zonenhinweis steht {@code zoneNoticeDuration} lang und verschwindet dann. Naheliegend wäre
 * eine eingeplante Aufgabe, die ihn wegnimmt — <b>und genau die verbietet Constitution II.2</b>: bei
 * 200 Spielern, die durch Zonen laufen, wären das laufend Aufgaben je Spieler.
 *
 * <p>Stattdessen wird ein <b>Ablaufzeitpunkt</b> gemerkt, und {@link #contentFor} vergleicht ihn mit
 * der Uhr. Zeitstempelbasiert lazy, wie jede andere Zeitrechnung dieses Blocks. Der Takt fragt
 * ohnehin jede Sekunde nach; ein Hinweis verschwindet damit höchstens eine Sekunde zu spät, und das
 * ist bei vier Sekunden Standzeit nicht wahrnehmbar.
 *
 * <h2>Der einzige Anlass ohne Zustand</h2>
 *
 * <p>{@code BOSS_FIGHT} und {@code CHANNELLING} kehren nach einer Verdrängung von selbst zurück,
 * weil ihr Zustand fortbesteht. Ein Zonenname nicht — er ist ein Ereignis, das vorbei ist, und wird
 * <b>nicht nachgeholt</b> (FR-004a). Was hier gemerkt wird, ist deshalb kein Anlass in einer
 * Warteschlange, sondern nur die Frage „läuft er noch".
 *
 * <h2>Er kommt über die Charakterkennung herein und über die Spielerkennung heraus</h2>
 *
 * <p>{@link ZoneChangedEvent} trägt die <b>Charakter</b>kennung; die Anzeige hängt am <b>Spieler</b>.
 * Die Übersetzung passiert an dieser einen Stelle. Wer sie vergisst, bekommt keinen
 * Übersetzungsfehler, sondern eine Bossbar, die nie erscheint — dieselbe Falle, die B08 und B11 je
 * einmal getroffen hat.
 */
public final class ZoneNoticeSource implements HudRefresh.BossBarSource {

    private final Supplier<UiConfig> config;
    private final Function<UUID, Optional<UUID>> playerOfCharacter;
    private final Clock clock;

    /** Bis wann der Hinweis dieses Spielers steht, und wie die Zone heißt. */
    private final Map<UUID, Notice> notices = new ConcurrentHashMap<>();

    private record Notice(String zoneName, Instant until) {}

    /**
     * @param playerOfCharacter übersetzt die Charakterkennung des Ereignisses in die Spielerkennung
     */
    public ZoneNoticeSource(
            Supplier<UiConfig> config,
            Function<UUID, Optional<UUID>> playerOfCharacter,
            Clock clock) {
        this.config = Objects.requireNonNull(config, "config");
        this.playerOfCharacter = Objects.requireNonNull(playerOfCharacter, "playerOfCharacter");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Hört auf den Zonenwechsel. */
    public void subscribeTo(EventBus eventBus) {
        Objects.requireNonNull(eventBus, "eventBus");
        eventBus.subscribe(ZoneChangedEvent.class, this::onZoneChanged);
    }

    private void onZoneChanged(ZoneChangedEvent event) {
        if (event.to().isEmpty()) {
            // Die Wildnis bekommt keinen Hinweis: "du bist jetzt nirgendwo" ist keine Nachricht.
            // Der laufende Hinweis bleibt stehen und laeuft von selbst ab.
            return;
        }
        Optional<UUID> playerId = playerOfCharacter.apply(event.characterId());
        if (playerId.isEmpty()) {
            return;
        }
        Duration standing = config.get().zoneNoticeDuration();
        notices.put(
                playerId.get(), new Notice(event.to().get(), clock.instant().plus(standing)));
    }

    @Override
    public Optional<HudRefresh.BossBarContent> contentFor(UUID playerId) {
        Notice notice = notices.get(playerId);
        if (notice == null) {
            return Optional.empty();
        }
        if (!clock.instant().isBefore(notice.until())) {
            // Abgelaufen. Hier entfernt statt von einer eingeplanten Aufgabe - keine Aufgabe je
            // Spieler (Constitution II.2).
            notices.remove(playerId);
            return Optional.empty();
        }
        return Optional.of(
                new HudRefresh.BossBarContent(
                        BossBarOccasion.ZONE_NAME,
                        UiMessageKeys.BOSSBAR_ZONE_NOTICE,
                        Map.of("zone", notice.zoneName()),
                        1.0));
    }

    /** Vergisst diesen Spieler — beim Abmelden (FR-004c). */
    public void forget(UUID playerId) {
        notices.remove(playerId);
    }

    /** Wie viele Hinweise gerade stehen — für den Test gegen ein Leck. */
    int tracked() {
        return notices.size();
    }
}
