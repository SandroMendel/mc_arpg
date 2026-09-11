package rpg.platform.statistics;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import rpg.core.event.EventBus;
import rpg.core.statistics.AccountLookup;
import rpg.core.statistics.MetricRegistry;
import rpg.core.zone.ZoneChangedEvent;

/**
 * Schließt den laufenden Zeitabschnitt und öffnet den nächsten (FR-014f).
 *
 * <h2>Kein eigenes Nachsehen</h2>
 *
 * <p>B09 veröffentlicht bereits ein {@code ZoneChangedEvent}, und zwar an genau der Stelle, an der
 * die Zone tatsächlich wechselt — ausgelöst von einem Zuhörer, der ohnehin an jeder Bewegung
 * hängt. Ein eigener periodischer Blick, in welcher Zone jemand steht, wäre nicht nur eine
 * zusätzliche Aufgabe, sondern auch <b>ungenauer</b>: er verlegte jeden Wechsel auf den nächsten
 * Takt, und die Zonenzeiten wären um die halbe Taktbreite verschoben — systematisch, aber
 * unauffällig.
 *
 * <h2>Die Übersetzung, die hier passieren muss</h2>
 *
 * <p>{@code ZoneChangedEvent} trägt die <b>Charakter</b>kennung; die Statistik zählt auf das
 * <b>Konto</b> (FR-005). Dazwischen steht {@link AccountLookup} — und genau hier wäre die
 * Verwechslung, die B08 und B11 je einmal erwischt hat, wieder möglich gewesen: beide Kennungen
 * sind {@code UUID}, und die falsche liefert kein Fehlerbild, sondern ein leeres Ergebnis.
 *
 * <p>Ist der Charakter nicht auffindbar, wird <b>nichts</b> gebucht. Ein Rückfall auf die
 * Charakterkennung schriebe Zeit auf ein Konto, das es nicht gibt — sichtbar erst, wenn jemand
 * seine Spielzeit sucht und sie nirgends steht.
 */
public final class ZoneTimeListener {

    private final PlaytimeAccrual accrual;
    private final AccountLookup accounts;

    public ZoneTimeListener(PlaytimeAccrual accrual, AccountLookup accounts) {
        this.accrual = Objects.requireNonNull(accrual, "accrual");
        this.accounts = Objects.requireNonNull(accounts, "accounts");
    }

    public void subscribeTo(EventBus events) {
        Objects.requireNonNull(events, "events").subscribe(ZoneChangedEvent.class, this::onZoneChanged);
    }

    void onZoneChanged(ZoneChangedEvent event) {
        Optional<UUID> account = accounts.accountOf(event.characterId());
        if (account.isEmpty()) {
            return;
        }
        // Aus der Wildnis heraus und in sie hinein ist beides ein Wechsel: to() ist dann leer, und
        // der feste Ersatzschluessel haelt die Summe zusammen (FR-014e).
        accrual.zoneChanged(account.get(), event.to().orElse(MetricRegistry.ZONE_WILDERNESS));
    }
}
