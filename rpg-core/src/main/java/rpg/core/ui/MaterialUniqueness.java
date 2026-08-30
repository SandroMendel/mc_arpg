package rpg.core.ui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import rpg.core.ability.Ability;
import rpg.core.session.CharacterClass;

/**
 * Keine zwei Fähigkeiten <b>derselben Klasse</b> auf demselben Material (FR-032).
 *
 * <h2>Warum das überhaupt eine Regel ist</h2>
 *
 * <p>Das Vanilla-Cooldown-Overlay — die graue Sweep-Animation — hängt am <b>Material</b>, nicht am
 * Slot. Zwei Fähigkeiten derselben Klasse auf demselben Item teilten sich also eine Anzeige: beide
 * grau, obwohl nur eine läuft. Der Spieler sieht dann eine Fähigkeit als gesperrt, die bereit ist,
 * und drückt sie nicht.
 *
 * <p><b>Das ist kein Fehler in der Anzeige, sondern in der Konfiguration</b> — und deshalb bricht er
 * den Start ab, statt sich im Spiel zu zeigen.
 *
 * <h2>Die Vereinigung aus {@code item()} und {@code items()}</h2>
 *
 * <p>{@code Ability.item()} gibt das <em>erste</em> Material zurück; {@code items()} gibt <b>alle</b>.
 * Für eine aktive Fähigkeit ist das dasselbe — dort gibt es genau eines. Eine <b>passive</b> kann
 * mehrere belegen: der Magier deckt mit <em>Rise &amp; Fall</em> zwei Slots aus einer Fähigkeit.
 *
 * <p>Eine Prüfung nur über {@code item()} sähe die Hälfte nicht. Das war die Annahme im ersten
 * Entwurf dieses Blocks, und sie war falsch — {@code MaterialUniquenessCoversMarkersTest} ist der
 * Fall, ohne den die Prüfung die halbe Wahrheit bliebe.
 *
 * <h2>Je Klasse, nicht global</h2>
 *
 * <p>Ein Krieger und ein Magier sehen die Leisten des jeweils anderen nie. Dasselbe Material in zwei
 * verschiedenen Klassen ist deshalb <b>in Ordnung</b> — eine globale Prüfung würde
 * Konfigurationen verbieten, die niemandem schaden, und der Betreiber müsste achtzehn Fähigkeiten
 * auf achtzehn verschiedene Materialien verteilen.
 *
 * <h2>Beim Start und nicht zur Laufzeit</h2>
 *
 * <p>Im Spiel wäre es zu spät: der Spieler hat die Klasse schon gewählt, die Leiste liegt schon, und
 * eine Meldung an ihn hilft ihm nicht — er kann die Datei nicht ändern. Beim Start liest sie der
 * Betreiber, und zwar bevor jemand spielt.
 */
public final class MaterialUniqueness {

    private MaterialUniqueness() {}

    /**
     * Was diese Prüfung von einer Fähigkeit braucht: ihre Kennung und die Materialien, die sie
     * belegt.
     *
     * <p><b>Zwei Felder von dreiundzwanzig.</b> {@link Ability} trägt Manakosten, Trigger,
     * Wirkdauern und vieles mehr — nichts davon entscheidet, ob sich zwei Fähigkeiten eine Anzeige
     * teilen. Die Prüfung gegen den ganzen Record laufen zu lassen hieße, für einen Test eine
     * vollständige Fähigkeit zu bauen, und das ist der Beleg dafür, dass die Abhängigkeit zu groß
     * wäre.
     *
     * @param abilityId für die Meldung — der Betreiber muss wissen, welche zwei sich streiten
     * @param materials die <b>Vereinigung</b> aus {@code item()} und {@code items()}
     */
    public record SlotUse(String abilityId, List<String> materials) {

        public SlotUse {
            Objects.requireNonNull(abilityId, "abilityId");
            materials = List.copyOf(Objects.requireNonNull(materials, "materials"));
        }

        /** Aus einer Fähigkeit von B08 — die einzige Stelle, die den ganzen Record kennt. */
        public static SlotUse of(Ability ability) {
            return new SlotUse(ability.id(), materialsOf(ability));
        }
    }

    /**
     * Prüft alle Klassen und wirft bei der ersten Doppelung.
     *
     * <p>Die Meldung nennt <b>Klasse, beide Fähigkeiten und das Material</b> — ohne das dritte
     * müsste der Betreiber die Datei durchsuchen, und ohne das zweite wüsste er nicht, welche der
     * beiden er ändern soll.
     *
     * @param abilitiesOf die Fähigkeiten je Klasse, aus {@code AbilityRegistry.abilitiesOf}
     */
    public static void verifyAbilities(Function<CharacterClass, List<Ability>> abilitiesOf) {
        Objects.requireNonNull(abilitiesOf, "abilitiesOf");
        verify(
                characterClass ->
                        abilitiesOf.apply(characterClass).stream().map(SlotUse::of).toList());
    }

    /** Wie {@link #verifyAbilities}, aber auf dem, was die Prüfung tatsächlich braucht. */
    public static void verify(Function<CharacterClass, List<SlotUse>> slotsOf) {
        Objects.requireNonNull(slotsOf, "slotsOf");

        for (CharacterClass characterClass : CharacterClass.values()) {
            // Welche Faehigkeit welches Material zuerst belegt hat. LinkedHashMap, damit die
            // Meldung bei einer Doppelung immer dieselbe ist - eine wechselnde Reihenfolge macht
            // aus einem reproduzierbaren Startfehler einen, den man zweimal liest.
            Map<String, String> owners = new LinkedHashMap<>();

            for (SlotUse ability : slotsOf.apply(characterClass)) {
                for (String material : ability.materials()) {
                    String previous = owners.putIfAbsent(material, ability.abilityId());
                    if (previous != null && !previous.equals(ability.abilityId())) {
                        throw new IllegalStateException(
                                "abilities.yml: "
                                        + characterClass
                                        + " has two abilities on the same material - '"
                                        + previous
                                        + "' and '"
                                        + ability.abilityId()
                                        + "' both use "
                                        + material
                                        + ". The vanilla cooldown overlay is per MATERIAL, not per"
                                        + " slot, so both would grey out while only one is on"
                                        + " cooldown (FR-032)");
                    }
                }
            }
        }
    }

    /**
     * Jedes Material, das diese Fähigkeit belegt.
     *
     * <p>Die <b>Vereinigung</b>, nicht das erste: eine passive Fähigkeit kann mehrere Slots
     * markieren, und eine Prüfung über {@code item()} allein sähe nur den ersten.
     */
    public static List<String> materialsOf(Ability ability) {
        Objects.requireNonNull(ability, "ability");
        List<String> materials = new ArrayList<>(ability.items());
        String first = ability.item();
        if (first != null && !materials.contains(first)) {
            // Kann heute nicht vorkommen - item() ist das erste aus items(). Steht hier, damit die
            // Pruefung nicht still die Haelfte verliert, falls sich das je aendert.
            materials.add(first);
        }
        return List.copyOf(materials);
    }
}
