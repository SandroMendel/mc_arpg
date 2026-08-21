package rpg.core.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Der Weg von „keine Klasse gewählt" in den Spielzustand, ohne Server und ohne Datenbank.
 *
 * <p>Diese Naht ist der einzige Weg, auf dem ein Charakter in eine laufende Sitzung kommt. Fehlt sie,
 * wirkt die Klassenwahl erst beim nächsten Login: der Spieler steht ohne Werte, ohne Level und ohne
 * Ausrüstung in der Welt, und jeder Modultest von B07 bleibt grün. Genau die Fehlerklasse aus ADR-012,
 * eine Ebene weiter.
 *
 * <p>Der Unterschied zum Setzer, den {@link PlayerSession} bewusst nicht hat, ist hier mitgeprüft: ein
 * leerer Platz wird gefüllt, ein belegter nie überschrieben.
 */
class CharacterActivationTest {

    private static final Instant NOW = Instant.parse("2026-08-21T12:00:00Z");
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final Logger QUIET = Logger.getLogger(CharacterActivationTest.class.getName());
    private static final Executor DIRECT = Runnable::run;

    private DefaultSessionRegistry registry;
    private DefaultSessionLifecycle lifecycle;

    @BeforeEach
    void setUp() {
        QUIET.setLevel(Level.OFF);
        registry = new DefaultSessionRegistry();
        lifecycle =
                new DefaultSessionLifecycle(
                        registry,
                        CharacterActivationTest::emptyBundle,
                        new NoOpWriter(),
                        new StateVersionMigrator(QUIET),
                        DIRECT,
                        Clock.fixed(NOW, ZoneOffset.UTC),
                        QUIET);
    }

    // === PlayerSession.activate ===

    @Test
    @DisplayName("eine Sitzung ohne Charakter nimmt einen an - genau einmal")
    void anEmptySessionTakesOneCharacter() {
        UUID playerId = UUID.randomUUID();
        PlayerSession session = new PlayerSession(playerId, null, List.of());
        PlayerCharacter warrior = PlayerCharacter.create(playerId, CharacterClass.WARRIOR, NOW);

        assertThat(session.activate(warrior)).isTrue();

        assertThat(session.activeCharacter()).contains(warrior);
        assertThat(session.availableCharacters())
                .as("der neue Charakter gehört zum Konto und steht in der Liste")
                .containsExactly(warrior);
    }

    @Test
    @DisplayName("ein zweiter Aufruf gewinnt nicht - das ist der fehlende Setzer (FR-039)")
    void aSecondActivationLoses() {
        UUID playerId = UUID.randomUUID();
        PlayerCharacter warrior = PlayerCharacter.create(playerId, CharacterClass.WARRIOR, NOW);
        PlayerCharacter mage = PlayerCharacter.create(playerId, CharacterClass.MAGE, NOW);
        PlayerSession session = new PlayerSession(playerId, null, List.of());
        session.activate(warrior);

        assertThat(session.activate(mage)).isFalse();

        assertThat(session.activeCharacter())
                .as("der Klassenwechsel bleibt unmöglich")
                .contains(warrior);
    }

    @Test
    @DisplayName("eine Sitzung, die schon spielt, nimmt keinen weiteren an")
    void aPlayingSessionRefuses() {
        UUID playerId = UUID.randomUUID();
        PlayerCharacter warrior = PlayerCharacter.create(playerId, CharacterClass.WARRIOR, NOW);
        PlayerSession session = new PlayerSession(playerId, warrior, List.of(warrior));

        assertThat(session.activate(PlayerCharacter.create(playerId, CharacterClass.ROGUE, NOW)))
                .isFalse();
        assertThat(session.activeCharacter()).contains(warrior);
    }

    @Test
    @DisplayName("ein fremder Charakter wird abgewiesen, nicht übernommen")
    void aForeignCharacterIsRejected() {
        PlayerSession session = new PlayerSession(UUID.randomUUID(), null, List.of());
        PlayerCharacter stranger =
                PlayerCharacter.create(UUID.randomUUID(), CharacterClass.WARRIOR, NOW);

        assertThatThrownBy(() -> session.activate(stranger))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not belong to");
    }

    // === die Naht im Lebenszyklus ===

    @Test
    @DisplayName("die Aktivierung ruft jedes Attachment - sonst ist die Wahl ohne Wirkung")
    void everyAttachmentIsCalled() {
        RecordingAttachment first = new RecordingAttachment("first", 0);
        RecordingAttachment second = new RecordingAttachment("second", 0);
        lifecycle.addAttachment(first);
        lifecycle.addAttachment(second);
        UUID playerId = openSession();
        PlayerCharacter warrior = PlayerCharacter.create(playerId, CharacterClass.WARRIOR, NOW);

        assertThat(lifecycle.activateCharacter(playerId, warrior)).isTrue();

        assertThat(first.activated).containsExactly(warrior.characterId());
        assertThat(second.activated).containsExactly(warrior.characterId());
        assertThat(registry.require(playerId).activeCharacter()).contains(warrior);
    }

    @Test
    @DisplayName("ein geworfenes Attachment kostet den Charakter nicht (Prinzip VI)")
    void aThrowingAttachmentIsConfined() {
        lifecycle.addAttachment(new ThrowingAttachment());
        RecordingAttachment after = new RecordingAttachment("after", 0);
        lifecycle.addAttachment(after);
        UUID playerId = openSession();
        PlayerCharacter warrior = PlayerCharacter.create(playerId, CharacterClass.WARRIOR, NOW);

        assertThat(lifecycle.activateCharacter(playerId, warrior)).isTrue();

        assertThat(after.activated)
                .as("die Kette läuft weiter, das kaputte Attachment fehlt nur")
                .containsExactly(warrior.characterId());
    }

    @Test
    @DisplayName("ohne Sitzung wird nichts aktiviert und kein Attachment gerufen")
    void withoutASessionNothingHappens() {
        RecordingAttachment attachment = new RecordingAttachment("one", 0);
        lifecycle.addAttachment(attachment);
        UUID gone = UUID.randomUUID();

        assertThat(lifecycle.activateCharacter(
                        gone, PlayerCharacter.create(gone, CharacterClass.MAGE, NOW)))
                .isFalse();

        assertThat(attachment.activated).isEmpty();
    }

    @Test
    @DisplayName("eine Sitzung mit Charakter wird abgewiesen, ohne ein Attachment zu rufen")
    void anAlreadyPlayingSessionCallsNoAttachment() {
        RecordingAttachment attachment = new RecordingAttachment("one", 0);
        lifecycle.addAttachment(attachment);
        UUID playerId = openSession();
        PlayerCharacter warrior = PlayerCharacter.create(playerId, CharacterClass.WARRIOR, NOW);
        lifecycle.activateCharacter(playerId, warrior);
        attachment.activated.clear();

        assertThat(lifecycle.activateCharacter(
                        playerId, PlayerCharacter.create(playerId, CharacterClass.ROGUE, NOW)))
                .isFalse();

        assertThat(attachment.activated).isEmpty();
    }

    // === die Reihenfolge ===

    @Test
    @DisplayName("Zulieferer laufen vor der Rechnung, Abbau in umgekehrter Reihenfolge")
    void suppliersRunBeforeTheCalculation() {
        // Die Registrierungsreihenfolge ist absichtlich die falsche: B04 startet vor B06 und B07, weil
        // beide von ihm abhängen. Ohne order() würde die Rechnung vor ihren Eingaben laufen und
        // restoreResources gegen einen Snapshot ohne Level und ohne Klasse klemmen.
        RecordingAttachment calculation = new RecordingAttachment("stats", 100);
        RecordingAttachment level = new RecordingAttachment("progression", 0);
        RecordingAttachment classes = new RecordingAttachment("classes", 0);
        lifecycle.addAttachment(calculation);
        lifecycle.addAttachment(level);
        lifecycle.addAttachment(classes);

        assertThat(lifecycle.attachmentIds())
                .as("die Rechnung steht hinten, die Zulieferer in Registrierungsreihenfolge davor")
                .containsExactly("progression", "classes", "stats");
    }

    @Test
    @DisplayName("beim Schließen läuft die Rechnung zuerst, ihre Eingaben zuletzt")
    void teardownRunsTheOtherWayRound() {
        List<String> order = new ArrayList<>();
        lifecycle.addAttachment(new OrderRecordingAttachment("stats", 100, order));
        lifecycle.addAttachment(new OrderRecordingAttachment("progression", 0, order));
        lifecycle.addAttachment(new OrderRecordingAttachment("classes", 0, order));
        UUID playerId = openSession();

        lifecycle.endSession(playerId, SessionEndReason.QUIT).join();

        assertThat(order)
                .as("niemand gibt einen Wert ab, der aus schon freigegebenem Zustand berechnet wurde")
                .containsExactly("stats", "classes", "progression");
    }

    // --- fixtures ---

    private UUID openSession() {
        UUID playerId = UUID.randomUUID();
        lifecycle.beginLoad(playerId, TIMEOUT).join();
        lifecycle.markReady(playerId);
        return playerId;
    }

    /** Ein Konto ohne Charakter - der Zustand, in dem die Auswahl greift (ADR-020). */
    private static SessionBundle emptyBundle(UUID playerId) {
        return new SessionBundle(
                playerId,
                Optional.of(rpg.core.persistence.PlayerState.initial(playerId, NOW)),
                List.of(),
                List.of(),
                List.of(),
                List.of());
    }

    private static class RecordingAttachment implements SessionAttachment {

        private final String id;
        private final int order;
        final List<UUID> activated = new ArrayList<>();

        RecordingAttachment(String id, int order) {
            this.id = id;
            this.order = order;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public int order() {
            return order;
        }

        @Override
        public void onSessionOpened(PlayerSession session, SessionBundle bundle) {
            // nichts zu laden
        }

        @Override
        public void onCharacterActivated(
                PlayerSession session, PlayerCharacter character, SessionBundle bundle) {
            activated.add(character.characterId());
        }

        @Override
        public void onSessionClosing(UUID playerId) {
            // nichts freizugeben
        }
    }

    /** Schreibt beim Schließen seinen Namen mit, um die Abbaureihenfolge zu belegen. */
    private static final class OrderRecordingAttachment extends RecordingAttachment {

        private final List<String> closing;

        OrderRecordingAttachment(String id, int order, List<String> closing) {
            super(id, order);
            this.closing = closing;
        }

        @Override
        public void onSessionClosing(UUID playerId) {
            closing.add(id());
        }
    }

    private static final class ThrowingAttachment implements SessionAttachment {

        @Override
        public String id() {
            return "broken";
        }

        @Override
        public void onSessionOpened(PlayerSession session, SessionBundle bundle) {
            // nichts
        }

        @Override
        public void onCharacterActivated(
                PlayerSession session, PlayerCharacter character, SessionBundle bundle) {
            throw new IllegalStateException("kaputt");
        }

        @Override
        public void onSessionClosing(UUID playerId) {
            // nichts
        }
    }

    private static final class NoOpWriter implements DefaultSessionLifecycle.SessionWriter {

        @Override
        public CompletableFuture<Void> writeAndAwait(UUID playerId) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void markCharactersDirty(List<PlayerCharacter> characters) {
            // nichts zu merken
        }
    }
}
