package rpg.core.statistics;

/**
 * Wie eine Einlösung ausgegangen ist — <b>vier Ausgänge, und jeder braucht eine eigene Antwort</b>
 * (contracts/stats-api.md §4).
 *
 * <p>Drei davon sehen für einen Spieler zunächst gleich aus: „ich habe nichts bekommen". Sie
 * bedeuten aber völlig Verschiedenes, und wer sie zusammenfasst, nimmt ihm die einzige
 * Information, die ihm weiterhilft.
 */
public enum ClaimOutcome {

    /** Gutgeschrieben. Der Anspruch ist verbraucht. */
    CLAIMED,

    /** Es gibt keinen Anspruch für diese Saison — der Spieler stand nicht auf einem Platz. */
    NOTHING_TO_CLAIM,

    /**
     * Bereits eingelöst.
     *
     * <p>Unterscheidet sich von {@link #NOTHING_TO_CLAIM} in dem, was der Spieler daraus lernt:
     * dass er <em>hatte</em>, nicht dass er nie hatte. Ohne die Unterscheidung meldet der Erste,
     * der zweimal klickt, seinen verschwundenen Anspruch als Fehler.
     */
    ALREADY_CLAIMED,

    /**
     * Kein Platz im Inventar — <b>der Anspruch bleibt offen</b> (FR-055).
     *
     * <p>Und er wird <b>gar nicht erst markiert</b>: die Prüfung auf Platz steht vor dem bedingten
     * Update. Andersherum wäre der Anspruch verbraucht und die Belohnung nirgends — der eine Fall,
     * den die Reihenfolge „markieren, dann gutschreiben" nicht abdeckt, weil er vorhersehbar ist
     * und deshalb vorher geprüft gehört.
     */
    INVENTORY_FULL
}
