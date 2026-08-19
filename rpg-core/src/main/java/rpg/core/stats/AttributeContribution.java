package rpg.core.stats;

/**
 * One source's share of one attribute value (FR-010).
 *
 * <p>Exists so an admin tool (B14) or anyone debugging a wrong number can ask "where does this come
 * from" and get an answer, without triggering a recalculation to find out.
 *
 * @param source who contributes
 * @param operation how it enters the formula
 * @param value the raw contribution, before any clamping
 */
public record AttributeContribution(
        SourceId source, ModifierOperation operation, double value) {}
