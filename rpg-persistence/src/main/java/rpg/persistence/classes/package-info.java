/**
 * B07 persistence - the reached armor and weapon tier of a character.
 *
 * <p>One table, two payload columns. The class itself already lives in {@code rpg.character} from
 * B03 and is deliberately <b>not</b> repeated here: a second copy would be a second truth.
 *
 * <p>Why an own table instead of two columns on {@code rpg.character}: the same argument B04 used
 * for {@code character_stats} and B06 for {@code character_progress}. A shared row means a shared
 * writer and a shared revision counter, and the block boundary from Constitution III would only
 * exist on paper.
 */
package rpg.persistence.classes;
