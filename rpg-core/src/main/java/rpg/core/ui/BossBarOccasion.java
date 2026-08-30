package rpg.core.ui;

/**
 * Die drei Anlässe, die sich die eine Bossbar teilen — <b>in der Reihenfolge ihrer Rangfolge</b>
 * (FR-004).
 *
 * <p><b>Die Reihenfolge der Aufzählung ist die Rangfolge.</b> Sie steht nicht zusätzlich als Zahl
 * daneben, weil zwei Quellen für dieselbe Ordnung genau eine zu viel sind: {@link #ordinal()} ist
 * hier keine Nebenwirkung, auf die sich jemand versehentlich verlässt, sondern die Zusage selbst.
 * {@link BossBarPriority} ist die einzige Stelle, die sie auswertet.
 *
 * <h2>Es gibt genau eine Bossbar je Spieler</h2>
 *
 * <p>Mehrere stapeln sich am oberen Bildrand und machen die Fläche unlesbar (FR-004b). Also muss
 * festgelegt sein, wer gewinnt — und zwar so, dass es <b>nicht davon abhängt, welches Ereignis
 * zufällig zuerst eintrifft</b>.
 *
 * <h2>Ein verdrängter Anlass wird nicht nachgeholt</h2>
 *
 * <p>Ein Zonenname, der während eines Bosskampfs anfiele, entfällt (FR-004a). Nachgereicht wäre er
 * eine Meldung über etwas, das längst vorbei ist.
 *
 * <p><b>Und trotzdem braucht es keine Warteschlange.</b> {@link #BOSS_FIGHT} und
 * {@link #CHANNELLING} kehren nach einer Verdrängung von selbst zurück — nicht weil jemand sie sich
 * gemerkt hat, sondern weil ihr <em>Zustand</em> beim nächsten Takt noch besteht. Das ist der
 * Unterschied zwischen einem Zustand und einem Ereignis, und er spart den einzigen veränderlichen
 * Zustand, den dieser Block sonst hätte (Constitution I.6).
 */
public enum BossBarOccasion {

    /**
     * Eine kanalisierte Fähigkeit läuft. <b>Rang 1.</b>
     *
     * <p>Sie gewinnt, weil sie Sekunden dauert und den Spieler <em>gerade festhält</em>: ohne
     * Balken weiß er nicht, wie lange er noch stillstehen muss. Der Füllstand ist eine Rechnung
     * gegen die Uhr aus {@code RunningAbility.startedAt} und {@code dueAt}, keine Aufgabe.
     */
    CHANNELLING,

    /**
     * Ein Bosskampf läuft. <b>Rang 2.</b>
     *
     * <p>Er dauert Minuten und kommt nach der Kanalisierung von selbst zurück — der Zustand war
     * nicht vorbei, nur verdeckt. Genau deshalb steht er hinter {@link #CHANNELLING} und nicht
     * davor: die kürzere Anzeige zu verdrängen kostet mehr, als die längere kurz zu unterbrechen.
     */
    BOSS_FIGHT,

    /**
     * Der Name einer betretenen Zone. <b>Rang 3.</b>
     *
     * <p>Reine Information, Sekunden lang, einmalig. Der einzige der drei Anlässe, der nach einer
     * Verdrängung <b>nicht</b> zurückkehrt — er hat keinen fortbestehenden Zustand, nur ein
     * Ereignis, das vorbei ist.
     */
    ZONE_NAME
}
