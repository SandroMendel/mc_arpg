package rpg.core.ui;

/**
 * Jeder Wert, den B13 auf den Bildschirm bringt — und die <b>eine</b> Fläche, auf die er gehört
 * (FR-001).
 *
 * <h2>Warum es diese Aufzählung gibt</h2>
 *
 * <p>FR-001c verlangt, dass die Zuordnung <b>maschinell</b> geprüft wird: ein Test geht jeden
 * anzeigbaren Wert durch und zählt die Flächen, auf denen er landet. Zwei sind ein Fehler.
 *
 * <p>Ohne diese Aufzählung stünde die Zuordnung nur im Javadoc von {@link HudSurface}, und ein Test
 * könnte sie nicht zählen — er prüfte dann seine eigene Erwartungsliste gegen sich selbst. Die
 * Aufzählung ist die <b>Quelle</b>, nicht ihre Abschrift: {@link #surface()} ist ein Feld, kein
 * Kommentar.
 *
 * <p><b>Sie ist keine Konfiguration.</b> Die Zuordnung zur Wahl zu stellen hieße, jedem Betreiber
 * die Frage zu überlassen, die dieser Block gerade beantwortet hat.
 *
 * <h2>Was hier NICHT steht</h2>
 *
 * <p><b>Level und Erfahrung auf der Vanilla-Erfahrungsleiste.</b> Sie sind die eine benannte
 * Ausnahme von FR-001 (FR-001a) und tauchen deshalb nicht als vierter Wert auf: die XP-Leiste ist
 * keine Zuordnungsentscheidung dieses Blocks, sondern eine Fläche, die B06 über
 * {@code ExperienceBar} bespielt. Stünde sie hier, zählte der Wächter zwei Flächen für {@link #XP}
 * und wäre zu Recht rot.
 *
 * <p><b>Die Herzleiste.</b> Sie zeigt Leben als Prozentwert (ADR-003, FR-007) und nicht als die
 * Zahl, die {@link #HEALTH} auf der Actionbar trägt — eine andere Größe, keine zweite Fassung
 * derselben.
 *
 * <p><b>Der Cooldown.</b> Er liegt auf dem Slot-Item als Vanilla-Overlay und belegt keine der drei
 * Flächen (FR-030).
 */
public enum DisplayedValue {

    /** Leben als Zahl. */
    HEALTH(HudSurface.ACTION_BAR),

    /** Mana als Zahl. */
    MANA(HudSurface.ACTION_BAR),

    /** Verteidigung als Zahl. */
    DEFENSE(HudSurface.ACTION_BAR),

    /**
     * Das Level.
     *
     * <p><b>Sidebar, nicht Actionbar</b> (FR-002a). {@code StatusActionBar.progressText} hat es bis
     * B13 mitgetragen; dieser Teil entfällt beim Umzug hinter {@code HudRenderer}.
     */
    LEVEL(HudSurface.SIDEBAR),

    /** Erfahrung und Schwelle. Aus demselben Grund wie {@link #LEVEL} auf der Sidebar. */
    XP(HudSurface.SIDEBAR),

    /** Der Coin-Stand. Die einzige Sidebar-Zeile ohne Ereignispfad (FR-009a). */
    COINS(HudSurface.SIDEBAR),

    /** Die Zone, in der der Spieler steht. */
    ZONE(HudSurface.SIDEBAR),

    /** Der Zonenname beim Betreten — situativ, deshalb Bossbar. */
    ZONE_NOTICE(HudSurface.BOSS_BAR),

    /** Das Leben eines Bosses im Kampf. */
    BOSS_HEALTH(HudSurface.BOSS_BAR),

    /** Der Fortschritt einer kanalisierten Fähigkeit. */
    CHANNELLING(HudSurface.BOSS_BAR);

    private final HudSurface surface;

    DisplayedValue(HudSurface surface) {
        this.surface = surface;
    }

    /** Die eine Fläche, auf die dieser Wert gehört. */
    public HudSurface surface() {
        return surface;
    }
}
