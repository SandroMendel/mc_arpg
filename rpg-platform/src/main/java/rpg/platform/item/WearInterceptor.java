package rpg.platform.item;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;

import rpg.core.combat.DamageInterceptor;
import rpg.core.combat.DamageView;
import rpg.core.combat.PipelineStage;
import rpg.core.item.DefaultGearConditions;

/**
 * Wo der Verschleiß entsteht (FR-040, FR-040a, FR-041).
 *
 * <p><b>An B05s Naht, nicht an {@code DamageDealtEvent}.</b> Der Aufgabenzettel sah das aggregierte
 * Ereignis vor, und das ging nicht: es trägt keine {@code DamageOrigin}. Damit ließe sich ein
 * Autoattack nicht von einer Fähigkeit unterscheiden — und genau das ist die Anforderung aus FR-041.
 * Es trägt außerdem den Schaden <em>nach</em> der Abwehr, und FR-040a verlangt den davor. B05 hat für
 * diesen Fall ausdrücklich einen {@link DamageInterceptor} gebaut: <em>„This is how B08 (buffs) and
 * B11 (item effects) influence damage."</em>
 *
 * <p><b>Stufe {@code MODIFIERS}, und die Wahl ist die Anforderung.</b> Dort steht der Rohschaden
 * fest und die Abwehr hat ihn noch nicht angefasst. Eine Stufe später wäre der durchgekommene
 * Schaden — und damit die Abwärtsspirale, gegen die FR-040a geschrieben ist: verschlissene Rüstung
 * lässt mehr durch, das nutzt sie schneller ab.
 *
 * <p><b>Dieser Zuhörer ändert nichts.</b> Er liest und schreibt in den Zustand; er berührt weder
 * Schaden noch Abbruch. Ein Interceptor, der im selben Atemzug misst und eingreift, wäre eine Stelle,
 * an der ein Verschleißfehler zu einem Kampffehler wird.
 */
public final class WearInterceptor implements DamageInterceptor {

    /** Stabile Kennung, für die Meldung, wenn dieser Zuhörer sich danebenbenimmt. */
    public static final String ID = "item.wear";

    private final DefaultGearConditions conditions;
    private final Function<UUID, Optional<UUID>> characterOf;
    private final Predicate<UUID> isSummon;

    /**
     * @param characterOf Halter zu Charakter — B04s Übersetzung, nicht eine zweite
     * @param isSummon ob dieser Halter gerade ein Klon ist (FR-041a)
     */
    public WearInterceptor(
            DefaultGearConditions conditions,
            Function<UUID, Optional<UUID>> characterOf,
            Predicate<UUID> isSummon) {
        this.conditions = Objects.requireNonNull(conditions, "conditions");
        this.characterOf = Objects.requireNonNull(characterOf, "characterOf");
        this.isSummon = Objects.requireNonNull(isSummon, "isSummon");
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public PipelineStage stage() {
        return PipelineStage.MODIFIERS;
    }

    @Override
    public void intercept(DamageView damage) {
        double incoming = damage.rawDamage();
        if (!(incoming > 0.0)) {
            return;
        }

        // Der Getroffene: seine Ruestung nutzt sich ab.
        characterOf
                .apply(damage.targetId())
                .ifPresent(
                        characterId ->
                                conditions.onDamageTaken(
                                        characterId,
                                        damage.origin(),
                                        isSummon.test(damage.targetId()),
                                        incoming));

        // Der Angreifer: seine Waffe, und nur bei einem Autoattack.
        damage.attackerId()
                .flatMap(characterOf)
                .ifPresent(
                        characterId ->
                                conditions.onDamageDealt(
                                        characterId,
                                        damage.origin(),
                                        damage.attackerId().filter(isSummon).isPresent(),
                                        incoming));
    }
}
