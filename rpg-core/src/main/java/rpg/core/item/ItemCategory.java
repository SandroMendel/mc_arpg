package rpg.core.item;

/**
 * Wovon ein Gegenstand eine Art ist (FR-017).
 *
 * <p><b>Zwei Werte, und das ist die kurze Fassung der Geschichte dieses Blocks.</b> Der
 * Blocksteckbrief kannte einmal vier Kategorien: Ausruestung, Aufstiegsmaterial, Verbrauchbares
 * und Kosmetik. Zwei davon sind unterwegs verlorengegangen, und beide aus einem guten Grund:
 *
 * <ul>
 *   <li><b>Ausruestung</b> ist seit ADR-017 Klassenprogression. Je Klasse eine feste Leiter mit
 *       festen Werten in {@code classes.yml}, geliefert von B07. Es gibt hier keine
 *       Ruestungsvorlage und keine Waffenvorlage - nicht als Beute, nicht als Ware, nicht als
 *       Konfiguration (FR-081).
 *   <li><b>Aufstiegsmaterial</b> war der Platzhalter fuer die Frage, wer den Stufenaufstieg
 *       bezahlt. ADR-027 hat sie mit <em>Coins</em> beantwortet, und B08b hat es gebaut
 *       ({@code EquipmentPurchase}). Damit hat die Kategorie keinen Treiber mehr (ADR-039).
 * </ul>
 *
 * <p>Was uebrig ist, ist genau das, was ausserhalb der Klassenleiter noch existiert - und deshalb
 * gibt es keine dritte Konstante. Wer eine braucht, hat vermutlich etwas gefunden, das anderswo
 * hingehoert.
 */
public enum ItemCategory {

    /**
     * Traenke und Nahrung. Wirkt sofort oder ueber {@code SourceKind.BUFF}, wird beim Benutzen
     * verbraucht (FR-033, FR-034).
     */
    CONSUMABLE,

    /**
     * Trimfarben fuer die Klassenruestung. Aendert das Aussehen und <b>keinen einzigen Wert</b>
     * (FR-070), und ist erst auf der Hoechststufe der Leiter anwendbar (FR-069) - sonst waeren
     * Schurken- und Kriegerstufen optisch ununterscheidbar, was B07s FR-016 verbietet.
     */
    COSMETIC
}
