package rpg.core.ui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import rpg.core.message.MessageKey;
import rpg.core.progression.ProgressView;

/**
 * Welche Zeile der Sidebar welchen Wert trägt (FR-005).
 *
 * <p><b>Jede Zeile ist ein Message-Schlüssel mit Platzhaltern, nie ein Text</b> (Prinzip V,
 * FR-014). Diese Klasse baut deshalb {@link Line}-Paare aus Schlüssel und Werten und setzt selbst
 * nichts ein — was daraus wird, entscheidet {@code Messages} und damit die Sprachdatei.
 *
 * <h2>Level und Erfahrung stehen seit B13 hier</h2>
 *
 * <p>Bis dahin trug sie die Actionbar über {@code StatusActionBar.progressText}. Sie tut es nicht
 * mehr (FR-002a): dieselben Zahlen auf zwei Flächen wären eine doppelte Wahrheit auf dem
 * Bildschirm, und die Actionbar aufzuräumen ist der Grund, aus dem dieser Block drei Flächen ordnet.
 *
 * <h2>Der Höchststufenfall ist ein eigener Schlüssel, keine abgeleitete Regel</h2>
 *
 * <p>Am Maximum ist die Schwelle 0, und {@code 4120/0} sähe aus wie ein Fehler.
 * {@link ProgressView#atMaxLevel()} beantwortet das als eigenes Feld;
 * {@code StatusActionBar.progressText} hat es bereits so benutzt, und diese Klasse folgt derselben
 * Unterscheidung, statt sie ein zweites Mal leicht anders zu erfinden.
 *
 * <h2>Drei Zeilen haben ein Ereignis, die vierte nicht</h2>
 *
 * <p>Level, Erfahrung und Zone zeichnen bei ihrer Änderung sofort neu (FR-009). <b>Die Coin-Zeile
 * nicht</b>: {@code rpg.core.currency} führt keinen Ereignistyp, nur {@code CoinLedger},
 * {@code LedgerEntry} und {@code BookingResult}. Sie folgt dem Sammeltakt und steht bis zu eine
 * Sekunde später (FR-009a). Das ist eine benannte Grenze und kein Fehler — nachgerüstet wird nichts,
 * weil ein Ereignis in B08b der Eingriff in einen fremden Block wäre, den dieser Block an drei
 * anderen Stellen ablehnt.
 */
public final class SidebarLines {

    private SidebarLines() {}

    /**
     * Eine Zeile: ein Schlüssel und die Platzhalter dazu.
     *
     * <p>Kein fertiger Text. Wer hier einen {@code String} zurückgäbe, hätte den Wortlaut im Code —
     * und der Wächter aus FR-015 müsste ihn später wieder einsammeln.
     */
    public record Line(MessageKey key, Map<String, String> values) {

        public Line {
            Objects.requireNonNull(key, "key");
            values = Map.copyOf(Objects.requireNonNull(values, "values"));
        }
    }

    /**
     * Die vier Zeilen der Sidebar, in ihrer Reihenfolge von oben nach unten.
     *
     * @param progress Level und Erfahrung aus B06
     * @param coins der Coin-Stand aus B08b
     * @param zoneName der Zonenname aus B09, oder leer für die Wildnis
     */
    public static List<Line> of(ProgressView progress, long coins, Optional<String> zoneName) {
        Objects.requireNonNull(progress, "progress");
        Objects.requireNonNull(zoneName, "zoneName");

        List<Line> lines = new ArrayList<>(4);
        lines.add(new Line(UiMessageKeys.SIDEBAR_LEVEL, Map.of("level", Integer.toString(progress.level()))));
        lines.add(xpLine(progress));
        lines.add(new Line(UiMessageKeys.SIDEBAR_COINS, Map.of("coins", Long.toString(coins))));
        lines.add(zoneLine(zoneName));
        return List.copyOf(lines);
    }

    /**
     * Die Erfahrungszeile — mit oder ohne Schwelle.
     *
     * <p>Welche gilt, folgt aus {@link ProgressView#atMaxLevel()} und nicht aus einem Vergleich, den
     * jeder Empfänger leicht anders anstellt.
     */
    private static Line xpLine(ProgressView progress) {
        if (progress.atMaxLevel()) {
            return new Line(
                    UiMessageKeys.SIDEBAR_XP_MAX,
                    Map.of("xp", Long.toString(progress.xpInLevel())));
        }
        Map<String, String> values = new LinkedHashMap<>();
        values.put("xp", Long.toString(progress.xpInLevel()));
        values.put("xpNext", Long.toString(progress.xpForNextLevel()));
        return new Line(UiMessageKeys.SIDEBAR_XP, values);
    }

    /**
     * Die Zonenzeile.
     *
     * <p><b>Die Wildnis ist ein eigener Schlüssel und kein leerer Name.</b> Eine Zeile, die
     * {@code Zone: } ohne Wert zeigt, sieht aus wie ein fehlender Wert; „Wildnis" sagt, dass der
     * Spieler wirklich in keiner Zone steht.
     */
    private static Line zoneLine(Optional<String> zoneName) {
        return zoneName.map(name -> new Line(UiMessageKeys.SIDEBAR_ZONE, Map.of("zone", name)))
                .orElseGet(() -> new Line(UiMessageKeys.SIDEBAR_ZONE_WILDERNESS, Map.of()));
    }
}
