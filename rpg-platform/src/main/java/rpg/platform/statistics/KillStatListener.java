package rpg.platform.statistics;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.DoubleSupplier;

import rpg.core.combat.CombatDeathEvent;
import rpg.core.combat.DeathCause;
import rpg.core.event.EventBus;
import rpg.core.mob.MobKind;
import rpg.core.mob.MobKinds;
import rpg.core.statistics.AccountLookup;
import rpg.core.statistics.KillCredit;
import rpg.core.statistics.MetricRegistry;
import rpg.core.statistics.Statistics;

/**
 * Kills und Tode — der eine Zuhörer, an dem beide hängen (FR-006, FR-008, FR-010, FR-011).
 *
 * <p>Beide entstehen aus <b>demselben</b> {@link CombatDeathEvent}; welcher Fall vorliegt,
 * entscheidet {@code playerVictim}. Zwei Zuhörer wären die naheliegende Aufteilung und hätten
 * bedeutet, dass zwei Stellen dieselbe Auflösung der Art vornehmen — und irgendwann verschieden.
 *
 * <h2>Die Art wird IM TICK aufgelöst (FR-012, R4)</h2>
 *
 * <p>{@link MobKinds#ofEntity(UUID)} antwortet aus B10s Bestand, und dieser Bestand verliert seinen
 * Eintrag, sobald die Entität verschwindet. Ein asynchrones Nachschlagen — der Reflex, um den Tick
 * zu entlasten — ginge deshalb ins Leere: bis der andere Thread fragt, ist die Kreatur fort, und
 * jeder Kill landete unter dem Ersatzschlüssel. Das Ergebnis wäre eine Rangliste voller
 * „unbekannt", ohne dass irgendwo ein Fehler stünde.
 *
 * <p>Die Auflösung ist ein Zugriff auf eine Map. Das ist der ganze Preis, und er wird hier
 * bezahlt.
 *
 * <h2>Was hier nicht entschieden wird</h2>
 *
 * <p>Ob es ein <b>Bosskill</b> war. Nach FR-009 gibt es dafür bewusst keinen zweiten Zähler; die
 * Trennung passiert in der Aggregation über {@code MobKind.boss()}. Dieser Zuhörer schreibt für
 * jede Art denselben Schlüssel — und deshalb wandern die Kills einer Art, die ihr Kennzeichen
 * verliert, rückwirkend in die andere Rangliste, statt für immer falsch einsortiert zu bleiben.
 */
public final class KillStatListener {

    /**
     * Wer beim Kill in Reichweite in der Gruppe stand.
     *
     * <p>Die Reichweite ist Plattformwissen — sie braucht Positionen. Sie steckt deshalb in einer
     * eigenen Naht statt in {@link KillCredit}, und sie ist <b>dieselbe</b> wie bei Erfahrung und
     * Coins (FR-007b): {@code party.range-blocks} aus {@code progression.yml}. Eine eigene Zahl
     * wäre ein zweiter Begriff von „dabei gewesen", und Spieler fänden ihn, sobald XP und Kill
     * einmal auseinanderliefen.
     */
    @FunctionalInterface
    public interface PartyInRange {

        /** Gruppenmitglieder der Beitragenden, die nahe genug am Opfer standen. */
        Set<UUID> around(UUID victimId, Collection<UUID> contributors);
    }

    private final Statistics statistics;
    private final MobKinds kinds;
    private final AccountLookup accounts;
    private final PartyInRange partyInRange;
    private final DoubleSupplier threshold;

    public KillStatListener(
            Statistics statistics,
            MobKinds kinds,
            AccountLookup accounts,
            PartyInRange partyInRange,
            DoubleSupplier threshold) {
        this.statistics = Objects.requireNonNull(statistics, "statistics");
        this.kinds = Objects.requireNonNull(kinds, "kinds");
        this.accounts = Objects.requireNonNull(accounts, "accounts");
        this.partyInRange = Objects.requireNonNull(partyInRange, "partyInRange");
        this.threshold = Objects.requireNonNull(threshold, "threshold");
    }

    public void subscribeTo(EventBus events) {
        Objects.requireNonNull(events, "events").subscribe(CombatDeathEvent.class, this::onDeath);
    }

    void onDeath(CombatDeathEvent event) {
        if (event.playerVictim()) {
            countDeath(event);
        } else {
            countKill(event);
        }
    }

    /** Eine Kreatur ist gestorben: jedem Berechtigten einen Kill unter ihrer Art (FR-006). */
    private void countKill(CombatDeathEvent event) {
        // Zuerst aufloesen, dann rechnen: die Entitaet existiert JETZT noch.
        Optional<MobKind> kind = kinds.ofEntity(event.victimId());
        if (kind.isEmpty()) {
            // Eine Kreatur, die nicht aus B10s Bestand stammt - ein Vanilla-Tier etwa. Sie
            // bekommt KEINEN Ersatzschluessel: eine Kill-Rangliste zaehlt die Arten dieses
            // Spiels, und ein Sammeleintrag "unbekannt" waere eine Zeile, die niemand deuten
            // kann (FR-006).
            return;
        }

        Set<UUID> contributors = event.shares().shares().keySet();
        Set<UUID> recipients =
                KillCredit.recipients(
                        event.shares(),
                        threshold.getAsDouble(),
                        partyInRange.around(event.victimId(), contributors));

        for (UUID account : recipients) {
            statistics.count(account, MetricRegistry.MOB_KILLS, kind.get().key(), 1);
        }
    }

    /** Ein Spieler ist gestorben: ein Tod, gebucht unter seinem Verursacher (FR-010, FR-011). */
    private void countDeath(CombatDeathEvent event) {
        UUID account = accountOf(event);
        if (account == null) {
            return;
        }
        statistics.count(account, MetricRegistry.DEATHS, causeKey(event), 1);
    }

    /**
     * Unter welchem Schlüssel dieser Tod steht.
     *
     * <p><b>Kein Tod ohne Mob-Verursacher darf unter einer Mob-Art erscheinen</b> (FR-011). Die
     * drei festen Schlüssel sind deshalb keine Notlösung, sondern die Antwort: ein Sturz ist keine
     * Kreatur, und ihn unter der letzten in der Nähe zu verbuchen wäre eine Zahl, die eine
     * Geschichte erzählt, die nicht stattgefunden hat.
     */
    private String causeKey(CombatDeathEvent event) {
        if (event.cause() == DeathCause.VOID) {
            return MetricRegistry.DEATH_VOID;
        }
        if (event.cause() != DeathCause.COMBAT || event.killerId() == null) {
            // ENVIRONMENT und ADMIN, und ein Kampftod ohne bekannten Verursacher.
            return MetricRegistry.DEATH_ENVIRONMENT;
        }

        Optional<MobKind> killer = kinds.ofEntity(event.killerId());
        if (killer.isPresent()) {
            return killer.get().key();
        }
        // Ein Verursacher, der keine unserer Arten ist: ein anderer Spieler - oder ein Vanilla-
        // Wesen, das genauso wenig unter einer Mob-Art stehen darf.
        return accounts.accountOf(event.killerId()).isPresent() || isPlayerLike(event)
                ? MetricRegistry.DEATH_PLAYER
                : MetricRegistry.DEATH_ENVIRONMENT;
    }

    /**
     * Ob der Verursacher ein Spieler war.
     *
     * <p>B05 fuellt {@code killerId} mit der Entitätskennung; für einen Spieler ist das seine
     * Konto-UUID. Ein Konto, das gerade keinen Charakter im Spiel hat, ist über
     * {@link AccountLookup} nicht auffindbar — deshalb diese zweite, gröbere Frage, statt einen
     * Spielertod als Umgebungstod zu verbuchen.
     */
    private boolean isPlayerLike(CombatDeathEvent event) {
        return event.shares().shares().containsKey(event.killerId());
    }

    private UUID accountOf(CombatDeathEvent event) {
        if (event.victimCharacterId() == null) {
            return event.victimId();
        }
        // Die CHARAKTER-Kennung geht hinein, die KONTO-Kennung kommt heraus. Andersherum liefert
        // holderOf ein leeres Optional, das wie "nicht im Spiel" aussieht - der Fehler, der B08
        // und B11 je einmal erwischt hat.
        return accounts.accountOf(event.victimCharacterId()).orElse(event.victimId());
    }

    /** Nur für Tests: die festen Ersatzschlüssel an einer Stelle nachlesbar. */
    static List<String> fixedDeathKeys() {
        return List.of(
                MetricRegistry.DEATH_ENVIRONMENT,
                MetricRegistry.DEATH_VOID,
                MetricRegistry.DEATH_PLAYER);
    }
}
