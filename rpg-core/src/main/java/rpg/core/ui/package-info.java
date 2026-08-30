/**
 * B13 — UI, HUD und Texte, die entscheidende Hälfte.
 *
 * <h2>Was hier wohnt</h2>
 *
 * <ul>
 *   <li>die <b>Flächenzuordnung</b> — welcher Wert auf welche der drei Vanilla-Flächen gehört, als
 *       Aufzählung und nicht als Konfiguration (FR-001, {@link rpg.core.ui.HudSurface})
 *   <li>die <b>Rangfolge</b> der Bossbar-Anlässe, an genau einer Stelle (FR-004,
 *       {@link rpg.core.ui.BossBarPriority})
 *   <li>die <b>Füllstandsrechnung</b> aus zwei Zeitstempeln — eine Rechnung, keine Aufgabe
 *       (Constitution II.2, {@link rpg.core.ui.BarProgress})
 *   <li>die <b>Sichtbarkeitsregeln</b>: wann eine Fläche für einen Halter überhaupt etwas zeigt
 *       (FR-008, {@link rpg.core.ui.HudVisibility})
 *   <li>das <b>Schema</b> von {@code ui.yml} mit Fail-Fast (Prinzip V,
 *       {@link rpg.core.ui.UiConfigSchema})
 *   <li>die <b>Message-Schlüssel</b> dieses Blocks ({@link rpg.core.ui.UiMessageKeys})
 * </ul>
 *
 * <h2>Was hier nicht wohnt</h2>
 *
 * <p><b>Paper nirgends.</b> Kein {@code org.bukkit}-Import, in keiner Klasse dieses Pakets. Was
 * eine Actionbar, eine Bossbar oder ein Scoreboard ist, weiß ausschließlich
 * {@code rpg.platform.ui}; hier stehen nur die Regeln, nach denen dort gezeichnet wird.
 *
 * <p><b>Keine Persistenz.</b> Kein Schema, keine Tabelle, keine Migration (FR-013b, SC-011). Das
 * ist keine Lücke, sondern eine Zusage: Anzeigen sind nur serverweit abschaltbar, es gibt also
 * keine persönliche Einstellung — und damit nichts, was diesen Block an B02 bände. Er lässt sich
 * vollständig entfernen, ohne dass Spielerdaten fehlen.
 *
 * <p><b>Keine eigene Spiellogik</b> (FR-074). Was gezeichnet wird, entscheiden die Blöcke, die die
 * Daten führen. Dieses Paket entscheidet nur, <em>wo</em> es steht.
 *
 * <h2>Der eine Wert, den dieser Block wegnimmt</h2>
 *
 * <p>Die Actionbar trug bis B13 auch den Fortschritt — Level, Erfahrung und Schwelle. Sie trägt ihn
 * nicht mehr (FR-002a); das ist jetzt die Sidebar. Zwei Flächen, die denselben Wert zeigen, sind
 * kein Layout, sondern eine doppelte Wahrheit auf dem Bildschirm.
 *
 * <p><b>Genau eine benannte Ausnahme</b> (FR-001a): die Vanilla-Erfahrungsleiste zeigt Level und
 * Erfahrung ein zweites Mal. Sie ist keine Zuordnungsentscheidung dieses Blocks, sondern eine
 * Fläche, die B06 bespielt. Eine <b>zweite</b> Ausnahme ist ausgeschlossen (FR-001b) — zwei, und
 * die Zusage aus FR-001 bedeutet nichts mehr.
 *
 * @see rpg.platform.ui für die zeichnende Hälfte
 */
package rpg.core.ui;
