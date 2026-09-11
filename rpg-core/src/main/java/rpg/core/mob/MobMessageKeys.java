package rpg.core.mob;

import java.util.ArrayList;
import java.util.List;

import rpg.core.message.MessageKey;

/**
 * Die Spielertexte dieses Blocks.
 *
 * <p>Wenig, und das ist Absicht: eine Kreatur redet nicht. Was ein Spieler von ihr liest, ist ihr
 * Name ueber dem Kopf - und der steht in {@code messages.yml} wie jeder andere Text (Prinzip V).
 *
 * <p><b>Der Name einer Art ist ein Schluessel, kein Text.</b> Genau wie bei B09s Regionen: der
 * sichtbare Name lebt unter {@code mob.<key>.name} und nirgends sonst. Eine Art umzubenennen ist
 * eine Zeile dort, und keine Horde, kein Bereich und kein spaeterer Block muss dafuer angefasst
 * werden. {@link #all(List)} nimmt deshalb die konfigurierten Artschluessel entgegen - die
 * Startpruefung kann dann beweisen, dass jede Art wirklich einen Namen hat, und eine Kreatur, die
 * als {@code mob.greenfields.rotling.name} durch die Welt liefe, verweigert stattdessen den Start.
 */
public final class MobMessageKeys {

    private MobMessageKeys() {}

    /** Der sichtbare Name einer Art, {@code mob.<key>.name}. */
    public static MessageKey nameOf(String kindKey) {
        return MessageKey.of("mob." + kindKey + ".name");
    }

    /**
     * Die Zeile ueber dem Kopf einer Kreatur.
     *
     * <p>Platzhalter: {@code name}, {@code level}, {@code health}, {@code max}, {@code defense}.
     *
     * <p>Ein eigener Text neben B05s {@code combat.mob.nameplate}, weil erst dieser Block ein Level
     * kennt. Welcher der beiden gilt, entscheidet, ob die Kreatur eine Art traegt - nicht ein
     * Schalter, den jemand zu setzen vergessen kann.
     */
    public static final MessageKey NAMEPLATE = MessageKey.of("mob.nameplate");

    /**
     * Jeder Schluessel, den dieser Block ausgeben kann - fuer die Aufloesungspruefung im
     * Plugin-Modul.
     *
     * <p>Nimmt die Artschluessel entgegen, weil sie erst feststehen, wenn {@code mobs.yml} gelesen
     * ist. Dieselbe Bauart wie {@code ZoneMessageKeys.all(List)}.
     */
    public static List<MessageKey> all(List<String> kindKeys) {
        List<MessageKey> keys = new ArrayList<>();
        keys.add(NAMEPLATE);
        for (String kindKey : kindKeys) {
            keys.add(nameOf(kindKey));
        }
        return List.copyOf(keys);
    }
}
