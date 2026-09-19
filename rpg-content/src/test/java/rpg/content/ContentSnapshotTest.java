package rpg.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class ContentSnapshotTest {

    private static final ContentRegistryContract ABILITIES =
            new ContentRegistryContract(
                    "abilities.<abilityId>", ContentRegistryContract.KeyType.ABILITY_ID);

    @Test
    void registryOwnsItsStructureAndPreservesTypedRegistryIdentity() {
        Map<String, String> mutableEntries = new LinkedHashMap<>();
        mutableEntries.put("slash", "Slash");
        TypedContentRegistry<String, String> abilities =
                new TypedContentRegistry<>(ABILITIES, mutableEntries);
        List<TypedContentRegistry<?, ?>> mutableRegistries = new ArrayList<>(List.of(abilities));

        ContentRegistry registry = new ContentRegistry("abilities", mutableRegistries);
        mutableEntries.put("fireball", "Fireball");
        mutableRegistries.clear();

        assertThat(registry.domain()).isEqualTo("abilities");
        assertThat(registry.paths()).containsExactly("abilities.<abilityId>");
        assertThat(registry.find(ABILITIES)).containsSame(abilities);
        assertThat(registry.require("abilities.<abilityId>")).isSameAs(abilities);
        assertThat(abilities.asMap()).containsOnly(Map.entry("slash", "Slash"));
        assertThatThrownBy(() -> registry.registries().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> registry.asMap().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void referenceRetainsCompleteFailureMetadata() {
        ContentReference reference = slashReference();

        assertThat(reference.sourceDomain()).isEqualTo("classes");
        assertThat(reference.sourceId()).isEqualTo("WARRIOR");
        assertThat(reference.targetDomain()).isEqualTo("abilities");
        assertThat(reference.targetId()).isEqualTo("slash");
        assertThat(reference.path()).isEqualTo("classes.WARRIOR.abilities[0]");
        assertThat(reference.yamlPath()).isEqualTo(reference.path());
        assertThat(reference.requirement())
                .isEqualTo("class ability references must resolve to an ability ID");
    }

    @Test
    void documentOwnsReferencesAndRequiresMatchingDomainIdentity() {
        List<ContentReference> mutableReferences = new ArrayList<>(List.of(slashReference()));
        ContentRegistry registry = ContentRegistry.empty("classes");
        Object boundContent = new Object();

        ContentDocument<Object> document =
                new ContentDocument<>(
                        "classes.yml", "classes", 1, boundContent, registry, mutableReferences);
        mutableReferences.clear();

        assertThat(document.content()).isSameAs(boundContent);
        assertThat(document.registry()).isSameAs(registry);
        assertThat(document.references()).containsExactly(slashReference());
        assertThatThrownBy(() -> document.references().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(
                        () ->
                                new ContentDocument<>(
                                        "classes.yml",
                                        "classes",
                                        1,
                                        boundContent,
                                        ContentRegistry.empty("abilities")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("document domain classes")
                .hasMessageContaining("registry domain abilities");
    }

    @Test
    void snapshotCapturesOneAtomicDocumentAndReferenceGeneration() {
        ContentDocument<String> oldClasses =
                new ContentDocument<>(
                        "classes.yml",
                        "classes",
                        1,
                        "old-classes",
                        ContentRegistry.empty("classes"),
                        List.of(slashReference()));
        List<ContentDocument<?>> publicationInput = new ArrayList<>(List.of(oldClasses));

        ContentSnapshot oldSnapshot = new ContentSnapshot(publicationInput);

        ContentReference fireballReference =
                new ContentReference(
                        "classes",
                        "MAGE",
                        "abilities",
                        "fireball",
                        "classes.MAGE.abilities[0]",
                        "class ability references must resolve to an ability ID");
        ContentDocument<String> newClasses =
                new ContentDocument<>(
                        "classes.yml",
                        "classes",
                        1,
                        "new-classes",
                        ContentRegistry.empty("classes"),
                        List.of(fireballReference));
        publicationInput.set(0, newClasses);
        ContentSnapshot newSnapshot = new ContentSnapshot(publicationInput);

        assertThat(oldSnapshot.requireByDomain("classes")).isSameAs(oldClasses);
        assertThat(oldSnapshot.requireBySource("classes.yml")).isSameAs(oldClasses);
        assertThat(oldSnapshot.references()).containsExactly(slashReference());
        assertThat(newSnapshot.requireByDomain("classes")).isSameAs(newClasses);
        assertThat(newSnapshot.references()).containsExactly(fireballReference);
        assertThatThrownBy(() -> oldSnapshot.documents().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> oldSnapshot.references().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void snapshotRejectsPartialIdentityCollisionsBeforeConstructionCompletes() {
        ContentDocument<String> classes =
                new ContentDocument<>("classes.yml", "classes", 1, "classes");
        ContentDocument<String> duplicateSource =
                new ContentDocument<>("classes.yml", "abilities", 1, "abilities");
        ContentDocument<String> duplicateDomain =
                new ContentDocument<>("classes-copy.yml", "classes", 1, "copy");

        assertThatThrownBy(() -> new ContentSnapshot(List.of(classes, duplicateSource)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate content document source: classes.yml");
        assertThatThrownBy(() -> new ContentSnapshot(List.of(classes, duplicateDomain)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate content document domain: classes");
    }

    @Test
    void blankReferenceMetadataAndDuplicateRegistryPathsAreRejected() {
        assertThatThrownBy(
                        () ->
                                new ContentReference(
                                        "classes",
                                        "WARRIOR",
                                        "abilities",
                                        "slash",
                                        " ",
                                        "must resolve"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("path must not be blank");

        TypedContentRegistry<String, String> first =
                new TypedContentRegistry<>(ABILITIES, Map.of("slash", "Slash"));
        TypedContentRegistry<String, String> second =
                new TypedContentRegistry<>(ABILITIES, Map.of("fireball", "Fireball"));
        assertThatThrownBy(() -> new ContentRegistry("abilities", List.of(first, second)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate registry path")
                .hasMessageContaining("abilities.<abilityId>");
    }

    private static ContentReference slashReference() {
        return new ContentReference(
                "classes",
                "WARRIOR",
                "abilities",
                "slash",
                "classes.WARRIOR.abilities[0]",
                "class ability references must resolve to an ability ID");
    }
}
