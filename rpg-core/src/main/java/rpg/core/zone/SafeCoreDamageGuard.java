package rpg.core.zone;

import java.util.Objects;

import rpg.core.combat.DamageInterceptor;
import rpg.core.combat.DamageView;
import rpg.core.combat.PipelineStage;

/**
 * Cancels every damage event whose target stands in a safe core (FR-028, SC-002).
 *
 * <p><b>Why this exists next to {@link ZoneDamagePermission}, which says the same thing.</b> The
 * permission is consulted on the attack paths - melee, projectile, ability. It is <b>not</b> consulted
 * on the environment path: {@code DefaultCombatPipeline.environment} checks the target's stats, the
 * session and the amount, and then goes straight into the stages. That is a reasonable shape for B05,
 * because "may A hurt B" has no answer when there is no A. The consequence for B09 is that lava,
 * fire, drowning, suffocation and a fall would all have gone through a safe core untouched while the
 * permission rule sat there claiming otherwise.
 *
 * <p><b>Found by the bootstrap test, not by the rule's own test.</b> {@code ZoneDamagePermissionTest}
 * asks the rule and gets the right answer; the rule was simply never asked. The distance between
 * "the object is correct" and "the server behaves correctly" is exactly what ADR-012 is about.
 *
 * <p><b>An interceptor rather than a change to B05.</b> Registering at {@link PipelineStage#SOURCE}
 * is the published way to influence damage without the pipeline growing a case per feature - the same
 * seam B08's passives use. Adding a permission call to B05's environment path would have been an
 * edit to a shipped block for something the block already offers a door for.
 *
 * <p><b>It cancels rather than refuses, and the difference is visible.</b> A refused event carries
 * {@code NOT_PERMITTED}; this one carries {@code CANCELLED}. Both leave the player unhurt, and the
 * two reasons keep saying which of the two rules stopped it.
 */
public final class SafeCoreDamageGuard implements DamageInterceptor {

    private final ZonePresence presence;

    public SafeCoreDamageGuard(ZonePresence presence) {
        this.presence = Objects.requireNonNull(presence, "presence");
    }

    @Override
    public String id() {
        return "zone.safe-core";
    }

    @Override
    public PipelineStage stage() {
        return PipelineStage.SOURCE;
    }

    @Override
    public void intercept(DamageView damage) {
        // One map lookup per damage event, on the same placement the permission reads - no position
        // is resolved and nothing is allocated (ZonePresence).
        if (presence.inSafeCore(damage.targetId())) {
            damage.cancel();
        }
    }
}
