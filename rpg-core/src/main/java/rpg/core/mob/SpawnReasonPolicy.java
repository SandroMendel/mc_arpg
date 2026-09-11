package rpg.core.mob;

import java.util.Set;

/**
 * Welcher {@code CreatureSpawnEvent.SpawnReason} durchgelassen wird (FR-018d).
 *
 * <p>Als reine Regel, ohne Bukkit: der Riegel in {@code rpg.platform.mob} fragt hier statt selbst
 * zu entscheiden, damit die Liste der erlaubten Gruende serverlos getestet werden kann. Die
 * Bukkit-Konstanten kommen als Namen herein - dieses Paket sieht {@code SpawnReason} nicht
 * (Prinzip III.1).
 *
 * <p><b>Erlaubt ist genau das absichtliche Setzen</b> (research.md R1): {@code CUSTOM} sind unsere
 * eigenen Kreaturen (FR-018e), {@code COMMAND} ein Betreiber, {@code SPAWNER_EGG} und
 * {@code DISPENSE_EGG} ein Spawn-Ei in der Hand oder aus einem Spender. Alles andere - Vanillas
 * natuerliches Spawnen, Raids, Portale, Verstaerkung, Jockeys und der Rest der gut vierzig
 * {@code SpawnReason}-Werte - wird verworfen.
 */
public final class SpawnReasonPolicy {

    private static final Set<String> ALLOWED = Set.of("CUSTOM", "COMMAND", "SPAWNER_EGG", "DISPENSE_EGG");

    private SpawnReasonPolicy() {}

    /** Ob dieser Grund ein Spawn-Ereignis unangetastet lassen darf. */
    public static boolean isAllowed(String spawnReasonName) {
        return spawnReasonName != null && ALLOWED.contains(spawnReasonName);
    }
}
