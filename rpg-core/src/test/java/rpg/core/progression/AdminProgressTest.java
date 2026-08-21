package rpg.core.progression;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.persistence.AuditEntry;
import rpg.core.stats.Attribute;
import rpg.core.stats.StatConfig;

/**
 * The operator's intervention (FR-024 to FR-024c, SC-021).
 *
 * <p>FR-024 promises a player that nothing earned in play is taken away. It does not tie the
 * operator's hands - otherwise a level handed out by a bug could only be repaired by editing the
 * database behind the authoritative cache, which the next autosave would overwrite anyway.
 */
class AdminProgressTest {

    @Test
    @DisplayName("an operator may raise level and experience freely")
    void raise() {
        ProgressionFixture fixture = new ProgressionFixture();
        UUID character = fixture.character();
        UUID actor = UUID.randomUUID();

        XpResult result = fixture.progression.setProgress(actor, character, 7, 55L);

        assertThat(result.rejected()).isFalse();
        ProgressView view = fixture.progression.progressOf(character).orElseThrow();
        assertThat(view.level()).isEqualTo(7);
        assertThat(view.xpInLevel()).isEqualTo(55L);
    }

    @Test
    @DisplayName("an operator may lower a level - the only way progress goes down")
    void lower() {
        ProgressionFixture fixture = new ProgressionFixture();
        UUID character = fixture.character(new ProgressState(8, 30L));
        UUID actor = UUID.randomUUID();

        fixture.progression.setProgress(actor, character, 3, 0L);

        assertThat(fixture.progression.levelOf(character)).hasValue(3);
    }

    @Test
    @DisplayName("every intervention lands in the audit log with the old and the new state")
    void auditLogged() {
        ProgressionFixture fixture = new ProgressionFixture();
        UUID character = fixture.character(new ProgressState(8, 30L));
        UUID actor = UUID.randomUUID();

        fixture.progression.setProgress(actor, character, 3, 0L);

        assertThat(fixture.auditLog.entries).hasSize(1);
        AuditEntry entry = fixture.auditLog.entries.get(0);
        assertThat(entry.actor()).as("an intervention nobody can attribute is worse than none")
                .isEqualTo(actor.toString());
        assertThat(entry.action()).isEqualTo("progression.set");
        assertThat(entry.details())
                .containsEntry("fromLevel", 8)
                .containsEntry("fromXp", 30L)
                .containsEntry("toLevel", 3)
                .containsEntry("toXp", 0L)
                .containsEntry("characterId", character.toString());
    }

    @Test
    @DisplayName("a lowered level does NOT refill, and clamps a value above the new maximum")
    void loweringDoesNotRefill() {
        ProgressionFixture fixture = new ProgressionFixture();
        UUID character = fixture.character(new ProgressState(9, 0L));
        double baseMax = StatConfig.defaults().definition(Attribute.HEALTH).base();
        // At level 9 the maximum is base + 8 * 8 = base + 64. Fill it up.
        fixture.fillToMax(fixture.playerOf(character));
        assertThat(fixture.health(character)).isEqualTo(baseMax + 64.0);

        fixture.progression.setProgress(UUID.randomUUID(), character, 2, 0L);

        // At level 2 the maximum is base + 8, so the old value is above it and must be clamped -
        // not refilled, because a lowering is not a rise (FR-024c).
        assertThat(fixture.maxHealth(character)).isEqualTo(baseMax + 8.0);
        assertThat(fixture.health(character)).isEqualTo(baseMax + 8.0);
    }

    @Test
    @DisplayName("a raised level refills, exactly like a natural rise")
    void raisingRefills() {
        ProgressionFixture fixture = new ProgressionFixture();
        UUID character = fixture.character();
        fixture.setHealth(character, 3.0);
        double baseMax = StatConfig.defaults().definition(Attribute.HEALTH).base();

        fixture.progression.setProgress(UUID.randomUUID(), character, 5, 0L);

        assertThat(fixture.health(character)).isEqualTo(baseMax + 4 * 8.0);
    }

    @Test
    @DisplayName("an intervention publishes the same event, marked as byAdmin")
    void publishesWithAdminFlag() {
        ProgressionFixture fixture = new ProgressionFixture();
        UUID character = fixture.character();

        fixture.progression.setProgress(UUID.randomUUID(), character, 4, 0L);

        // B13 will not want to celebrate a level somebody set by hand, while B12 still counts it.
        // Without the flag every receiver would have to guess (FR-024c).
        assertThat(fixture.levelUps).hasSize(1);
        assertThat(fixture.levelUps.get(0).byAdmin()).isTrue();
        assertThat(fixture.levelUps.get(0).newLevel()).isEqualTo(4);
    }

    @Test
    @DisplayName("attributes follow an intervention, so B08 and B09 do not stay on a stale level")
    void attributesFollow() {
        ProgressionFixture fixture = new ProgressionFixture();
        UUID character = fixture.character();
        double baseHealth = StatConfig.defaults().definition(Attribute.HEALTH).base();

        fixture.progression.setProgress(UUID.randomUUID(), character, 6, 0L);

        assertThat(fixture.attribute(character, Attribute.HEALTH)).isEqualTo(baseHealth + 5 * 8.0);
        assertThat(fixture.progression.meetsLevel(character, 6)).isTrue();
    }

    @Test
    @DisplayName("a level outside 1..maxLevel is refused without touching anything")
    void outOfRangeRefused() {
        ProgressionFixture fixture = new ProgressionFixture();
        UUID character = fixture.character(new ProgressState(4, 10L));
        UUID actor = UUID.randomUUID();

        assertThat(fixture.progression.setProgress(actor, character, 0, 0L).rejection())
                .isEqualTo(XpRejection.INVALID_AMOUNT);
        assertThat(fixture.progression.setProgress(actor, character, 11, 0L).rejection())
                .isEqualTo(XpRejection.INVALID_AMOUNT);
        assertThat(fixture.progression.setProgress(actor, character, 5, -1L).rejection())
                .isEqualTo(XpRejection.INVALID_AMOUNT);

        assertThat(fixture.progression.levelOf(character)).hasValue(4);
        assertThat(fixture.auditLog.entries).as("a refused intervention is not an intervention")
                .isEmpty();
    }

    @Test
    @DisplayName("ADMIN is the only source allowed to lower anything")
    void adminIsTheOnlyLoweringSource() {
        ProgressionFixture fixture = new ProgressionFixture();
        UUID character = fixture.character(new ProgressState(5, 50L));

        // Every other source goes through grant, and grant refuses anything that is not positive.
        assertThat(fixture.progression.grant(character, -100L, XpSource.MOB_KILL).rejection())
                .isEqualTo(XpRejection.INVALID_AMOUNT);
        assertThat(fixture.progression.grant(character, -100L, XpSource.ZONE_OBJECTIVE).rejection())
                .isEqualTo(XpRejection.INVALID_AMOUNT);
        assertThat(fixture.progression.grant(character, -100L, XpSource.ADMIN).rejection())
                .as("even ADMIN cannot lower through grant - setProgress is the way")
                .isEqualTo(XpRejection.INVALID_AMOUNT);

        assertThat(fixture.progression.levelOf(character)).hasValue(5);
    }

    @Test
    @DisplayName("an unknown character is refused")
    void unknownCharacter() {
        ProgressionFixture fixture = new ProgressionFixture();

        assertThat(
                        fixture.progression
                                .setProgress(UUID.randomUUID(), UUID.randomUUID(), 3, 0L)
                                .rejection())
                .isEqualTo(XpRejection.UNKNOWN_CHARACTER);
    }
}
