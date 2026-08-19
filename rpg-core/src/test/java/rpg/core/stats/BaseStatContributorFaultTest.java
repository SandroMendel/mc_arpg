package rpg.core.stats;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** T026: a misbehaving base stat supplier costs its own contribution, nothing more (FR-038). */
class BaseStatContributorFaultTest {

    private record FixedContributor(String id, Attribute attribute, double amount)
            implements BaseStatContributor {
        @Override
        public void contribute(StatHolderView holder, BaseStatSink sink) {
            sink.addBase(attribute, amount);
        }
    }

    private record ThrowingContributor(String id) implements BaseStatContributor {
        @Override
        public void contribute(StatHolderView holder, BaseStatSink sink) {
            throw new IllegalStateException("deliberate failure from " + id);
        }
    }

    @Test
    @DisplayName("a throwing contributor does not stop the calculation")
    void calculationSurvives() {
        EngineFixture fixture = new EngineFixture();
        fixture.engine.registerBaseStatContributor(new ThrowingContributor("broken"));

        UUID holder = fixture.character();

        assertThat(fixture.engine.value(holder, Attribute.HEALTH)).isEqualTo(100.0);
    }

    @Test
    @DisplayName("the other contributors still land")
    void othersStillContribute() {
        EngineFixture fixture = new EngineFixture();
        fixture.engine.registerBaseStatContributor(
                new FixedContributor("level", Attribute.HEALTH, 50.0));
        fixture.engine.registerBaseStatContributor(new ThrowingContributor("broken"));
        fixture.engine.registerBaseStatContributor(
                new FixedContributor("class", Attribute.HEALTH, 25.0));

        UUID holder = fixture.character();

        assertThat(fixture.engine.value(holder, Attribute.HEALTH)).isEqualTo(175.0);
    }

    @Test
    @DisplayName("other holders are unaffected")
    void otherHoldersUnaffected() {
        EngineFixture fixture = new EngineFixture();
        fixture.engine.registerBaseStatContributor(new ThrowingContributor("broken"));

        UUID first = fixture.character();
        UUID second = fixture.character();

        assertThat(fixture.engine.value(first, Attribute.HEALTH)).isEqualTo(100.0);
        assertThat(fixture.engine.value(second, Attribute.HEALTH)).isEqualTo(100.0);
        assertThat(fixture.engine.holderCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("a contributor sees the holder read-only, including its previous snapshot")
    void contributorSeesReadOnlyView() {
        EngineFixture fixture = new EngineFixture();
        UUID playerId = UUID.randomUUID();
        UUID characterId = UUID.randomUUID();

        // Asserted inside contribute, not afterwards: the view is live for the duration of the
        // call, not a frozen copy. Reading it later would report whatever is current by then.
        var observedId = new java.util.concurrent.atomic.AtomicReference<UUID>();
        var observedCharacter = new java.util.concurrent.atomic.AtomicReference<UUID>();
        var hadPrevious = new java.util.concurrent.atomic.AtomicReference<Boolean>();

        fixture.engine.registerBaseStatContributor(
                new BaseStatContributor() {
                    @Override
                    public String id() {
                        return "observer";
                    }

                    @Override
                    public void contribute(StatHolderView holder, BaseStatSink sink) {
                        observedId.set(holder.holderId());
                        observedCharacter.set(holder.characterId().orElse(null));
                        hadPrevious.set(holder.previousSnapshot().isPresent());
                    }
                });

        fixture.engine.createForCharacter(playerId, characterId, new ResourcePool(0.0, 0.0));
        fixture.engine.recalculateNow(playerId);

        assertThat(observedId.get()).isEqualTo(playerId);
        assertThat(observedCharacter.get()).isEqualTo(characterId);
        assertThat(hadPrevious.get()).as("no previous snapshot on the first calculation").isFalse();

        // On the second calculation the contributor does see the earlier result.
        fixture.engine.recalculateNow(playerId);
        assertThat(hadPrevious.get()).isTrue();
    }

    @Test
    @DisplayName("a non-finite base contribution is refused rather than poisoning the value")
    void nonFiniteContributionRefused() {
        EngineFixture fixture = new EngineFixture();
        fixture.engine.registerBaseStatContributor(
                new FixedContributor("broken-math", Attribute.HEALTH, Double.NaN));

        UUID holder = fixture.character();

        // The sink throws, the fault barrier catches it, and the value stays sane.
        assertThat(fixture.engine.value(holder, Attribute.HEALTH)).isEqualTo(100.0);
    }
}
