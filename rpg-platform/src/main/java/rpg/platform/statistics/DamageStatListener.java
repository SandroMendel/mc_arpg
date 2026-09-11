package rpg.platform.statistics;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import rpg.core.combat.DamageDealtEvent;
import rpg.core.event.EventBus;
import rpg.core.statistics.MetricRegistry;
import rpg.core.statistics.Statistics;

/**
 * Der höchste Einzeltreffer (FR-016, FR-016a).
 *
 * <h2>Was „höchster Schaden" genau heißt</h2>
 *
 * <p>Die <b>Fenstersumme nach Abwehr</b>, so wie B05 sie meldet — nicht der Rohschaden vor
 * Rüstung und nicht die Summe eines Kampfes. B05 fasst zusammengehörige Treffer bereits zu einem
 * Fenster zusammen; diese Zahl ist die, die der Getroffene tatsächlich abbekommen hat, und damit
 * die einzige, die ein Spieler nachvollziehen kann.
 *
 * <h2>Abgerundet, nicht gerundet</h2>
 *
 * <p>1249,7 wird zu <b>1249</b>. Das ist keine Nachlässigkeit: würde aufgerundet, holte ein
 * Treffer von 1249,7 einen echten Treffer von 1250 ein, und zwei verschiedene Leistungen stünden
 * mit derselben Zahl da. Beim Maximum fällt so etwas besonders auf, weil genau dieser eine Wert
 * die Rangliste trägt.
 */
public final class DamageStatListener {

    /**
     * Wem ein beschworener Klon gehört (ADR-047).
     *
     * <p>Leer für alles, was kein Klon ist — also fast immer.
     */
    @FunctionalInterface
    public interface CloneOwnership {

        Optional<UUID> summonerOf(UUID entityId);
    }

    private final Statistics statistics;
    private final CloneOwnership clones;

    public DamageStatListener(Statistics statistics, CloneOwnership clones) {
        this.statistics = Objects.requireNonNull(statistics, "statistics");
        this.clones = Objects.requireNonNull(clones, "clones");
    }

    public void subscribeTo(EventBus events) {
        Objects.requireNonNull(events, "events").subscribe(DamageDealtEvent.class, this::onDamage);
    }

    void onDamage(DamageDealtEvent event) {
        if (event.attackerId() == null) {
            // Umgebungsschaden hat keinen Urheber, dem etwas gutzuschreiben waere.
            return;
        }

        UUID account = clones.summonerOf(event.attackerId()).orElse(event.attackerId());

        // Math.floor ueber (long) zu setzen waere dasselbe fuer positive Werte - aber der Cast
        // schneidet zur Null hin ab, und ein negativer Schaden waere damit still aufgerundet.
        long damage = (long) Math.floor(event.totalDamage());
        if (damage <= 0) {
            return;
        }
        statistics.reportMax(account, MetricRegistry.DAMAGE_MAX, damage);
    }
}
