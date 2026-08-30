/**
 * B13 — UI, HUD und Texte, die zeichnende Hälfte.
 *
 * <h2>Hier und nur hier wird Paper angefasst</h2>
 *
 * <p>Die drei Flächen (Actionbar, Bossbar, Scoreboard), die drei Fenster, die Schadensanzeigen und
 * das Cooldown-Overlay. Die Regeln dahinter stehen in {@code rpg.core.ui} und sind ohne laufenden
 * Server prüfbar (Constitution III.1).
 *
 * <h2>Die Regel, an der dieser Block still scheitern könnte</h2>
 *
 * <p>Der HUD-Takt läuft <b>asynchron</b>. Aus einem asynchronen Kontext liefert
 * {@code Scheduler.runSyncOnEntity} für alles außer einem <b>Spieler</b> einen bereits
 * abgebrochenen Handle — ohne Fehler, ohne Log, ohne dass ein Test rot wird. Deshalb:
 *
 * <ul>
 *   <li><b>Spieler</b> → {@code runSyncOnEntity} ist richtig
 *   <li><b>alles andere</b> (Schadensanzeigen, Kreaturen, Orte) → {@code runSyncAtLocation}
 *   <li>wer eine Entität erst <em>im</em> Tick auflöst, ist auf der sicheren Seite
 * </ul>
 *
 * <p>Das ist die Falle aus T112 (B10): sie bleibt grün und zeichnet nur manchmal. Sie hat dort zwei
 * Wochen gekostet, weil sich zwei Fehler gegenseitig verdeckt haben.
 *
 * <h2>Zwei Nähte, und warum sie hier liegen und nicht in {@code rpg-core}</h2>
 *
 * <p>{@code ItemRenderer} gibt einen Bukkit-{@code ItemStack} zurück und <b>kann</b> nach
 * Constitution III.1 nicht tiefer liegen. {@code HudRenderer} führt keinen Paper-Typ und ginge nach
 * unten — hätte dort aber weder Aufrufer noch Umsetzung in derselben Schicht. Beide stehen deshalb
 * bei ihrer einzigen Umsetzung; der Umzug nach unten wäre später eine Verschiebung ohne
 * Signaturänderung.
 *
 * <h2>Was hier nicht wohnt</h2>
 *
 * <p><b>Die Skill-Leiste.</b> {@code AbilityHotbar} aus B08 bleibt, wo sie ist, und wird nicht
 * hinter {@code HudRenderer} gezogen (FR-024). Das ist eine Abweichung von Constitution III.4 und
 * als ADR festgehalten, nicht stillschweigend angenommen (FR-024a).
 *
 * <p><b>{@code ClassSelectionMenu}</b> (B07) und <b>B12s {@code StatisticsMenu} /
 * {@code LeaderboardMenu}</b>. Sie laufen, sie sind abgenommen, und keine ist befristet (FR-070,
 * FR-071). Umgezogen sind nur die zwei Fenster, deren ADRs sie ausdrücklich befristen:
 * {@code WaypointMenu} (ADR-032, mit seiner Eingabe) und {@code CurrencyMenu} (ADR-028).
 *
 * @see rpg.core.ui für die Regeln, nach denen hier gezeichnet wird
 */
package rpg.platform.ui;
