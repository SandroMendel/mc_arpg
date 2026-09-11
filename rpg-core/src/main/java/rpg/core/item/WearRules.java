package rpg.core.item;

import java.util.EnumSet;
import java.util.Set;

import rpg.core.classes.LadderSlot;
import rpg.core.combat.DamageOrigin;
import rpg.core.combat.DeathCause;

/**
 * Welcher Vorgang welchen Slot abnutzt (FR-040 bis FR-042, data-model.md §6).
 *
 * <p><b>Nur die Entscheidung, kein Zustand.</b> Was der Verschleiß <em>ausmacht</em>, rechnet
 * {@link WearCurve}; wo er landet, hält {@link GearCondition}. Diese Klasse beantwortet eine einzige
 * Frage — welche Slots dieser Vorgang betrifft —, und zwar so, dass die Antwort an einer Stelle
 * steht und nicht in drei Zuhörern verteilt.
 *
 * <table>
 *   <caption>Die Tabelle aus data-model.md §6</caption>
 *   <tr><th>Vorgang</th><th>Rüstung</th><th>Waffe</th></tr>
 *   <tr><td>Schaden erlitten, jede Herkunft außer {@code ADMIN}</td><td>ja</td><td>nein</td></tr>
 *   <tr><td>Schaden ausgeteilt, {@code MELEE} oder {@code PROJECTILE}</td><td>nein</td><td>ja</td></tr>
 *   <tr><td>Schaden ausgeteilt, {@code ABILITY}</td><td>nein</td><td>nein</td></tr>
 *   <tr><td>Alles, was ein Klon tut oder erleidet</td><td>nein</td><td>nein</td></tr>
 *   <tr><td>Tod, außer {@code DeathCause.ADMIN}</td><td>ja</td><td>ja</td></tr>
 * </table>
 *
 * <p><b>Warum eine Fähigkeit die Waffe nicht abnutzt</b> (FR-041): sonst wäre die Klasse, die am
 * meisten mit Fähigkeiten arbeitet, die mit den höchsten Reparaturkosten — eine Strafe auf einen
 * Spielstil, die niemand als Balancing-Entscheidung getroffen hat.
 *
 * <p><b>Warum ein Klon gar nichts abnutzt</b> (FR-041a, Q10): der Klon trägt die Ausrüstung nicht.
 * Sie über ihn zu verschleißen hieße, eine Fähigkeit mit einer Rechnung zu belegen, die ihr Wortlaut
 * nicht nennt — und wer sie oft benutzt, zahlt am meisten dafür.
 */
public final class WearRules {

    private WearRules() {}

    /**
     * Was der <b>erlittene</b> Schaden abnutzt.
     *
     * @param victimIsSummon ob das Ziel ein Klon ist; dann nichts (FR-041a)
     */
    public static Set<LadderSlot> onDamageTaken(DamageOrigin origin, boolean victimIsSummon) {
        if (victimIsSummon || origin == DamageOrigin.ADMIN) {
            // ADMIN ist /kill und die Leere - kein Kampf, also keine Rechnung (FR-042).
            return Set.of();
        }
        return EnumSet.of(LadderSlot.ARMOR);
    }

    /**
     * Was der <b>ausgeteilte</b> Schaden abnutzt.
     *
     * @param attackerIsSummon ob der Angreifer ein Klon ist; dann nichts (FR-041a)
     */
    public static Set<LadderSlot> onDamageDealt(DamageOrigin origin, boolean attackerIsSummon) {
        if (attackerIsSummon) {
            return Set.of();
        }
        return switch (origin) {
            case MELEE, PROJECTILE -> EnumSet.of(LadderSlot.WEAPON);
            case ABILITY, ENVIRONMENT, ADMIN -> Set.of();
        };
    }

    /** Was der <b>Tod</b> abnutzt — beides, und deutlich stärker (FR-042, FR-043). */
    public static Set<LadderSlot> onDeath(DeathCause cause) {
        if (cause == DeathCause.ADMIN) {
            return Set.of();
        }
        return EnumSet.allOf(LadderSlot.class);
    }
}
