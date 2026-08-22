package rpg.persistence.ability;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import rpg.core.ability.Ability;
import rpg.core.ability.AbilityConfig;
import rpg.core.ability.UnknownAbilityException;
import rpg.core.classes.AbilityBinding;
import rpg.core.classes.CharacterClassDefinition;
import rpg.core.classes.ClassRegistry;
import rpg.core.session.CharacterClass;

/**
 * V25 to V28: the cross-check between {@code classes.yml} and {@code abilities.yml} (FR-006, FR-006a,
 * FR-007).
 *
 * <p><b>This is the promise B07 could not keep.</b> There an ability id travels as an opaque string
 * and is never looked up, because B08 did not exist yet. Here both configurations are loaded, so the
 * id can finally be checked - and the start refuses rather than letting a typo surface as a silently
 * missing ability in the game.
 *
 * <p>Deliberately <b>not</b> checked: how the six split between active and passive. Warrior and mage
 * are 4+2, the rogue is 3+3, and that is content rather than structure (ADR-025).
 */
final class AbilityBindingCheck {

    private AbilityBindingCheck() {}

    static void validate(AbilityConfig config, ClassRegistry classes, Logger logger) {
        // The mirror image of B07's rule, and the same argument. There an empty loadout is allowed
        // "while B08 does not exist" (FR-045); here unresolved bindings are allowed while B08 has no
        // definitions at all. Both say "the content has not arrived yet", and both stop being
        // tolerated the moment any content does - one defined ability turns every check below on.
        //
        // Without this the server would refuse to start for the whole of US1 to US5, because
        // classes.yml already binds the warrior's six ids and abilities.yml is filled last, on
        // purpose: SC-001 only proves something if the machine was finished first.
        if (config.size() == 0) {
            logger.warning(
                    "[abilities] abilities.yml defines nothing yet - the class bindings are NOT checked."
                            + " This is the expected state until the loadouts are filled in.");
            return;
        }

        int checked = 0;
        for (CharacterClass id : CharacterClass.values()) {
            List<AbilityBinding> bindings = classes.abilitiesOf(id);
            if (bindings.isEmpty()) {
                // Allowed while a loadout has not been filled in yet - B07 says so explicitly
                // (FR-045). The moment it is filled, the checks below apply in full.
                logger.warning(
                        () -> "[abilities] " + id + " has no loadout yet - nothing to check against");
                continue;
            }
            validateClass(config, id, bindings);
            checked += bindings.size();
        }
        int total = checked;
        logger.fine(() -> "[abilities] " + total + " class binding(s) resolved against the definitions");
    }

    private static void validateClass(
            AbilityConfig config, CharacterClass id, List<AbilityBinding> bindings) {
        // V27. B07 already refuses anything but six; repeated here because this is where the message
        // can name both files.
        if (bindings.size() != CharacterClassDefinition.TOTAL_ABILITIES) {
            throw new IllegalStateException(
                    id
                            + ": classes.yml binds "
                            + bindings.size()
                            + " abilities, but exactly "
                            + CharacterClassDefinition.TOTAL_ABILITIES
                            + " are required");
        }

        Set<String> seen = new HashSet<>();
        int uniques = 0;
        for (AbilityBinding binding : bindings) {
            if (!seen.add(binding.abilityId())) {
                throw new IllegalStateException(
                        id + ": binds '" + binding.abilityId() + "' twice");
            }
            // V25
            Ability ability;
            try {
                ability = config.require(binding.abilityId());
            } catch (UnknownAbilityException unknown) {
                throw new IllegalStateException(
                        id
                                + " binds '"
                                + binding.abilityId()
                                + "', which abilities.yml does not define",
                        unknown);
            }
            // V26. Two truths about one ability are worse than one wrong one.
            if (ability.kind() != binding.kind()) {
                throw new IllegalStateException(
                        id
                                + "."
                                + binding.abilityId()
                                + ": classes.yml says "
                                + binding.kind()
                                + ", abilities.yml says "
                                + ability.kind());
            }
            if (binding.unique()) {
                uniques++;
            }
        }

        // V28. The kind of the unique is deliberately not constrained (ADR-022).
        if (uniques != 1) {
            throw new IllegalStateException(
                    id + ": exactly one unique class ability is required, but found " + uniques);
        }
    }
}
