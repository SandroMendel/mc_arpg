package rpg.core.mob;

import java.time.Duration;
import java.util.Objects;

/**
 * Der Boss einer Region: welche Art, wo er steht, und wie lange es dauert, bis er wiederkommt.
 *
 * <p><b>Ein Bereichsschluessel und ein Versatz - keine Weltkoordinaten.</b> B09 liefert je Region
 * benannte Bereiche und weigert sich ausdruecklich, einen davon als Bossbereich zu kennzeichnen:
 * <em>„ein Bossbereich unterscheidet sich geometrisch von keinem anderen; er unterscheidet sich in
 * dem, was darin steht"</em>. Genau dieses „was darin steht" ist diese Klasse. Ueber den
 * Bereichsschluessel zu gehen haelt die Geometrie an einer Stelle.
 *
 * <p><b>Und mehr ist ein Boss hier nicht.</b> Keine eigenen Faehigkeiten, keine Phasenwechsel - was
 * ihn ausmacht, sind hoehere Zahlen in seiner {@link MobKind} und der Timer hier. Das ist ehrlich
 * benannt: es ist ein dicker Mob. Der eigentliche Bosskampf kommt spaeter als Dungeon-Boss, braucht
 * eine Instanzwelt und gehoert nicht in diesen Block.
 *
 * @param kindKey eine Art mit {@code boss: true}
 * @param areaKey ein Bereichsschluessel derselben Zone
 * @param offsetX Versatz im Bereich
 * @param offsetY Versatz im Bereich
 * @param offsetZ Versatz im Bereich
 * @param respawn wie lange nach seinem Tod, bis er wieder darf
 */
public record BossSpec(
        String kindKey,
        String areaKey,
        double offsetX,
        double offsetY,
        double offsetZ,
        Duration respawn) {

    public BossSpec {
        Objects.requireNonNull(kindKey, "kindKey");
        Objects.requireNonNull(areaKey, "areaKey");
        Objects.requireNonNull(respawn, "respawn");
        if (kindKey.isBlank() || areaKey.isBlank()) {
            throw new IllegalArgumentException("boss kind and area must not be blank");
        }
        if (!Double.isFinite(offsetX) || !Double.isFinite(offsetY) || !Double.isFinite(offsetZ)) {
            throw new IllegalArgumentException(kindKey + ": boss offset must be finite");
        }
        if (respawn.isZero() || respawn.isNegative()) {
            throw new IllegalArgumentException(
                    kindKey
                            + ": respawn must be positive, but was "
                            + respawn.toSeconds()
                            + "s - a boss that returns instantly is not an event");
        }
    }
}
