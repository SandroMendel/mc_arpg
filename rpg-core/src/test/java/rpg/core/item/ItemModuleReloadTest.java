package rpg.core.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.config.ConfigHandle;
import rpg.core.message.MapMessages;
import rpg.core.message.Messages;
import rpg.core.zone.Zones;

/**
 * Der Start scheitert bei unbrauchbarer Konfiguration, und ein Nachladen tauscht sie <b>im
 * Ganzen</b> — dieselbe Bauart wie {@code MobModule} und {@code ZoneModule}.
 *
 * <p><b>Und das Wichtigste: ein vorhandenes Exemplar wird beim Nachladen nicht angefasst.</b> Es
 * trägt nur die Vorlagen-ID, und die zeigt nach dem Tausch auf die neuen Werte. Genau daran hängt
 * SC-001 — und daran, dass das Modul den <em>Handle</em> hält und nicht den Wert. Ein
 * festgehaltener Verweis auf die Konfiguration wäre nach dem nächsten Reload die alte Fassung, und
 * der Fehler fiele erst auf, wenn jemand fragt, warum sein Trank noch das Alte tut.
 */
class ItemModuleReloadTest {

    private static final Logger LOGGER = Logger.getLogger(ItemModuleReloadTest.class.getName());

    @Test
    @DisplayName("ein Nachladen tauscht die Konfiguration im Ganzen")
    void reloadSwapsTheWholeConfiguration() {
        MutableHandle handle = new MutableHandle(configWith(40.0));
        ItemModule module = module(handle);

        assertThat(module.template("potion.test").orElseThrow().effect().heal()).isEqualTo(40.0);

        // Das ist, was ein Reload tut: der Handle zeigt auf ein neues Dokument.
        handle.set(configWith(80.0));

        assertThat(module.template("potion.test").orElseThrow().effect().heal())
                .as("das Modul haelt den Handle, nicht den Wert")
                .isEqualTo(80.0);
    }

    @Test
    @DisplayName("wer beim Nachladen zuhoeren wollte, wird benachrichtigt")
    void reloadNotifiesListeners() {
        MutableHandle handle = new MutableHandle(configWith(40.0));
        ItemModule module = module(handle);

        boolean[] told = {false};
        module.onReload(() -> told[0] = true);

        handle.set(configWith(80.0));
        module.applyReloadedConfig();

        assertThat(told[0]).isTrue();
    }

    @Test
    @DisplayName("eine Region, die es nicht gibt, bricht den Start ab")
    void anUnknownRegionAbortsTheStart() {
        ItemConfig broken =
                new ItemConfig(
                        Map.of("potion.test", template(40.0)),
                        WearCurve.defaults(),
                        pricing(),
                        LootTables.empty(),
                        Map.of("atlantis", VendorStock.empty()));

        ItemModule module = module(new MutableHandle(broken));

        assertThatThrownBy(module::verifyAll)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("vendors.atlantis")
                // Ein Betreiber muss sehen, was es STATTDESSEN gibt - sonst raet er.
                .hasMessageContaining("Known:");
    }

    @Test
    @DisplayName("eine Art, die es nicht gibt, bricht den Start ab")
    void anUnknownMobKindAbortsTheStart() {
        ItemConfig broken =
                new ItemConfig(
                        Map.of("potion.test", template(40.0)),
                        WearCurve.defaults(),
                        pricing(),
                        new LootTables(
                                Map.of(
                                        "greenfields.nonexistent",
                                        new LootTable(List.of(LootEntry.single("potion.test", 0.5)))),
                                Map.of(),
                                Map.of()),
                        Map.of());

        assertThatThrownBy(module(new MutableHandle(broken))::verifyAll)
                .hasMessageContaining("loot.by-kind.greenfields.nonexistent")
                // Die Begruendung gehoert in die Meldung: ein Tippfehler sieht aus wie kaputte
                // Beute, nicht wie ein kaputter Buchstabe.
                .hasMessageContaining("never fires");
    }

    @Test
    @DisplayName("eine Vorlage ohne Namen in messages.yml bricht den Start ab")
    void aTemplateWithoutADisplayNameAbortsTheStart() {
        ItemModule module =
                ItemModule.withConfig(
                        LOGGER,
                        new MapMessages(Map.of()), // kein einziger Schluessel
                        ItemModuleReloadTest::zones,
                        () -> Set.of(),
                        new MutableHandle(configWith(40.0)));

        assertThatThrownBy(module::verifyAll)
                .hasMessageContaining("item.potion.test.name")
                .hasMessageContaining("Principle V");
    }

    // ---------------------------------------------------------------------------------

    private static ItemModule module(ConfigHandle<ItemConfig> handle) {
        return ItemModule.withConfig(
                LOGGER, messages(), ItemModuleReloadTest::zones, () -> Set.of("greenfields.rotling"), handle);
    }

    /**
     * Eine Textsammlung, die <b>jeden</b> Schlüssel kennt, den dieser Block ausgeben kann.
     *
     * <p>Gebaut aus {@link ItemMessageKeys#all}, nicht von Hand aufgezählt. Der handgeschriebene
     * Vorgänger war beim ersten neuen Schlüssel unvollständig — und die Prüfung, die er umgehen
     * sollte, ist genau die, die einen fehlenden Text beim Start meldet. Ein Prüfstand, der ihr
     * ausweicht, prüft weniger, als er behauptet.
     */
    private static Messages messages() {
        Map<String, String> texts = new java.util.LinkedHashMap<>();
        for (rpg.core.message.MessageKey key : ItemMessageKeys.all(Set.of("potion.test"))) {
            texts.put(key.value(), key.value());
        }
        return new MapMessages(texts);
    }

    /** Eine Zonenabfrage, die genau eine Region kennt — mehr braucht die Prüfung nicht. */
    private static Zones zones() {
        return new FakeZones();
    }

    private static ItemConfig configWith(double heal) {
        return new ItemConfig(
                Map.of("potion.test", template(heal)),
                WearCurve.defaults(),
                pricing(),
                LootTables.empty(),
                Map.of());
    }

    private static RepairPricing pricing() {
        return new RepairPricing(List.of(0L, 40L, 120L, 400L, 1200L, 3000L));
    }

    private static ItemTemplate template(double heal) {
        return new ItemTemplate(
                "potion.test",
                ItemCategory.CONSUMABLE,
                "POTION",
                Rarity.COMMON,
                null,
                null,
                3L,
                null,
                ConsumableEffect.healing(heal, Duration.ofSeconds(8)),
                null);
    }

    /** Der Handle, den ein Reload bewegt. */
    private static final class MutableHandle implements ConfigHandle<ItemConfig> {

        private ItemConfig current;

        MutableHandle(ItemConfig initial) {
            this.current = initial;
        }

        void set(ItemConfig next) {
            this.current = next;
        }

        @Override
        public ItemConfig get() {
            return current;
        }

        @Override
        public Path source() {
            return Path.of("items.yml");
        }
    }
}
