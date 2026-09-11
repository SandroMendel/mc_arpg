package rpg.platform.item;

import java.util.Objects;
import java.util.OptionalInt;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.inventory.ItemStack;

/**
 * Hebt ein Exemplar aus einer älteren Schema-Fassung an (FR-005).
 *
 * <p><b>Der Pfad existiert ab Tag eins, obwohl es noch nichts zu migrieren gibt.</b> Das ist
 * Absicht: ein Migrationspfad lässt sich nachträglich nicht erfinden. Wer Version 2 einführt und
 * dann feststellt, dass Version-1-Exemplare keinen Weg nach oben haben, steht vor der Wahl zwischen
 * Datenverlust und einer Sonderbehandlung, die für immer bleibt. Constitution IV verlangt den Pfad
 * deshalb <em>bevor</em> er gebraucht wird.
 *
 * <p><b>Was migriert werden kann, ist wenig — und das ist der Punkt.</b> Ein Exemplar trägt nur
 * Vorlagen-ID und Schema-Version (FR-001). Alles andere wird aus der Vorlage abgeleitet und ändert
 * sich mit ihr, ohne dass ein Item angefasst werden muss. Eine Migration ist hier also nur nötig,
 * wenn sich die <em>Form des Vermerks</em> ändert — nicht, wenn sich Werte ändern.
 *
 * <p><b>Ein Exemplar ohne Version wird nicht angefasst.</b> Es ist keines dieses Blocks: entweder
 * ein Vanilla-Gegenstand oder einer aus einem anderen. Ihm eine Version zu geben hieße, ihn zu
 * adoptieren.
 */
public final class ItemSchemaMigration {

    /** Die Fassung, die dieser Stand schreibt. */
    public static final int CURRENT = 1;

    private final Logger logger;

    public ItemSchemaMigration(Logger logger) {
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /**
     * Hebt {@code stack} auf {@link #CURRENT} an, falls nötig.
     *
     * @return {@code true}, wenn etwas geändert wurde
     */
    public boolean migrate(ItemStack stack) {
        OptionalInt version = ItemTag.schemaOf(stack);
        if (version.isEmpty()) {
            // Kein Vermerk: nicht unser Gegenstand. Nichts tun ist hier die richtige Antwort.
            return false;
        }
        int found = version.getAsInt();
        if (found == CURRENT) {
            return false;
        }
        if (found > CURRENT) {
            // Ein Exemplar aus einer NEUEREN Fassung - etwa nach einem Rueckbau des Servers.
            // Herunterzurechnen ist geraten; das Item bleibt unangetastet und inert, und die
            // Meldung sagt einem Betreiber, was er vor sich hat (Constitution VI).
            logger.log(
                    Level.WARNING,
                    "[item] an item carries schema version {0}, but this build writes {1}."
                            + " It is left untouched rather than downgraded by guesswork.",
                    new Object[] {found, CURRENT});
            return false;
        }

        // Ab hier stuende die Kette der Schritte: 1 -> 2 -> 3, jeder fuer sich, damit ein
        // Exemplar aus Version 1 auch zwei Fassungen spaeter noch ankommt. Solange CURRENT 1 ist,
        // ist die Kette leer - aber sie ist da, und der naechste Schritt haengt sich hier ein.
        String templateKey = ItemTag.templateOf(stack).orElse(null);
        if (templateKey == null) {
            return false;
        }
        ItemTag.mark(stack, templateKey, CURRENT);
        return true;
    }
}
