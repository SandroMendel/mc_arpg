package rpg.core.ui;

import java.util.Collection;
import java.util.Optional;

/**
 * Welcher Anlass die eine Bossbar bekommt (FR-004).
 *
 * <p><b>An genau einer Stelle.</b> Kennte jeder Aufrufer die Rangfolge selbst, kennte sie jeder ein
 * bisschen anders — und die Unterschiede fielen erst dort auf, wo zwei Anlässe zusammentreffen,
 * also selten und unter Last.
 *
 * <h2>Kein Zustand, keine Warteschlange</h2>
 *
 * <p>Diese Klasse hat kein Feld. Sie bekommt die <em>anstehenden</em> Anlässe und gibt den
 * gewinnenden zurück, mehr nicht. Das ist die ganze Umsetzung von FR-004a („ein verdrängter Anlass
 * wird nicht nachgeholt") — was nicht gemerkt wird, kann nicht nachgereicht werden.
 *
 * <p><b>Und trotzdem kehrt ein Bosskampf zurück.</b> Nicht, weil ihn jemand aufgehoben hätte,
 * sondern weil er beim nächsten Takt noch <em>anliegt</em>: sein Zustand besteht fort, das Ereignis
 * war nur verdeckt. Ein Zonenname kehrt aus demselben Grund <b>nicht</b> zurück — er hat keinen
 * Zustand, nur ein Ereignis, das vorbei ist.
 *
 * <p>Das ist der Unterschied zwischen einem Zustand und einem Ereignis, und er spart den einzigen
 * veränderlichen Zustand, den dieser Block sonst führen müsste (Constitution I.6).
 *
 * <h2>Warum die Reihenfolge so ist</h2>
 *
 * <p>Die Kanalisierung gewinnt, weil sie Sekunden dauert und den Spieler <em>gerade festhält</em>.
 * Der Bosskampf dauert Minuten und kommt danach von selbst zurück. Die kürzere Anzeige zu
 * verdrängen kostet mehr, als die längere kurz zu unterbrechen.
 */
public final class BossBarPriority {

    private BossBarPriority() {}

    /**
     * Der Anlass, der die Bossbar bekommt — oder keiner, wenn nichts anliegt.
     *
     * <p>Die Rangfolge ist die Reihenfolge von {@link BossBarOccasion}. Sie steht nicht zusätzlich
     * als Zahl daneben: zwei Quellen für dieselbe Ordnung sind genau eine zu viel.
     *
     * @param pending was gerade anliegt; darf leer sein und darf Duplikate enthalten
     */
    public static Optional<BossBarOccasion> winner(Collection<BossBarOccasion> pending) {
        BossBarOccasion best = null;
        for (BossBarOccasion occasion : pending) {
            if (occasion != null && (best == null || occasion.ordinal() < best.ordinal())) {
                best = occasion;
            }
        }
        return Optional.ofNullable(best);
    }

    /**
     * Ob {@code candidate} gegen {@code current} durchkommt.
     *
     * <p>Für den Ereignispfad, der einen einzelnen neuen Anlass gegen den stehenden hält, ohne die
     * ganze Menge zu bilden. Gleichstand verliert: ein Anlass, der schon steht, wird nicht durch
     * sich selbst ersetzt — das wäre ein Neuzeichnen ohne Änderung und damit ein Verstoß gegen
     * FR-013.
     */
    public static boolean beats(BossBarOccasion candidate, BossBarOccasion current) {
        if (candidate == null) {
            return false;
        }
        return current == null || candidate.ordinal() < current.ordinal();
    }
}
