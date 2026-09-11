package rpg.core.statistics;

/**
 * Ob eine Metrik öffentlich rankbar ist oder nur dem Konto selbst gehört.
 *
 * <p>Der Grundsatz ist Offenheit: <b>alles ist öffentlich, außer drei Werten</b> (FR-036). Die drei
 * sind namentlich festgelegt, damit die Liste nicht mit jedem neuen Bedürfnis wächst:
 *
 * <ol>
 *   <li><b>Die Aufschlüsselung der Tode nach Verursacher</b> ({@code deaths.*}) — woran jemand
 *       immer wieder stirbt, ist eine Schwäche, keine Leistung.
 *   <li><b>Die gesamte Onlinezeit</b> ({@code playtime_online}) — sie sagt aus, wann und wie lange
 *       jemand am Rechner sitzt, und das geht die Rangliste nichts an.
 *   <li><b>Die Aufteilung der Spielzeit nach Zonen</b> ({@code playtime_active.*}) — sie verrät,
 *       wo jemand sich aufhält.
 * </ol>
 *
 * <p><b>Die Sichtbarkeit hängt am Detail, nicht an der Summe.</b> Die Einzelposten von
 * {@code deaths.*} sind privat, ihre Gesamtzahl ist öffentlich; ebenso bei
 * {@code playtime_active.*}, dessen Summe sogar die öffentliche Spielzeit-Rangliste trägt
 * (FR-037, FR-038a). Eine private Metrik ist also keine, über die nichts gesagt werden darf —
 * sondern eine, deren <em>Aufschlüsselung</em> das Konto nicht verlässt (ADR-043).
 */
public enum MetricVisibility {

    /** Rankbar, in fremden Profilen sichtbar. */
    PUBLIC,

    /** Nur im eigenen Profil — über keinen Command, kein Menü, keine Rangliste, kein Hologramm. */
    PRIVATE
}
