package rpg.plugin.command.framework;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import rpg.core.item.ItemTemplate;
import rpg.core.item.Items;
import rpg.core.item.WearCurve;
import rpg.core.message.MessageKey;
import rpg.core.mob.MobKind;
import rpg.core.mob.MobKinds;

/**
 * T027 — <b>jeder vorgeschlagene Wert besteht die Prüfung</b> (FR-002).
 *
 * <p>Das ist die Zusage dieses Blocks als Test statt als Absicht. Der heutige Zustand ist ihr
 * Gegenteil: fünf handgeschriebene {@code onTabComplete}, jede in einer anderen Methode als die
 * Prüfung, mit der sie übereinstimmen soll. Solange beide dasselbe tun, fällt nichts auf — sobald
 * eine von beiden angefasst wird, schlägt die Vervollständigung Werte vor, die hinterher abgelehnt
 * werden.
 *
 * <p><b>Der Test prüft die Eigenschaft, nicht die Werte.</b> Er zählt keine Vorschläge und kennt
 * keine Liste auswendig; er nimmt, was ein Typ vorschlägt, und schickt es durch dessen eigene
 * Prüfung. Ein neuer Typ, der hier einträgt, ist damit mitgeprüft, ohne dass jemand den Test
 * anfasst — und ein Typ, der <em>nicht</em> einträgt, fällt in
 * {@link #everyTypeInTheFactoryIsCovered()} auf.
 */
class SuggestionsMatchValidationTest {

    private ServerMock server;

    @BeforeEach
    void setUp() {
        // MockBukkit nur wegen des PLAYER-Typs - der braucht einen Server, um ueberhaupt zu
        // existieren. Ohne ihn muesste er hier fehlen, und dann waere der Abdeckungstest unten
        // eine Luege.
        server = MockBukkit.mock();
        server.addPlayer("Sandro");
        server.addPlayer("Jonas");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    /** Die Typen, die Vorschläge machen. Wer keine macht, kann keine falschen machen. */
    private Map<String, ArgumentType<?>> suggestingTypes() {
        Map<String, ArgumentType<?>> types = new LinkedHashMap<>();
        types.put("period", Arguments.period());
        types.put("duration", Arguments.duration(Duration.ofDays(30)));
        types.put("player", Arguments.player(server));
        types.put("itemTemplate", Arguments.itemTemplate(itemsWith("potion.healing", "trim.ember")));
        types.put("mobKind", Arguments.mobKind(kindsWith("greenfields.rotling", "darkforest.moss-husk")));
        types.put("characterClass", Arguments.characterClass());
        // Der zusammengesetzte Typ von /stats: schlaegt Zeitraeume UND Spielernamen vor, und beides
        // muss durch dieselbe Pruefung kommen. Genau hier koennte die Zusammensetzung etwas
        // vorschlagen, das keine der beiden Haelften annimmt.
        types.put("periodOrPlayer", Arguments.periodOrPlayer(server));
        // /top: oeffentliche Ranglisten plus das Wort "score". Der Test faengt hier zwei Faelle -
        // eine PRIVATE Tafel darf nicht vorgeschlagen werden (sie wuerde abgelehnt), und "score"
        // muss durch dieselbe Pruefung kommen wie die Tafelnamen.
        types.put("leaderboard", Arguments.leaderboard());
        return types;
    }

    /** Die Typen, die bewusst nichts vorschlagen. */
    private Map<String, ArgumentType<?>> silentTypes() {
        Map<String, ArgumentType<?>> types = new LinkedHashMap<>();
        types.put("amount", Arguments.amount(1, 100));
        types.put("level", Arguments.level(() -> 60));
        types.put("atLeast", Arguments.atLeast("amount", 1));
        return types;
    }

    private static Items itemsWith(String... keys) {
        return new Items() {
            @Override
            public Optional<ItemTemplate> template(String templateKey) {
                return Optional.empty();
            }

            @Override
            public Collection<String> templateKeys() {
                return List.of(keys);
            }

            @Override
            public OptionalLong sellPriceOf(String templateKey) {
                return OptionalLong.empty();
            }

            @Override
            public WearCurve wear() {
                throw new UnsupportedOperationException("fuer diesen Test nicht noetig");
            }
        };
    }

    private static MobKinds kindsWith(String... keys) {
        List<MobKind> kinds = new java.util.ArrayList<>();
        for (String key : keys) {
            kinds.add(
                    new MobKind(
                            key,
                            "ZOMBIE",
                            1,
                            Map.of(),
                            16.0,
                            MessageKey.of("mob." + key + ".name"),
                            1L,
                            1L,
                            false));
        }
        return new MobKinds() {
            @Override
            public Optional<MobKind> find(String kindKey) {
                return kinds.stream().filter(k -> k.key().equals(kindKey)).findFirst();
            }

            @Override
            public Optional<MobKind> ofEntity(java.util.UUID entityId) {
                return Optional.empty();
            }

            @Override
            public List<MobKind> all() {
                return List.copyOf(kinds);
            }
        };
    }

    @Test
    @DisplayName("FR-002: jeder Vorschlag jedes Typs besteht die Pruefung desselben Typs")
    void everySuggestionParses() {
        suggestingTypes()
                .forEach(
                        (name, type) -> {
                            List<String> suggestions = type.suggest("");
                            assertThat(suggestions)
                                    .as("%s schlaegt nichts vor - dann steht es in silentTypes()", name)
                                    .isNotEmpty();

                            for (String suggestion : suggestions) {
                                try {
                                    type.parse(suggestion);
                                } catch (ArgumentRejected rejected) {
                                    throw new AssertionError(
                                            "Typ '"
                                                    + name
                                                    + "' schlaegt '"
                                                    + suggestion
                                                    + "' vor und lehnt es dann ab - genau die"
                                                    + " Doppelung, die FR-002 abschafft",
                                            rejected);
                                }
                            }
                        });
    }

    @Test
    @DisplayName("auch die gefilterten Vorschlaege bestehen - Filtern erfindet nichts")
    void filteredSuggestionsParseToo() {
        // Der Filter ist die Stelle, an der aus einer gueltigen Liste eine ungueltige werden
        // koennte, etwa durch Abschneiden am Praefix.
        for (Map.Entry<String, ArgumentType<?>> entry : suggestingTypes().entrySet()) {
            ArgumentType<?> type = entry.getValue();
            for (String full : type.suggest("")) {
                for (int cut = 1; cut <= full.length(); cut++) {
                    String partial = full.substring(0, cut);
                    for (String suggestion : type.suggest(partial)) {
                        try {
                            type.parse(suggestion);
                        } catch (ArgumentRejected rejected) {
                            throw new AssertionError(
                                    "Typ '"
                                            + entry.getKey()
                                            + "' schlaegt nach '"
                                            + partial
                                            + "' den ungueltigen Wert '"
                                            + suggestion
                                            + "' vor",
                                    rejected);
                        }
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("stille Typen schlagen wirklich nichts vor")
    void silentTypesStaySilent() {
        silentTypes()
                .forEach(
                        (name, type) ->
                                assertThat(type.suggest(""))
                                        .as("%s soll nichts vorschlagen", name)
                                        .isEmpty());
    }

    @Test
    @DisplayName("jeder Typ aus der Fabrik steht in einer der beiden Listen")
    void everyTypeInTheFactoryIsCovered() {
        // Ohne diesen Test waere der obige nur so viel wert wie die Sorgfalt dessen, der einen
        // neuen Typ hinzufuegt - und genau darauf soll sich hier nichts verlassen.
        List<String> factoryMethods =
                java.util.Arrays.stream(Arguments.class.getDeclaredMethods())
                        .filter(m -> java.lang.reflect.Modifier.isPublic(m.getModifiers()))
                        .filter(m -> ArgumentType.class.isAssignableFrom(m.getReturnType()))
                        .map(java.lang.reflect.Method::getName)
                        .sorted()
                        .toList();

        List<String> covered =
                java.util.stream.Stream.concat(
                                suggestingTypes().keySet().stream(), silentTypes().keySet().stream())
                        .sorted()
                        .toList();

        assertThat(covered)
                .as("ein neuer Argumenttyp gehoert in suggestingTypes() oder silentTypes()")
                .containsExactlyInAnyOrderElementsOf(factoryMethods);
    }
}
