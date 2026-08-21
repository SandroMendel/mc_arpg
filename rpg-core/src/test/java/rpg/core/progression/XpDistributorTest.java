package rpg.core.progression;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.combat.CombatDeathEvent;
import rpg.core.combat.DamageShare;
import rpg.core.combat.DeathCause;

/**
 * The five-step distribution (FR-039 to FR-047, SC-006, SC-007, SC-013).
 *
 * <p>Every case is arithmetic with numbers written out. The curve used here makes level 2 cost 1000,
 * so nobody levels up and the credited amount can be read straight off {@code xpInLevel} - the
 * distribution is what is under test, not the level-up.
 */
class XpDistributorTest {

    private static final long MOB_XP = 100L;

    private ProgressionFixture fixture;
    private PartyRegistry parties;
    private XpDistributor distributor;
    private UUID mob;
    private WorldPoint origin;

    @BeforeEach
    void setUp() {
        Map<Integer, Long> curve = new LinkedHashMap<>();
        curve.put(2, 1_000L);
        curve.put(3, 2_000L);
        fixture = new ProgressionFixture(ProgressionFixture.config(curve));
        Logger logger = Logger.getLogger(XpDistributorTest.class.getName());
        logger.setLevel(Level.OFF);
        parties =
                new PartyRegistry(
                        fixture.sessions,
                        fixture.eventBus,
                        fixture.clock,
                        5,
                        Duration.ofSeconds(60));
        distributor =
                new XpDistributor(
                        fixture.progression, parties, fixture.stats, fixture.config, logger);
        // The creature is worth 100 here: SHEEP has no entry of its own, so a provider gives it one.
        fixture.progression.setMobXpProvider(
                key -> java.util.OptionalLong.of(MOB_XP));
        mob = UUID.randomUUID();
        origin = new WorldPoint(UUID.randomUUID(), 0, 64, 0);
    }

    /** All members count as in range, at any distance. */
    private void everyoneInRange() {
        fixture.progression.setProximityCheck(
                (o, candidates, count, range, out) -> {
                    System.arraycopy(candidates, 0, out, 0, count);
                    return count;
                });
    }

    /** Only the named players count as in range. */
    private void inRange(UUID... allowed) {
        fixture.progression.setProximityCheck(
                (o, candidates, count, range, out) -> {
                    int written = 0;
                    for (int i = 0; i < count; i++) {
                        for (UUID ok : allowed) {
                            if (candidates[i].equals(ok)) {
                                out[written++] = candidates[i];
                                break;
                            }
                        }
                    }
                    return written;
                });
    }

    private CombatDeathEvent death(Map<UUID, Double> shares) {
        UUID top =
                shares.entrySet().stream()
                        .max(Map.Entry.comparingByValue())
                        .map(Map.Entry::getKey)
                        .orElse(null);
        return new CombatDeathEvent(
                mob, null, top, DeathCause.COMBAT, new DamageShare(shares, top, 100.0), false);
    }

    private long xpOf(UUID character) {
        return fixture.progression.progressOf(character).orElseThrow().xpInLevel();
    }

    @Test
    @DisplayName("a single contributor with the whole share gets the whole amount")
    void singleContributor() {
        UUID character = fixture.character();
        UUID player = fixture.playerOf(character);

        distributor.distribute(death(Map.of(player, 1.0)), "SHEEP", origin);

        assertThat(xpOf(character)).isEqualTo(100L);
    }

    @Test
    @DisplayName("60 % and 40 % without a party become 60 and 40")
    void twoContributorsNoParty() {
        UUID a = fixture.character();
        UUID b = fixture.character();

        distributor.distribute(
                death(Map.of(fixture.playerOf(a), 0.6, fixture.playerOf(b), 0.4)), "SHEEP", origin);

        assertThat(xpOf(a)).isEqualTo(60L);
        assertThat(xpOf(b)).isEqualTo(40L);
    }

    @Test
    @DisplayName("a party of two with 60 % gets 33 each, the single contributor with 40 % gets 40")
    void partyPlusSingleContributor() {
        // SC-006, the worked example from the specification. 60 party share, +10 % for the second
        // member in range = 66, split evenly = 33 each. C keeps its own 40.
        UUID a = fixture.character();
        UUID b = fixture.character();
        UUID c = fixture.character();
        UUID pa = fixture.playerOf(a);
        UUID pb = fixture.playerOf(b);
        UUID pc = fixture.playerOf(c);
        parties.invite(pa, pb);
        parties.accept(pb);
        everyoneInRange();

        distributor.distribute(death(Map.of(pa, 0.4, pb, 0.2, pc, 0.4)), "SHEEP", origin);

        assertThat(xpOf(a)).isEqualTo(33L);
        assertThat(xpOf(b)).isEqualTo(33L);
        assertThat(xpOf(c)).isEqualTo(40L);
    }

    @Test
    @DisplayName("a member out of range gets nothing, and the bonus falls away")
    void memberOutOfRange() {
        // SC-007. Only A is in range, so the whole party share of 60 goes to A - and with one member
        // in range there is no bonus at all.
        UUID a = fixture.character();
        UUID b = fixture.character();
        UUID pa = fixture.playerOf(a);
        UUID pb = fixture.playerOf(b);
        parties.invite(pa, pb);
        parties.accept(pb);
        inRange(pa);

        distributor.distribute(death(Map.of(pa, 0.4, pb, 0.2)), "SHEEP", origin);

        assertThat(xpOf(a)).isEqualTo(60L);
        assertThat(xpOf(b)).isZero();
    }

    @Test
    @DisplayName("a member in range who dealt no damage still gets a share")
    void nonDamagingMemberStillShares() {
        UUID healer = fixture.character();
        UUID fighter = fixture.character();
        UUID ph = fixture.playerOf(healer);
        UUID pf = fixture.playerOf(fighter);
        parties.invite(pf, ph);
        parties.accept(ph);
        everyoneInRange();

        // The healer contributed nothing to the damage split at all.
        distributor.distribute(death(Map.of(pf, 1.0)), "SHEEP", origin);

        assertThat(xpOf(healer)).as("FR-041: being there is the condition, not damage").isEqualTo(55L);
        assertThat(xpOf(fighter)).isEqualTo(55L);
    }

    @Test
    @DisplayName("an empty damage split credits nobody and is not an error")
    void emptySplit() {
        UUID character = fixture.character();

        long credited = distributor.distribute(death(Map.of()), "SHEEP", origin);

        assertThat(credited).isZero();
        assertThat(xpOf(character)).isZero();
    }

    @Test
    @DisplayName("a dead player yields no experience")
    void playerVictim() {
        UUID killer = fixture.character();
        UUID pk = fixture.playerOf(killer);
        CombatDeathEvent playerDeath =
                new CombatDeathEvent(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        pk,
                        DeathCause.COMBAT,
                        new DamageShare(Map.of(pk, 1.0), pk, 100.0),
                        true);

        long credited = distributor.distribute(playerDeath, "SHEEP", origin);

        assertThat(credited).isZero();
        assertThat(xpOf(killer)).isZero();
    }

    @Test
    @DisplayName("the shares come from the event and are never recomputed")
    void sharesAreTakenAsGiven() {
        UUID a = fixture.character();
        UUID b = fixture.character();

        // Shares that do not add up to 1.0 - the distributor must use them as they are rather than
        // normalising, because B05 owns that arithmetic (FR-011).
        distributor.distribute(
                death(Map.of(fixture.playerOf(a), 0.5, fixture.playerOf(b), 0.2)), "SHEEP", origin);

        assertThat(xpOf(a)).isEqualTo(50L);
        assertThat(xpOf(b)).isEqualTo(20L);
    }

    @Test
    @DisplayName("without a registered proximity check only the contributor counts")
    void noProximityProvider() {
        // FR-044: falls back to the no-party behaviour rather than handing experience to everyone or
        // swallowing it. B09 has not been built yet, so this is today's real configuration.
        UUID a = fixture.character();
        UUID b = fixture.character();
        UUID pa = fixture.playerOf(a);
        UUID pb = fixture.playerOf(b);
        parties.invite(pa, pb);
        parties.accept(pb);

        distributor.distribute(death(Map.of(pa, 0.6, pb, 0.4)), "SHEEP", origin);

        assertThat(xpOf(a) + xpOf(b)).isEqualTo(100L);
        assertThat(xpOf(b)).isZero();
    }

    @Test
    @DisplayName("a member at the maximum level loses its share, and nobody else gains it")
    void maxedMemberShareLapses() {
        Map<Integer, Long> shortCurve = new LinkedHashMap<>();
        shortCurve.put(2, 1_000L);
        fixture = new ProgressionFixture(ProgressionFixture.config(shortCurve));
        Logger quiet = Logger.getLogger("quiet");
        quiet.setLevel(Level.OFF);
        parties =
                new PartyRegistry(
                        fixture.sessions, fixture.eventBus, fixture.clock, 5, Duration.ofSeconds(60));
        distributor =
                new XpDistributor(fixture.progression, parties, fixture.stats, fixture.config, quiet);
        fixture.progression.setMobXpProvider(key -> java.util.OptionalLong.of(MOB_XP));

        UUID maxed = fixture.character(new ProgressState(2, 0L));
        UUID normal = fixture.character();
        UUID pm = fixture.playerOf(maxed);
        UUID pn = fixture.playerOf(normal);
        parties.invite(pn, pm);
        parties.accept(pm);
        everyoneInRange();

        distributor.distribute(death(Map.of(pn, 1.0)), "SHEEP", origin);

        // 100 + 10 % = 110, split evenly = 55 each. The maxed member's 55 lapses and is NOT handed
        // to the other one (FR-052) - that would make a party with a maxed member stronger.
        assertThat(xpOf(normal)).isEqualTo(55L);
        assertThat(fixture.progression.progressOf(maxed).orElseThrow().atMaxLevel()).isTrue();
    }
}
