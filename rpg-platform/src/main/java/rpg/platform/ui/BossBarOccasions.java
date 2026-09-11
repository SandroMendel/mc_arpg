package rpg.platform.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import rpg.core.ui.BossBarOccasion;
import rpg.core.ui.BossBarPriority;

/**
 * Die drei Anlassquellen unter einer Rangfolge (FR-004).
 *
 * <p><b>Hier wird nicht entschieden, welcher gewinnt</b> — das tut {@link BossBarPriority}, und zwar
 * an genau einer Stelle. Diese Klasse sammelt nur ein, was anliegt, und reicht es dorthin. Kennte
 * sie die Rangfolge selbst, gäbe es zwei Stellen mit derselben Ordnung, und die zweite wäre die, die
 * bei einer Änderung vergessen wird.
 *
 * <h2>Sie fragt alle drei, auch wenn die erste antwortet</h2>
 *
 * <p>Naheliegend wäre, bei der höchstrangigen Quelle mit einer Antwort aufzuhören. Das wäre auch
 * richtig — aber es machte die Reihenfolge der <em>Abfrage</em> zur Rangfolge, und damit stünde sie
 * ein zweites Mal im Code. Drei Abfragen je Spieler und Sekunde sind drei Map-Zugriffe; die
 * Ersparnis wäre nicht messbar, der Preis wäre eine doppelte Wahrheit.
 *
 * <h2>Ein verdrängter Anlass wird nicht nachgeholt</h2>
 *
 * <p>Was nicht gewinnt, wird verworfen und nicht gemerkt (FR-004a). Dass {@code BOSS_FIGHT} und
 * {@code CHANNELLING} trotzdem zurückkehren, liegt an ihren Quellen: deren <b>Zustand</b> besteht
 * fort, und beim nächsten Takt liegen sie einfach wieder an. Ein Zonenname tut das nicht — er ist
 * ein Ereignis, das vorbei ist.
 */
public final class BossBarOccasions implements HudRefresh.BossBarSource {

    private final List<HudRefresh.BossBarSource> sources;

    public BossBarOccasions(HudRefresh.BossBarSource... sources) {
        this.sources = List.of(Objects.requireNonNull(sources, "sources"));
    }

    @Override
    public Optional<HudRefresh.BossBarContent> contentFor(UUID playerId) {
        List<HudRefresh.BossBarContent> pending = new ArrayList<>(sources.size());
        List<BossBarOccasion> occasions = new ArrayList<>(sources.size());

        for (HudRefresh.BossBarSource source : sources) {
            source.contentFor(playerId)
                    .ifPresent(
                            content -> {
                                pending.add(content);
                                occasions.add(content.occasion());
                            });
        }

        return BossBarPriority.winner(occasions)
                .flatMap(
                        winner ->
                                pending.stream()
                                        .filter(content -> content.occasion() == winner)
                                        .findFirst());
    }
}
