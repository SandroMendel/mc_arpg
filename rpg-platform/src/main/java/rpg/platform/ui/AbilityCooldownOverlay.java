package rpg.platform.ui;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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
    private final java.time.Clock clock;

    /**
     * Welchen Cooldown-Endzeitpunkt wir je Spieler und Fähigkeit zuletzt gesetzt haben.
     *
     * <p>Die Grundlage dafür, dass der Sekundenabgleich <b>nur bei Änderung</b> sendet.
     */
    private final Map<UUID, Map<String, Instant>> lastApplied = new ConcurrentHashMap<>();

    public AbilityCooldownOverlay(
            Server server,
            Scheduler scheduler,
            AbilityRegistry abilities,
            Function<UUID, Optional<UUID>> characterOfPlayer,
            java.time.Clock clock) {
        this.server = Objects.requireNonNull(server, "server");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.abilities = Objects.requireNonNull(abilities, "abilities");
        this.characterOfPlayer = Objects.requireNonNull(characterOfPlayer, "characterOfPlayer");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Legt das Overlay über alle Fähigkeiten dieses Spielers, die gerade abkühlen.
     *
     * <p>Gerufen beim Anmelden (FR-033) und nach jedem Auslösen. <b>Beides über denselben Weg</b>:
     * die Restzeit ist in beiden Fällen dieselbe Frage, und zwei Wege dorthin wären zwei
     * Gelegenheiten, sie verschieden zu beantworten.
     */
    public void restore(UUID playerId) {
        refresh(playerId);
    }

    /**
     * Gleicht das Overlay mit dem <b>tatsächlichen</b> Cooldown ab — einmal je Sekunde.
     *
     * <h2>Warum es das braucht, obwohl der Auslösepfad verdrahtet ist</h2>
     *
     * <p><b>Nicht jeder Cooldown beginnt beim Auslösen.</b> Eine <em>anhaltende</em> Fähigkeit
     * ({@code sustained: true}) startet ihren, wenn sie <b>endet</b> — beim Krieger sind das
     * {@code shield}, {@code whirl} und {@code call-of-the-berserker}, beim Schurken
     * {@code invisibility}. Eine Fähigkeit mit <em>Ladungen</em> startet ihn erst, wenn die letzte
     * verbraucht ist ({@code rogue.teleport}, zwei Ladungen).
     *
     * <p>Der erste Entwurf hing nur am Auslösepfad. Im Spiel hieß das: beim Krieger wurde
     * <b>ausschließlich Leap</b> grau — die einzige seiner aktiven Fähigkeiten ohne
     * {@code sustained}. Ein Fehler, der bei einer von vier funktioniert.
     *
     * <p><b>Beide Wege, nicht einer:</b> der Auslösepfad macht es <em>sofort</em> (eine Sekunde
     * Verzug ist genau die Sekunde, in der ein Spieler ein zweites Mal drückt), dieser Abgleich
     * macht es <em>vollständig</em> — er greift für jeden Cooldown, gleich woher er kommt.
     *
     * <h2>Er sendet nur bei Änderung</h2>
     *
     * <p>{@code setCooldown} ist ein Paket. Bei 200 Spielern mal sieben Fähigkeiten je Sekunde
     * wären das 1400 — für eine Anzeige, die sich meist nicht ändert. Gemerkt wird deshalb der
     * <b>Endzeitpunkt</b>, den wir zuletzt gesetzt haben; ein unveränderter kostet nichts.
     */
    public void refresh(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        Optional<UUID> characterId = characterOfPlayer.apply(playerId);
        if (characterId.isEmpty()) {
            return;
        }
        Map<String, Instant> applied =
                lastApplied.computeIfAbsent(playerId, id -> new ConcurrentHashMap<>());

        refreshConsumables(playerId, characterId.get(), applied);

        Optional<rpg.core.session.CharacterClass> characterClass =
                abilities.classOf(characterId.get());
        if (characterClass.isEmpty()) {
            return;
        }

        for (Ability ability : abilities.abilitiesOf(characterClass.get())) {
            Optional<Duration> remaining =
                    abilities.remainingCooldown(characterId.get(), ability.id());
            if (remaining.isEmpty() || remaining.get().isNegative() || remaining.get().isZero()) {
                // Kein Cooldown mehr. Den Merker wegnehmen, damit der naechste wieder als neu
                // erkannt wird - sonst bliebe eine zweite Ausloesung mit gleicher Dauer stumm.
                applied.remove(ability.id());
                continue;
            }
            Instant until = clock.instant().plus(remaining.get());
            Instant previous = applied.get(ability.id());
            // Eine Sekunde Toleranz: der Takt laeuft sekundenweise, und ein Endzeitpunkt, der sich
            // nur um Millisekunden unterscheidet, ist derselbe Cooldown und kein neuer.
            if (previous != null && Duration.between(previous, until).abs().toMillis() < 1000) {
                continue;
            }
            applied.put(ability.id(), until);
            apply(playerId, ability, remaining.get());
        }
    }

    /** Vergisst diesen Spieler — beim Abmelden. */
    public void forget(UUID playerId) {
        lastApplied.remove(playerId);
    }

    /**
     * Woher die Abklingzeiten <b>verbrauchbarer</b> Gegenstände kommen (B11).
     *
     * <p>Was gerade abkühlt, als Vorlagenschlüssel und Restzeit — und zu welchem Material eine
     * Vorlage gehört.
     */
    public interface ConsumableCooldowns {

        /** Die Vorlagen, die für diesen Charakter gerade abkühlen, mit ihrer Restzeit. */
        Map<String, Duration> remainingFor(UUID characterId);

        /** Das Vanilla-Material einer Vorlage. */
        Optional<String> materialOf(String templateKey);
    }

    /**
     * Solange niemand {@link #alsoShow} gerufen hat, kühlt kein Trank.
     *
     * <p>Ein leerer Vorgabewert statt {@code null}: die Fähigkeitsanzeige ist das ältere und
     * wichtigere Stück, und sie darf nicht davon abhängen, dass B11 überhaupt verdrahtet ist.
     */
    private ConsumableCooldowns consumables =
            new ConsumableCooldowns() {

                @Override
                public Map<String, Duration> remainingFor(UUID characterId) {
                    return Map.of();
                }

                @Override
                public Optional<String> materialOf(String templateKey) {
                    return Optional.empty();
                }
            };

    /**
     * Nimmt zusätzlich die Abklingzeiten von Tränken auf.
     *
     * <h2>Das ist eine Erweiterung von FR-030, keine Fehlerbehebung</h2>
     *
     * <p>FR-030 spricht vom „<b>Fähigkeits</b>-Item"; ein Trank ist keine Fähigkeit, und B11 setzt
     * für ihn selbst kein Vanilla-Overlay. Aus Spielersicht ist es trotzdem dieselbe Frage: das Ding
     * hat eine Abklingzeit und wird nicht grau. <b>Auf ausdrücklichen Wunsch am 2026-08-30
     * aufgenommen</b>, nachdem der Manatrank des Magiers beim Testspiel auffiel.
     *
     * <h2>Über das Material, nicht über das Inventar</h2>
     *
     * <p>Das Vanilla-Overlay hängt am <b>Material</b>, nicht am Slot — es muss also niemand
     * nachsehen, ob der Spieler den Trank überhaupt dabei hat. Das spart 36 Inventarplätze mal 200
     * Spieler je Sekunde, und vor allem spart es einen Bukkit-Zugriff aus einem asynchronen Takt
     * (Constitution I.1).
     *
     * <p>Wer den Trank nicht trägt, sieht davon nichts: ein Cooldown auf einem Material, das nirgends
     * liegt, zeigt sich auf keinem Slot.
     */
    public void alsoShow(ConsumableCooldowns consumables) {
        this.consumables = Objects.requireNonNull(consumables, "consumables");
    }

    /**
     * Der Abgleich für die Tränke — dieselbe Regel, dieselbe Nur-bei-Änderung-Bremse.
     *
     * <p>Bewusst <b>vor</b> der Klassenabfrage und außerhalb von ihr: ein Trank gehört dem
     * Charakter, nicht seiner Klasse. Stünde das im Fähigkeitszweig, verlöre jeder ohne gewählte
     * Klasse seine Trank-Anzeige — und niemand fände heraus, warum.
     *
     * <p>Die Schlüssel bekommen ein {@code item:} vorweg. Ein Vorlagenschlüssel und eine
     * Fähigkeits-ID teilen sich denselben Merkspeicher, und {@code potion.mana} als Fähigkeitsname
     * ist nicht verboten — die Verwechslung wäre lautlos und selten.
     */
    private void refreshConsumables(UUID playerId, UUID characterId, Map<String, Instant> applied) {
        for (Map.Entry<String, Duration> cooling :
                consumables.remainingFor(characterId).entrySet()) {
            Duration remaining = cooling.getValue();
            String key = "item:" + cooling.getKey();
            if (remaining == null || remaining.isNegative() || remaining.isZero()) {
                applied.remove(key);
                continue;
            }
            Optional<String> material = consumables.materialOf(cooling.getKey());
            if (material.isEmpty()) {
                continue;
            }
            Instant until = clock.instant().plus(remaining);
            Instant previous = applied.get(key);
            if (previous != null && Duration.between(previous, until).abs().toMillis() < 1000) {
                continue;
            }
            applied.put(key, until);
            applyTo(playerId, List.of(material.get()), remaining);
        }
    }

    /**
     * Legt das Overlay über eine einzelne Fähigkeit.
     *
     * <p>Über <b>alle</b> ihre Materialien: eine passive kann mehrere Slots belegen, und eines davon
     * grau zu lassen sähe aus, als hätte nur die Hälfte ausgelöst.
     */
    public void apply(UUID playerId, Ability ability, Duration remaining) {
        applyTo(playerId, MaterialUniqueness.materialsOf(ability), remaining);
    }

    /**
     * Setzt das Overlay auf diese Materialien.
     *
     * <p>Der gemeinsame Boden von Fähigkeit und Trank. Beide rechnen dieselbe Restzeit in dieselben
     * Ticks um und hüpfen über denselben Weg in den Takt — zwei Fassungen davon wären zwei
     * Gelegenheiten, verschieden zu runden.
     */
    private void applyTo(UUID playerId, List<String> materials, Duration remaining) {
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
