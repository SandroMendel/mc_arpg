package rpg.core.message;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Checks at startup that every message key a module declares actually has a text (FR-023a).
 *
 * <p>The point is timing. Without this, a typo in a key or a forgotten entry in {@code messages.yml}
 * surfaces only when the situation occurs - and the situations these texts cover are exactly the
 * rare ones: a rejected login during a database outage, a forced disconnect when the buffer fills.
 * Those are the worst possible moments to discover a missing text. Failing the bootstrap instead
 * follows the same fail-fast reasoning as B01's configuration validation.
 */
public final class MessageKeyValidator {

    private MessageKeyValidator() {}

    /** Die ausgelieferte Datei — und bis B13 die einzige, die es gab. */
    public static final String DEFAULT_SOURCE_FILE = "messages.yml";

    /**
     * Verifies that {@code messages} contains a text for every key in {@code declaredKeys}.
     *
     * <p>Nennt {@link #DEFAULT_SOURCE_FILE} als Quelle. Wer eine <em>gewählte</em> Sprachdatei
     * liest, nimmt {@link #verifyAllPresent(Messages, Collection, String)} — die Begründung steht
     * dort.
     *
     * @throws MissingMessageKeysException listing <em>all</em> missing keys, not just the first -
     *     an operator fixing a configuration file wants the whole list in one pass
     */
    public static void verifyAllPresent(Messages messages, Collection<MessageKey> declaredKeys) {
        verifyAllPresent(messages, declaredKeys, DEFAULT_SOURCE_FILE);
    }

    /**
     * Dieselbe Prüfung, aber sie nennt die Datei, aus der die Texte wirklich kamen.
     *
     * <p><b>Warum der Dateiname ein Parameter ist (B13, FR-018).</b> Bis B13 gab es genau eine
     * Textdatei, und die Meldung durfte sie fest benennen. Seit {@code ui.yml} eine Sprache wählt,
     * ist die gelesene Datei {@code messages_<code>.yml} — eine Meldung, die weiterhin
     * {@code messages.yml} nennt, schickt den Betreiber in die Datei, in der die Lücke
     * <em>nicht</em> ist. Die englische Vorlage liegt vollständig daneben, er findet dort nichts
     * und hält die Meldung für falsch.
     *
     * <p>Gefunden bei der B13-Serverabnahme, Schritt 17: der Abbruch mit lückenhafter
     * {@code messages_de.yml} meldete {@code messages.yml}.
     *
     * @param sourceFile die Datei, aus der {@code messages} gelesen wurde
     * @throws MissingMessageKeysException listing <em>all</em> missing keys, not just the first
     */
    public static void verifyAllPresent(
            Messages messages, Collection<MessageKey> declaredKeys, String sourceFile) {
        Objects.requireNonNull(messages, "messages");
        Objects.requireNonNull(declaredKeys, "declaredKeys");
        Objects.requireNonNull(sourceFile, "sourceFile");

        List<MessageKey> missing = new ArrayList<>();
        for (MessageKey key : declaredKeys) {
            if (!messages.contains(key)) {
                missing.add(key);
            }
        }
        if (!missing.isEmpty()) {
            missing.sort(MessageKey::compareTo);
            throw new MissingMessageKeysException(missing, sourceFile);
        }
    }

    /** Thrown when one or more declared keys have no configured text. */
    public static class MissingMessageKeysException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        @SuppressWarnings("serial") // List.copyOf returns a serializable implementation
        private final List<MessageKey> missingKeys;

        private final String sourceFile;

        public MissingMessageKeysException(List<MessageKey> missingKeys) {
            this(missingKeys, DEFAULT_SOURCE_FILE);
        }

        public MissingMessageKeysException(List<MessageKey> missingKeys, String sourceFile) {
            super(
                    sourceFile
                            + " is missing "
                            + missingKeys.size()
                            + " text(s) for declared message key(s): "
                            + missingKeys.stream().map(MessageKey::value).toList());
            this.missingKeys = List.copyOf(missingKeys);
            this.sourceFile = sourceFile;
        }

        /** Every key that had no text, sorted. */
        public List<MessageKey> missingKeys() {
            return missingKeys;
        }

        /** Die Datei, in der die Lücken stehen — nicht zwingend {@code messages.yml}. */
        public String sourceFile() {
            return sourceFile;
        }
    }
}
