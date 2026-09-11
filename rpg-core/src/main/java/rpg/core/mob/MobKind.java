package rpg.core.mob;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

import rpg.core.message.MessageKey;
import rpg.core.stats.Attribute;

/**
 * Eine Mob-Art aus {@code mobs.yml} - die Einheit, nach der die drei wartenden Schnittstellen
 * fragen.
 *
 * <p><b>Warum das nicht der Vanilla-Typ ist.</b> Acht Arten je Region auf sechs Regionen sind 48,
 * und Minecraft hat nicht 48 passende Entity-Typen. Mehrere Arten teilen sich also eine Basis, und
 * genau daran ist der Schluessel bisher gescheitert: {@code creature.getType().name()} antwortet
 * fuer vier verschiedene Kreaturen vier Mal {@code ZOMBIE}. Die Art ist der Unterschied, den es
 * braucht, und {@link #key()} ist er.
 *
 * <p><b>{@code boss} ist ein Etikett und keine Mechanik.</b> Ein Boss dieses Blocks ist eine Art mit
 * deutlich hoeheren Zahlen, einem eigenen Platz und einem Respawn-Timer - keine eigenen
 * Faehigkeiten, keine Phasen. Das ist ehrlich benannt: es ist ein dicker Mob und kein Kampf mit
 * Wendungen. Der eigentliche Bosskampf kommt spaeter als Dungeon-Boss und braucht eine Instanzwelt,
 * die es nach ADR-006 noch nicht gibt.
 *
 * @param key Kennung, eindeutig ueber alle Arten; der Wert, der im Vermerk der Entitaet landet
 * @param base Name eines Vanilla-Entity-Typs. Hier ein {@code String} und kein Bukkit-Typ, weil
 *     dieses Paket kein Bukkit sieht (Prinzip III.1) - dass der Name existiert, prueft die
 *     Plattformschicht beim Start
 * @param level was die Anzeige nennt, und woran ein Levelband sich spaeter messen laesst
 * @param attributes was B04 fuer diese Art setzt. Kein eigenes Kampfmodell (FR-008)
 * @param followRange Zielsuchreichweite in Bloecken. Die Stellschraube mit dem groessten Hebel: die
 *     Suche ist quadratisch im Radius (research.md R6)
 * @param displayNameKey nie ein Text (Prinzip V)
 * @param xp was {@code MobXpProvider} fuer diese Art antwortet
 * @param coins was {@code MobCoinProvider} fuer diese Art antwortet
 * @param boss ob sie als Boss ihrer Region gilt
 */
public record MobKind(
        String key,
        String base,
        int level,
        Map<Attribute, Double> attributes,
        double followRange,
        MessageKey displayNameKey,
        long xp,
        long coins,
        boolean boss) {

    public MobKind {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(base, "base");
        Objects.requireNonNull(displayNameKey, "displayNameKey");
        if (key.isBlank()) {
            throw new IllegalArgumentException("a mob kind key must not be blank");
        }
        if (base.isBlank()) {
            throw new IllegalArgumentException(key + ": base must not be blank");
        }
        if (level < 1) {
            throw new IllegalArgumentException(key + ": level must be >= 1, but was " + level);
        }
        if (!Double.isFinite(followRange) || followRange <= 0.0) {
            throw new IllegalArgumentException(
                    key + ": follow-range must be a positive number, but was " + followRange);
        }
        if (xp < 0L) {
            throw new IllegalArgumentException(key + ": xp must not be negative, but was " + xp);
        }
        if (coins < 0L) {
            throw new IllegalArgumentException(
                    key + ": coins must not be negative, but was " + coins);
        }
        // Eine EnumMap statt der uebergebenen Karte: sie ist kompakt, ihre Iteration ist
        // vorhersagbar, und sie wird beim Setzen jeder Kreatur gelesen.
        Map<Attribute, Double> copy = new EnumMap<>(Attribute.class);
        copy.putAll(Objects.requireNonNull(attributes, "attributes"));
        for (Map.Entry<Attribute, Double> entry : copy.entrySet()) {
            Double value = entry.getValue();
            if (value == null || !Double.isFinite(value)) {
                throw new IllegalArgumentException(
                        key + ": attribute " + entry.getKey() + " must be a finite number");
            }
        }
        attributes = Map.copyOf(copy);
    }

    /** Der Wert dieses Attributs, oder {@code fallback}, wenn die Art ihn nicht nennt. */
    public double attributeOr(Attribute attribute, double fallback) {
        Double value = attributes.get(attribute);
        return value == null ? fallback : value;
    }
}
