package rpg.platform.item;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.classes.LadderSlot;
import rpg.core.currency.EquipmentPurchase;

/**
 * FR-061, FR-079 — <b>der Stufenaufstieg geht durch die vorhandene Route.</b>
 *
 * <p>B08b hat {@code EquipmentPurchase} gebaut: erst fragen, was es kostet, dann B07 entscheiden
 * lassen, und erst danach buchen. Diese Reihenfolge ist die Zusage, dass ein abgelehnter Aufstieg
 * keine Coins bewegt (FR-062) — und sie ist mühsam genug, dass eine zweite Fassung davon garantiert
 * anders ausfiele.
 *
 * <p><b>Warum eine Quelltextprüfung.</b> Ein Verhaltenstest kann zeigen, dass ein Aufstieg klappt;
 * er kann nicht zeigen, dass es <em>keinen zweiten Weg</em> gibt. Genau das ist hier die
 * Anforderung. Das Verhalten der Route selbst prüft
 * {@code RefusedTierAdvanceMovesNoCoinsTest} in {@code rpg-core} — geschrieben mit diesem Block,
 * weil B11s Händler ihr erster Aufrufer überhaupt ist.
 */
class TierPurchaseUsesTheExistingRouteTest {

    private static final Path ITEM_PACKAGE =
            Path.of("src", "main", "java", "rpg", "platform", "item");

    private static final Path LISTENER = ITEM_PACKAGE.resolve("VendorListener.java");

    @Test
    @DisplayName("der Haendler ruft EquipmentPurchase.buyNext auf")
    void theVendorCallsTheExistingRoute() throws IOException {
        String code = codeOnly(Files.readString(LISTENER));

        assertThat(code)
                .as("FR-061: der Aufstieg geht durch B08bs Route und durch keine andere")
                .contains("buyNext(");
    }

    @Test
    @DisplayName("die Naht gibt B08bs EIGENEN Ergebnistyp zurueck - ein Nachbau muesste ihn nachbauen")
    void theSeamSpeaksB08bsResultType() throws NoSuchMethodException {
        var buyNext =
                VendorListener.TierRoute.class.getMethod("buyNext", UUID.class, LadderSlot.class);

        assertThat(buyNext.getReturnType())
                .as(
                        "die Naht ist schmal, damit sie testbar ist - aber sie ist auf B08bs Route"
                                + " festgelegt, nicht auf irgendeinen Aufstieg (FR-061)")
                .isEqualTo(EquipmentPurchase.Result.class);
    }

    @Test
    @DisplayName("und sie wird hereingereicht, nicht hier gebaut")
    void theRouteIsInjected() {
        boolean declared =
                List.of(VendorListener.class.getConstructors()).stream()
                        .flatMap(constructor -> List.of(constructor.getParameterTypes()).stream())
                        .anyMatch(VendorListener.TierRoute.class::equals);

        assertThat(declared)
                .as("waere sie hier gebaut statt hereingereicht, gaebe es zwei Konfigurationen davon")
                .isTrue();
    }

    @Test
    @DisplayName("KEINE Klasse dieses Blocks steigt selbst eine Stufe auf")
    void nothingHereAdvancesATierItself() throws IOException {
        assertThat(offendersContaining("TierAdvance"))
                .as(
                        "ein eigener Aufstieg haette seine eigene Reihenfolge von Pruefungen - und die"
                                + " beiden waeren nach dem ersten Balancing verschieden (FR-079)")
                .isEmpty();
    }

    @Test
    @DisplayName("und KEINE bucht Coins fuer eine Stufe an der Route vorbei")
    void nothingHereBooksTierCoinsItself() throws IOException {
        // VendorTransaction bucht Kauf und Verkauf - das ist der Handel, nicht der Aufstieg. Was
        // hier nicht stehen darf, ist eine Buchung aus diesem Paket heraus: die Reihenfolge
        // "pruefen, dann buchen" gehoert in rpg-core, wo sie geprueft ist.
        assertThat(offendersContaining("currency.debit(", "currency.credit("))
                .as("gebucht wird in rpg-core, nicht in einem Zuhoerer (SC-009)")
                .isEmpty();
    }

    private static List<String> offendersContaining(String... needles) throws IOException {
        try (var sources = Files.walk(ITEM_PACKAGE)) {
            return sources.filter(path -> path.toString().endsWith(".java"))
                    .filter(
                            path -> {
                                try {
                                    String code = codeOnly(Files.readString(path));
                                    for (String needle : needles) {
                                        if (code.contains(needle)) {
                                            return true;
                                        }
                                    }
                                    return false;
                                } catch (IOException failure) {
                                    throw new IllegalStateException(failure);
                                }
                            })
                    .map(path -> path.getFileName().toString())
                    .toList();
        }
    }

    /** Kommentare weg — eine Erklärung, warum etwas nicht dasteht, ist kein Aufruf. */
    private static String codeOnly(String source) {
        return source.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
    }
}
