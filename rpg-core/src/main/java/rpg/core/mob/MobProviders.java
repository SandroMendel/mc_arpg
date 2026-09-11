package rpg.core.mob;

import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Supplier;

import rpg.core.combat.MobStatProvider;
import rpg.core.currency.MobCoinProvider;
import rpg.core.progression.MobXpProvider;
import rpg.core.stats.Attribute;
import rpg.core.stats.ModifierSet;
import rpg.core.stats.SourceId;
import rpg.core.stats.SourceKind;
import rpg.core.stats.StatConfig;
import rpg.core.stats.StatModifier;

/**
 * Die drei Schnittstellen, die B05, B06 und B08b seit Monaten offen halten - jetzt aus
 * {@code mobs.yml} bedient.
 *
 * <p><b>Dieselbe Form, ein anderer Inhalt.</b> Alle drei behalten ihre Signatur; es wird keine
 * zweite eingefuehrt. Das war die Zusage in {@code 02-decisions.md} Abschnitt 5 und im Javadoc von
 * {@link MobXpProvider} und {@link MobCoinProvider}, jedes Mal mit demselben Satz: <em>„until B10
 * exists, then B10 replaces the provider through this same interface"</em>.
 *
 * <p>Was sich aendert, ist die <b>Bedeutung des Schluessels</b>. Bis heute war er der
 * Vanilla-Typname; ab jetzt ist er die Mob-Art. Die Umrechnung macht {@code MobKindTag.kindKeyOf}
 * an genau einer Stelle, und fuer eine Kreatur ohne Vermerk faellt sie auf den Typnamen zurueck -
 * also greifen die vorhandenen Eintraege in {@code combat.yml} unveraendert weiter (FR-009).
 *
 * <p><b>Ein leeres Ergebnis heisst „kein eigener Eintrag" und niemals Null</b> (FR-007). Ein
 * ausdrueckliches {@code 0} in der Konfiguration heisst Null - die beiden bleiben unterscheidbar,
 * denn ein Mob, den Mojang letzte Woche hinzugefuegt hat, soll nicht stillschweigend wertlos sein.
 *
 * <p><b>Warum das hier liegt und nicht in {@code rpg-platform}.</b> Keine der drei braucht einen
 * laufenden Server: es sind Nachschlaege in einer Konfiguration und eine Differenz gegen Basiswerte.
 * Die Aufteilung dieses Projekts folgt genau dieser Frage, und {@code PaperMobStatProvider} lag nur
 * deshalb drueben, weil es die Uebergangsloesung von B05 war - Bukkit hat es nie angefasst.
 */
public final class MobProviders {

    private MobProviders() {}

    /**
     * Die Attributwerte einer Art (FR-006).
     *
     * <p>Als <em>Differenz gegen den Basiswert</em>, nicht als Absolutwert - genauso, wie B05s
     * Uebergangsanbieter es tut. Die Stat-Engine addiert Modifikatoren auf ihre Basis; ein
     * Absolutwert waere dieselbe Zahl zweimal.
     *
     * <p>Leer fuer eine Art, die es nicht gibt: dann faellt der Aufrufer auf seine eigene
     * Uebergangsloesung zurueck, und ein friedliches Tier bleibt ausserhalb des Kampfsystems
     * (FR-019e in B05).
     */
    public static MobStatProvider stats(Supplier<MobConfig> config, StatConfig stats) {
        java.util.Objects.requireNonNull(config, "config");
        java.util.Objects.requireNonNull(stats, "stats");
        return kindKey -> {
            Optional<MobKind> kind = config.get().kind(kindKey);
            if (kind.isEmpty()) {
                return Optional.empty();
            }
            MobKind found = kind.get();
            return Optional.of(
                    ModifierSet.of(
                            SourceId.of(SourceKind.CLASS, "mob:" + found.key()),
                            flat(found, Attribute.HEALTH, stats),
                            flat(found, Attribute.DEFENSE, stats),
                            flat(found, Attribute.PHYSICAL_DAMAGE, stats),
                            flat(found, Attribute.MAGIC_DAMAGE, stats)));
        };
    }

    private static StatModifier flat(MobKind kind, Attribute attribute, StatConfig stats) {
        double base = stats.definition(attribute).base();
        return StatModifier.flat(attribute, kind.attributeOr(attribute, base) - base);
    }

    /** Die Erfahrung einer Art (FR-007). Leer heisst „kein eigener Eintrag", nie Null. */
    public static MobXpProvider xp(Supplier<MobConfig> config) {
        java.util.Objects.requireNonNull(config, "config");
        return kindKey ->
                config.get()
                        .kind(kindKey)
                        .map(kind -> OptionalLong.of(kind.xp()))
                        .orElseGet(OptionalLong::empty);
    }

    /** Die Coins einer Art (FR-007). Leer heisst „kein eigener Eintrag", nie Null. */
    public static MobCoinProvider coins(Supplier<MobConfig> config) {
        java.util.Objects.requireNonNull(config, "config");
        return kindKey ->
                config.get()
                        .kind(kindKey)
                        .map(kind -> OptionalLong.of(kind.coins()))
                        .orElseGet(OptionalLong::empty);
    }
}
