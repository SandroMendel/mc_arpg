package rpg.core.zone;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.event.DefaultEventBus;
import rpg.core.event.EventBus;
import rpg.core.scheduler.WorldPosition;

/**
 * T122a - was B13 zum Anzeigen bekommt, ist Kennung und Ereignis, nie ein fertiger Text (FR-058,
 * FR-003a).
 *
 * <p>Die Versuchung ist konkret: das Ereignis traegt schon einen Zonenschluessel, und ein
 * {@code displayName} daneben waere bequem. Er waere auch ein zweiter Ort fuer Zonennamen - einer,
 * der aus der Konfiguration kaeme, waehrend der andere aus der Nachrichtendatei kommt, und die beiden
 * liefen beim ersten Umbenennen auseinander. Der Name gehoert in {@code messages.yml}, aufgeloest von
 * dem, der ihn anzeigt.
 */
class ZoneDisplayHandoffTest {

    @Test
    @DisplayName("das Zonenereignis traegt Schluessel, keine Namen")
    void thezoneEventCarriesKeys() {
        EventBus bus = new DefaultEventBus(Logger.getLogger("zone-handoff-test"));
        List<ZoneChangedEvent> seen = new ArrayList<>();
        bus.subscribe(ZoneChangedEvent.class, seen::add);
        UUID character = UUID.randomUUID();

        bus.publish(
                new ZoneChangedEvent(
                        character,
                        java.util.Optional.of("greenfields"),
                        java.util.Optional.of("dustlands")));

        assertThat(seen).hasSize(1);
        assertThat(seen.get(0).to()).contains("dustlands");
        assertThat(seen.get(0).from()).contains("greenfields");
    }

    @Test
    @DisplayName("kein Rueckgabewert dieses Blocks traegt einen aufgeloesten Zonennamen (FR-003a)")
    void nothingHandsOutAResolvedName() throws IOException {
        Path zonePackage = repositoryRoot().resolve("rpg-core/src/main/java/rpg/core/zone");

        try (Stream<Path> sources = Files.walk(zonePackage)) {
            List<String> offenders =
                    sources.filter(path -> path.toString().endsWith(".java"))
                            .filter(path -> !path.getFileName().toString().equals("package-info.java"))
                            .filter(ZoneDisplayHandoffTest::carriesADisplayName)
                            .map(path -> path.getFileName().toString())
                            .toList();

            assertThat(offenders)
                    .as("der sichtbare Name lebt in messages.yml unter zone.<key>.name, und nur dort")
                    .isEmpty();
        }
    }

    @Test
    @DisplayName("die Zone selbst hat kein Namensfeld - dort waere der zweite Ort entstanden")
    void thezoneRecordHasNoName() throws IOException {
        String code =
                TravelFixture.codeOnly(
                        Files.readString(
                                repositoryRoot()
                                        .resolve("rpg-core/src/main/java/rpg/core/zone/Zone.java")));

        assertThat(code).doesNotContain("displayName");
        // "String name" faengt auch ein nameKey() ab, und genau das hat es beim ersten Lauf getan:
        // der Record baute den Nachrichtenschluessel selbst zusammen, unbenutzt, neben
        // ZoneMessageKeys.nameOf. Zwei Stellen, die wissen, wie ein Zonenname adressiert wird, sind
        // eine zu viel fuer einen Block, dessen ganze Namenszusage lautet, dass es genau eine gibt.
        assertThat(code).doesNotContain("String name");
    }

    @Test
    @DisplayName("der Schluessel fuer den Namen ist eine Berechnung, kein zweiter Speicher")
    void thenameKeyIsDerived() throws Exception {
        Zones zones = TravelFixture.load(ZoneFixture.document());

        for (Zone zone : zones.all()) {
            assertThat(ZoneMessageKeys.nameOf(zone.key()).value())
                    .as(zone.key())
                    .isEqualTo("zone." + zone.key() + ".name");
        }
    }

    @Test
    @DisplayName("die Zonenabfrage liefert Schluessel, und die Position bleibt eine Position")
    void thequeryHandsOutKeys() throws Exception {
        Zones zones = TravelFixture.load(ZoneFixture.document());
        WorldPosition inGreenfields = new WorldPosition(ZoneFixture.WORLD, 0.5d, 65.0d, 0.5d);

        assertThat(zones.zoneKeyAt(inGreenfields)).isEqualTo("greenfields");
        assertThat(zones.zoneAt(inGreenfields))
                .map(Zone::key)
                .as("auch der ganze Datensatz gibt einen Schluessel her, keinen Anzeigetext")
                .contains("greenfields");
    }

    private static boolean carriesADisplayName(Path source) {
        try {
            String code = TravelFixture.codeOnly(Files.readString(source));
            return code.contains("displayName") || code.contains("localizedName");
        } catch (IOException unreadable) {
            throw new IllegalStateException("could not read " + source, unreadable);
        }
    }

    private static Path repositoryRoot() {
        Path at = Path.of("").toAbsolutePath();
        while (at != null && !Files.exists(at.resolve("settings.gradle.kts"))) {
            at = at.getParent();
        }
        if (at == null) {
            throw new IllegalStateException("repository root not found");
        }
        return at;
    }
}
