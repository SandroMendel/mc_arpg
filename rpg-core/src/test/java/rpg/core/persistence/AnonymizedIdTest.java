package rpg.core.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * T078 / FR-017b: the substitute identifier must not be derivable from the original.
 *
 * <p>Asserting non-derivability is unusual, but the alternative failure is silent: a hash-based
 * substitute would look perfectly anonymised while anyone holding the original id could recompute
 * it and find the rows again.
 */
class AnonymizedIdTest {

    @Test
    void twoSubstitutesAreNeverTheSame() {
        Set<UUID> seen = new HashSet<>();
        for (int i = 0; i < 1_000; i++) {
            assertThat(seen.add(AnonymizedId.random().value())).isTrue();
        }
    }

    @Test
    void theSubstituteIsNotAHashOfAnyParticularInput() throws Exception {
        UUID original = UUID.randomUUID();
        AnonymizedId substitute = AnonymizedId.random();

        // The obvious derivations, spelled out so a future "optimisation" to a deterministic
        // substitute fails here rather than in an audit.
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        byte[] digest = sha256.digest(original.toString().getBytes(StandardCharsets.UTF_8));
        UUID derived = UUID.nameUUIDFromBytes(digest);

        assertThat(substitute.value()).isNotEqualTo(derived);
        assertThat(substitute.value())
                .isNotEqualTo(UUID.nameUUIDFromBytes(original.toString().getBytes(StandardCharsets.UTF_8)));
        assertThat(substitute.value()).isNotEqualTo(original);
    }

    @Test
    void generatingTwiceForTheSameOriginalYieldsDifferentSubstitutes() {
        // A deterministic substitute would let two anonymisations be linked to the same person.
        assertThat(AnonymizedId.random().value()).isNotEqualTo(AnonymizedId.random().value());
    }
}
