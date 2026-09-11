package rpg.plugin.command.framework;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Was ein Blatt bei der Ausführung in die Hand bekommt.
 *
 * <p>Die Werte sind <b>schon geprüft</b> — das Gerüst hat jedes Argument durch seinen
 * {@link ArgumentType} geschickt, bevor es hierher kommt. Ein Kommando zerlegt nichts mehr und
 * prüft nichts mehr; täte es das, gäbe es die zweite Wahrheit wieder, die FR-002 abschafft.
 */
public final class CommandContext {

    private final CommandSender sender;
    private final Map<String, Object> values;

    CommandContext(CommandSender sender, Map<String, Object> values) {
        this.sender = Objects.requireNonNull(sender, "sender");
        this.values = Map.copyOf(values);
    }

    /** Wer das Kommando abgesetzt hat — Spieler oder Konsole. */
    public CommandSender sender() {
        return sender;
    }

    /**
     * Der Spieler, falls es einer war.
     *
     * <p>Ein Blatt mit {@code requiresPlayer} bekommt hier immer einen: das Gerüst hat den
     * Konsolenfall vorher abgefangen (FR-008). Für alle anderen ist die Abwesenheit der Normalfall
     * und keine Ausnahme.
     */
    public Optional<Player> player() {
        return sender instanceof Player player ? Optional.of(player) : Optional.empty();
    }

    /**
     * Der geprüfte Wert eines Pflichtarguments.
     *
     * @throws IllegalStateException wenn das Argument nicht deklariert war — ein Programmierfehler
     *     im Kommando, kein Eingabefehler des Absenders
     */
    public <T> T get(Argument<T> argument) {
        return require(argument)
                .orElseThrow(
                        () ->
                                new IllegalStateException(
                                        "Argument '"
                                                + argument.name()
                                                + "' ist nicht als Pflicht deklariert, wird aber"
                                                + " als Pflicht gelesen"));
    }

    /** Der geprüfte Wert eines optionalen Arguments. */
    public <T> Optional<T> find(Argument<T> argument) {
        return require(argument);
    }

    @SuppressWarnings("unchecked") // der Typ kommt aus demselben Argument, das ihn geparst hat
    private <T> Optional<T> require(Argument<T> argument) {
        return Optional.ofNullable((T) values.get(argument.name()));
    }
}
