package rpg.core.ui;

/**
 * Die drei Vanilla-Flächen und die Rolle, die jede trägt (FR-001 bis FR-005).
 *
 * <p><b>Jeder Wert gehört genau einer Fläche.</b> Die Zuordnung ist eine Aufzählung und keine
 * Konfiguration: sie zur Wahl zu stellen hieße, jedem Betreiber die Frage zu überlassen, die dieser
 * Block gerade beantwortet hat — und zwei Flächen, die denselben Wert tragen, sind kein Layout,
 * sondern eine doppelte Wahrheit auf dem Bildschirm.
 *
 * <h2>Warum es drei sind und vorher eine war</h2>
 *
 * <p>Bis B13 trug die Actionbar alles, während Bossbar und Scoreboard <b>unbenutzt</b> dalagen —
 * zwei von vier Vanilla-Flächen brach. Das ist der Grund, aus dem dieser Block existiert.
 *
 * <h2>Die eine benannte Ausnahme</h2>
 *
 * <p>Die <b>Vanilla-Erfahrungsleiste</b> zeigt Level und Erfahrung ein zweites Mal (FR-001a). Sie
 * steht hier nicht als vierter Wert, weil sie keine Zuordnungsentscheidung dieses Blocks ist: B06
 * bespielt sie über {@code ExperienceBar}, und B13 fasst das nicht an. Eine <b>zweite</b> Ausnahme
 * ist ausgeschlossen (FR-001b) — bei zwei prüft {@code HudSurfaceAssignmentTest} nur noch, was
 * übrig blieb.
 *
 * @see BossBarOccasion für die drei Anlässe, die sich die eine Bossbar teilen
 */
public enum HudSurface {

    /**
     * Die laufenden Werte: Leben, Mana, Verteidigung.
     *
     * <p><b>Kein Fortschritt mehr</b> (FR-002a). {@code StatusActionBar.progressText} rendert bis
     * B13 auch Level, Erfahrung und Schwelle — genau die zwei Zeilen, die {@link #SIDEBAR} trägt.
     * Beim Umzug hinter {@code HudRenderer} entfällt dieser Teil. Das ist der eine Wert, den dieser
     * Block einer Fläche <em>wegnimmt</em> statt hinzuzufügen.
     *
     * <p>Sie wird im Sammeltakt <b>erneut gesendet</b>, auch wenn sich nichts geändert hat:
     * Minecraft blendet sie nach etwa zwei Sekunden von selbst aus (FR-011). Das ist die einzige
     * Fläche, für die das gilt, und der Grund, aus dem der Takt eine Sekunde lang ist.
     */
    ACTION_BAR,

    /**
     * Das Situative: Zonenname beim Betreten, Bosskampf, kanalisierte Fähigkeit.
     *
     * <p>Es gibt <b>genau eine</b> Bossbar je Spieler (FR-004b); welcher Anlass sie bekommt,
     * entscheidet {@link BossBarPriority} an einer Stelle.
     */
    BOSS_BAR,

    /**
     * Die Übersicht als Scoreboard-Sidebar: Level, Erfahrung, Coins, Zone.
     *
     * <p>Drei dieser vier Zeilen zeichnen auf ein Ereignis neu; die <b>Coin-Zeile nicht</b>, weil
     * B08b keine Buchung als Ereignis meldet (FR-009a). Sie folgt dem Sammeltakt und steht bis zu
     * eine Sekunde später — eine benannte Grenze, kein Fehler.
     */
    SIDEBAR
}
