package rpg.platform.ui;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.entity.Player;

import rpg.core.ability.Ability;
import rpg.core.ability.AbilityRegistry;
import rpg.core.scheduler.EntityRef;
import rpg.core.scheduler.Scheduler;
import rpg.core.ui.MaterialUniqueness;

/**
 * Der laufende Cooldown als <b>Vanilla-Overlay</b> auf dem Fähigkeits-Item (FR-030 bis FR-033).
 *
 * <h2>Die graue Sweep-Animation, die jeder von der Enderperle kennt</h2>
 *
 * <p>Keine eigene Fläche, keine Zahl, keine Textzeile. {@code Player.setCooldown} färbt das Item im
 * Slot ein und lässt eine Uhr darüber laufen — die Geste ist bekannt, kostet keinen Platz und
 * braucht kein Resource Pack (ADR-005).
 *
 * <h2>Die Restzeit wird nicht zweitgerechnet</h2>
 *
 * <p>Sie kommt aus {@code AbilityRegistry.remainingCooldown} (FR-031) — zeitstempelbasiert auf
 * Anfrage, dieselbe Methode, die {@code AbilityRuntime} selbst benutzt. Eine zweite Rechnung hier
 * wäre eine zweite Wahrheit darüber, wann eine Fähigkeit bereit ist, und sie ginge auseinander,
 * sobald B08 seine Formel ändert.
 *
 * <h2>Nach einer Wiederanmeldung steht die VERBLEIBENDE Zeit da</h2>
 *
 * <p>FR-033. Nicht die volle und nicht keine: B08 führt den Cooldown über Zeitstempel, also
 * überlebt er die Abmeldung von selbst — was fehlt, ist nur die Anzeige. Diese Klasse stellt sie
 * beim Betreten wieder her, für <b>jede</b> belegte Fähigkeit.
 *
 * <p><b>Ohne das wäre der Fehler unauffällig und ärgerlich</b>: der Spieler sähe ein bereites Item,
 * drückte es, und nichts geschähe — B08 lehnt ab, weil der Cooldown in Wahrheit noch läuft.
 *
 * <h2>Das Overlay gilt je MATERIAL, nicht je Slot</h2>
 *
 * <p>Die Grenze der Technik, und der Grund für die Startprüfung aus {@link MaterialUniqueness}:
 * zwei Fähigkeiten derselben Klasse auf demselben Item teilten sich eine Anzeige. Der Start bricht
 * deshalb ab, bevor jemand spielt.
 *
 * <h2>{@code AbilityHotbar} wird dabei nicht angefasst</h2>
 *
 * <p>FR-024. Das Overlay setzt auf dem Material auf, das B08 dort <em>bereits</em> gelegt hat — es
 * fragt nicht, wo, und es ändert nichts daran. Ein Umbau an fremdem funktionierendem Code wäre der
 * teuerste Weg zu keinem sichtbaren Unterschied.
 */
public final class AbilityCooldownOverlay {

    private final Server server;
    private final Scheduler scheduler;
    private final AbilityRegistry abilities;
    private final Function<UUID, Optional<UUID>> characterOfPlayer;

    public AbilityCooldownOverlay(
            Server server,
            Scheduler scheduler,
            AbilityRegistry abilities,
            Function<UUID, Optional<UUID>> characterOfPlayer) {
        this.server = Objects.requireNonNull(server, "server");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.abilities = Objects.requireNonNull(abilities, "abilities");
        this.characterOfPlayer = Objects.requireNonNull(characterOfPlayer, "characterOfPlayer");
    }

    /**
     * Legt das Overlay über alle Fähigkeiten dieses Spielers, die gerade abkühlen.
     *
     * <p>Gerufen beim Anmelden (FR-033) und nach jedem Auslösen. <b>Beides über denselben Weg</b>:
     * die Restzeit ist in beiden Fällen dieselbe Frage, und zwei Wege dorthin wären zwei
     * Gelegenheiten, sie verschieden zu beantworten.
     */
    public void restore(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        Optional<UUID> characterId = characterOfPlayer.apply(playerId);
        if (characterId.isEmpty()) {
            return;
        }
        Optional<rpg.core.session.CharacterClass> characterClass =
                abilities.classOf(characterId.get());
        if (characterClass.isEmpty()) {
            return;
        }

        for (Ability ability : abilities.abilitiesOf(characterClass.get())) {
            Optional<Duration> remaining =
                    abilities.remainingCooldown(characterId.get(), ability.id());
            if (remaining.isEmpty() || remaining.get().isNegative() || remaining.get().isZero()) {
                continue;
            }
            apply(playerId, ability, remaining.get());
        }
    }

    /**
     * Legt das Overlay über eine einzelne Fähigkeit.
     *
     * <p>Über <b>alle</b> ihre Materialien: eine passive kann mehrere Slots belegen, und eines davon
     * grau zu lassen sähe aus, als hätte nur die Hälfte ausgelöst.
     */
    public void apply(UUID playerId, Ability ability, Duration remaining) {
        List<String> materials = MaterialUniqueness.materialsOf(ability);
        if (materials.isEmpty()) {
            return;
        }
        // Ticks, nicht Millisekunden: Papers Cooldown zaehlt in Ticks, und eine Rundung nach unten
        // liesse das Item eine Zwanzigstelsekunde zu frueh frei - sichtbar als ein Klick, der noch
        // abgelehnt wird.
        int ticks = (int) Math.max(1, Math.ceil(remaining.toMillis() / 50.0));

        scheduler.runSyncOnEntity(
                new EntityRef(playerId),
                () -> {
                    Player player = server.getPlayer(playerId);
                    if (player == null) {
                        return;
                    }
                    for (String name : materials) {
                        Material material = Material.matchMaterial(name);
                        if (material == null) {
                            // Beim Start bereits abgefangen; hier still uebergehen statt den
                            // Aufrufer mitzureissen.
                            continue;
                        }
                        player.setCooldown(material, ticks);
                    }
                });
    }
}
