/**
 * B12s Datenbankseite: die vier Ranglistensichten, ihr Refresh und die beiden Saisontabellen.
 *
 * <h2>Zwei Regeln, die hier nicht bloß gelten, sondern geprüft werden</h2>
 *
 * <p><b>1. {@code java.sql} lebt nur in diesem Modul.</b> Kein anderer Block nimmt sich eine eigene
 * Verbindung — sonst wäre der getrennte Anmeldepool aus B02 FR-008 wirkungslos, weil jeder daran
 * vorbeigreifen könnte. {@code NoDirectDatabaseAccessTest} prüft das über den Quelltext, nicht über
 * eine Übereinkunft.
 *
 * <p><b>2. Gegen {@code player_statistic_daily} steht nirgends ein {@code DELETE}.</b> B02 FR-017
 * sagt unbegrenzte Aufbewahrung zu, und ADR-044 hat entschieden, die Aufschlüsselung in Zeilen zu
 * bezahlen statt sie zu verdichten. Beides zusammen heißt: die Tabelle wächst, und das ist der
 * Plan. Ein Aufräumauftrag, der sich später leise hereinschleicht, würde genau die Aussagen
 * unmöglich machen, für die die Zeilen angelegt wurden. Derselbe Test hält auch das fest.
 *
 * <h2>Was hier gerade nicht liegt</h2>
 *
 * <p>Die Tagestabelle selbst gehört <b>B02</b>, samt {@code StatisticsRepository} und ihrem Platz
 * in {@code FlushCycle.WRITE_ORDER}. B12 baut sie nicht neu, sondern schließt sich an: der zweite
 * Schreibweg für Höchstwerte (ADR-040) ist ein zweites Statement auf derselben Tabelle, kein
 * zweiter Bestand — und ausdrücklich <b>kein neuer {@code AggregateType}</b> (research.md R10).
 *
 * @see rpg.persistence.stats für B04s Ressourcen — ein anderer Block, ein ähnlicher Name
 */
package rpg.persistence.statistics;
