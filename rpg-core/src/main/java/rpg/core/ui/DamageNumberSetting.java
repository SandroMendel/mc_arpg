package rpg.core.ui;

import java.time.Duration;
import java.util.Objects;

/**
 * Die Einstellungen der Schadenszahlen (FR-043, FR-045).
 *
 * <p><b>Die Lebensdauer ist der wichtigste Wert dieses Blocks.</b> Schadenszahlen sind Entities in
 * der Welt — die einzige Stelle, an der B13 überhaupt etwas hineinschreibt. Sie sind ausdrücklich
 * <b>nicht persistent</b>: was der Server nicht speichert, kann ein Absturz nicht zurücklassen, und
 * das ist der ganze Inhalt von SC-009.
 *
 * <p>Das ist die genaue Umkehrung von B12s Hologramm, das persistent sein <em>muss</em> und deshalb
 * vor dem Setzen aufräumt. Eine Schadenszahl, die dasselbe täte, wäre bei 150 Spielern in Minuten
 * tausendfacher Müll, der einen Neustart überlebt.
 *
 * @param enabled ob Schadenszahlen gezeigt werden; abgeschaltet heißt kostenlos (FR-045)
 * @param lifetime wie lange eine Zahl steht — positiv
 * @param offset Höhe über dem Trefferpunkt in Blöcken — endlich
 */
public record DamageNumberSetting(boolean enabled, Duration lifetime, double offset) {

    public DamageNumberSetting {
        Objects.requireNonNull(lifetime, "lifetime");
        if (lifetime.isZero() || lifetime.isNegative()) {
            throw new IllegalArgumentException(
                    "lifetime ist " + lifetime + " - erlaubt ist groesser als null");
        }
        if (!Double.isFinite(offset)) {
            throw new IllegalArgumentException("offset ist " + offset + " - erlaubt ist endlich");
        }
    }
}
