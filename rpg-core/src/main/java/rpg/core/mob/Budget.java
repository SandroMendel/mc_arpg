package rpg.core.mob;

/**
 * Die vier harten Obergrenzen fuer gleichzeitig lebende Kreaturen (FR-013).
 *
 * <p><b>Eine Grenze, kein Zielwert.</b> Das ist der Unterschied, an dem dieser ganze Block haengt.
 * Die Dichte-Skalierung aus {@link DensityScaling} rechnet aus, wie voll eine Zone <em>sein
 * sollte</em>; das Budget sagt, wie voll sie <em>hoechstens sein darf</em>. Beide Zahlen stehen
 * nebeneinander, und ihre Rollen duerfen nicht verschwimmen - sonst waere ein ploetzlicher
 * Spieleransturm ein Weg, die Zusage zu ueberschreiten.
 *
 * <p><b>Die schaerfste entscheidet.</b> Ein voller Chunk nimmt nichts mehr, auch wenn die Zone noch
 * Platz hat, und umgekehrt.
 *
 * <p><b>Und {@code serverWide} gilt auch gegen die Summe der Zonen</b> (FR-013a). Sechs Regionen zu
 * je 130 sind 780 und bleiben darunter - aber niemand hindert einen Betreiber daran, jede Zone auf
 * 200 zu stellen, und dann waeren es 1.200. Die serverweite Grenze ist der Ort, an dem der Zielwert
 * aus dem M4-Nachweis wirklich haengt; ohne sie stuende er in der Vision und wuerde nirgends
 * eingehalten. Deshalb ist es kein Konfigurationsfehler, wenn die Summe darueber liegt: es ist eine
 * legitime Verteilung, solange nie alle gleichzeitig stehen.
 *
 * @param serverWide ueber alle Zonen zusammen
 * @param perZone je Region
 * @param perChunk je Chunk - was auf 16x16 Bloecken noch nicht ineinandersteht
 * @param perPlayer je anwesendem Spieler - was ein Spieler noch ueberblickt
 */
public record Budget(int serverWide, int perZone, int perChunk, int perPlayer) {

    public Budget {
        require(serverWide, "budget.server-wide");
        require(perZone, "budget.per-zone");
        require(perChunk, "budget.per-chunk");
        require(perPlayer, "budget.per-player");
        if (perChunk > perZone) {
            throw new IllegalArgumentException(
                    "budget.per-chunk ("
                            + perChunk
                            + ") must not exceed budget.per-zone ("
                            + perZone
                            + ") - a limit a narrower one can never reach is a number without"
                            + " effect");
        }
    }

    private static void require(int value, String where) {
        if (value <= 0) {
            throw new IllegalArgumentException(
                    where
                            + " must be positive, but was "
                            + value
                            + " - a budget of zero means 'no horde' and belongs left out, not"
                            + " configured");
        }
    }

    /**
     * Ob noch eine Kreatur dazu darf.
     *
     * <p>Alle vier Fragen, und die erste, die nein sagt, entscheidet. Die Reihenfolge ist von der
     * billigsten zur teuersten gewaehlt, aber das Ergebnis haengt nicht davon ab.
     *
     * @param serverTotal wie viele dieser Block insgesamt haelt
     * @param zoneTotal wie viele in dieser Zone stehen
     * @param chunkTotal wie viele in diesem Chunk stehen
     * @param playersInZone wie viele Spieler in dieser Zone sind - null Spieler heisst null erlaubt
     */
    public boolean allows(int serverTotal, int zoneTotal, int chunkTotal, int playersInZone) {
        if (playersInZone <= 0) {
            // FR-015: in einer Zone ohne Spieler entsteht nichts. Hier und nicht erst im
            // Durchlauf, damit die Regel an einer Stelle steht statt an zweien.
            return false;
        }
        return serverTotal < serverWide
                && zoneTotal < perZone
                && chunkTotal < perChunk
                && zoneTotal < playersInZone * perPlayer;
    }

    /**
     * Die Obergrenze fuer diese Zone bei so vielen Spielern - die Zahl, an der die Skalierung
     * gekappt wird (FR-027).
     */
    public int ceilingFor(int playersInZone, int serverTotalElsewhere) {
        if (playersInZone <= 0) {
            return 0;
        }
        int byPlayers = playersInZone * perPlayer;
        int byServer = Math.max(0, serverWide - serverTotalElsewhere);
        return Math.min(Math.min(perZone, byPlayers), byServer);
    }
}
