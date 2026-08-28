package rpg.platform.item;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import rpg.core.item.ItemMessageKeys;
import rpg.core.message.MessageKey;
import rpg.core.message.Messages;
import rpg.platform.classes.BoundItemTag;

/**
 * {@code /trash} — der dritte Entsorgungsweg (FR-078).
 *
 * <p><b>Warum es ihn gibt.</b> ADR-018 schaltet das Fallenlassen ab: kein Gegenstand verlässt ein
 * Inventar durch die Welt. Damit bleiben zwei Wege — Enderchest und Händler —, und beide setzen
 * voraus, dass der Gegenstand etwas wert ist. Für den Rest braucht es einen dritten, sonst ist ein
 * volles Inventar mit lauter Unverkäuflichem eine Sackgasse.
 *
 * <p><b>Zwei Aufrufe, und der erste tut nichts.</b> Vernichten ist der einzige unumkehrbare Vorgang,
 * den ein Spieler hier auslösen kann; ein einzelner Tippfehler darf ihn nicht auslösen. Der erste
 * Aufruf merkt sich, <em>was</em> gemeint war, und sagt es; der zweite führt aus — aber nur, wenn er
 * denselben Gegenstand meint. Wer zwischendurch etwas anderes in die Hand nimmt, fängt von vorn an.
 *
 * <p><b>Klassenausrüstung wird abgewiesen</b> (FR-063, ADR-018). Sie ist der Fortschritt selbst; sie
 * zu vernichten wäre eine gelöschte Stufe, und die kann niemand zurückgeben. Gefragt wird B07 — hier
 * wird nur der Vermerk gelesen (FR-079).
 *
 * <p><b>Vorläufig, wie jeder Befehl vor B14</b> (ADR-028). Die Berechtigungsstruktur und die
 * Tab-Vervollständigung gehören dorthin; das hier existiert, weil ein Weg, den niemand aufrufen kann,
 * kein Weg ist.
 */
public final class TrashCommand implements CommandExecutor {

    public static final String PERMISSION = "rpg.item.trash";

    /**
     * Wie lange eine Bestätigung gilt.
     *
     * <p>Kurz genug, dass sie zu dem gehört, was der Spieler gerade tut, und lang genug, dass er den
     * zweiten Befehl noch tippen kann. Eine Bestätigung, die eine Minute später noch gilt, ist keine
     * Bestätigung mehr, sondern eine Falle.
     */
    static final Duration WINDOW = Duration.ofSeconds(30);

    /** Was ein Spieler zuletzt zu vernichten angeboten hat, und wann. */
    private record Pending(ItemStack offered, long at) {}

    private final Messages messages;
    private final Clock clock;
    private final Predicate<String> bound;

    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    /**
     * @param bound ob dieser Vermerk eine Bindung ist — B07s {@code BoundEquipment::isBound}, dieselbe
     *     Naht, die der Händler benutzt. Eine zweite Prüfung wäre eine zweite Wahrheit (FR-079).
     */
    public TrashCommand(Messages messages, Clock clock, Predicate<String> bound) {
        this.messages = Objects.requireNonNull(messages, "messages");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.bound = Objects.requireNonNull(bound, "bound");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            return true;
        }
        handle(player);
        return true;
    }

    /** Der eigentliche Vorgang, ohne Bukkits Befehlsverpackung — so ist er prüfbar. */
    public void handle(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held == null || held.getType().isAir()) {
            forget(player.getUniqueId());
            tell(player, ItemMessageKeys.TRASH_NOTHING_HELD);
            return;
        }
        if (BoundItemTag.tagOf(held).map(bound::test).orElse(false)) {
            // ADR-018: die Ausruestung IST der Fortschritt. Kein Weg vernichtet sie, und dieser hier
            // ist einer von dreien, die das einzeln nachweisen muessen.
            forget(player.getUniqueId());
            tell(player, ItemMessageKeys.TRASH_BOUND);
            return;
        }

        Pending waiting = pending.get(player.getUniqueId());
        if (waiting == null
                || !isTheSame(waiting.offered(), held)
                || clock.millis() - waiting.at() > WINDOW.toMillis()) {
            // Erster Aufruf, anderer Gegenstand, oder die Frist ist abgelaufen: fragen, nicht
            // vernichten.
            pending.put(player.getUniqueId(), new Pending(held.clone(), clock.millis()));
            tell(player, ItemMessageKeys.TRASH_CONFIRM);
            return;
        }

        // Erst jetzt, und nur fuer genau den Gegenstand, der bestaetigt wurde.
        forget(player.getUniqueId());
        player.getInventory().setItemInMainHand(null);
        tell(player, ItemMessageKeys.TRASH_DONE);
    }

    /** Vergisst eine offene Bestätigung — beim Sitzungsende, wie jede Karte je Spieler. */
    public void forget(UUID playerId) {
        pending.remove(playerId);
    }

    /** Ob für diesen Spieler eine Bestätigung offen ist. Für Tests und das Herunterfahren. */
    public boolean hasPending(UUID playerId) {
        return pending.containsKey(playerId);
    }

    public void clear() {
        pending.clear();
    }

    /**
     * Ob der Spieler noch dasselbe in der Hand hat wie beim Nachfragen.
     *
     * <p><b>Über den Gegenstand selbst, nicht über seinen Typnamen.</b> Der erste Anlauf verglich
     * {@code getType().name()} plus Stückzahl, und {@code NoRawTypeNameLeftTest} hat das gefunden —
     * zu Recht: der Vanilla-Typ ist in diesem Projekt kein Schlüssel, weil vier verschiedene Dinge
     * denselben tragen können. Hier war es dieselbe Falle in klein: zwei verschiedene Tränke sind
     * beide {@code POTION}, und ein „ja" hätte den falschen erwischt.
     *
     * <p>{@code isSimilar} vergleicht alles außer der Stückzahl — Material, Vermerke, Metadaten —,
     * also wird die Stückzahl daneben geprüft. Wer zwischen den beiden Aufrufen etwas anderes
     * greift oder den Stapel teilt, bekommt die Frage neu gestellt.
     */
    private static boolean isTheSame(ItemStack offered, ItemStack held) {
        return offered.isSimilar(held) && offered.getAmount() == held.getAmount();
    }

    private void tell(Player player, MessageKey key) {
        net.kyori.adventure.text.Component text = ItemText.of(messages, key, Map.of());
        if (text != null) {
            player.sendMessage(text);
        }
    }
}
