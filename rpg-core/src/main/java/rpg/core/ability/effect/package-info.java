/**
 * The effect primitives - one stateless application per {@link rpg.core.ability.EffectType}.
 *
 * <p><b>None of these knows which ability it belongs to.</b> It receives a spec, a caster and a
 * target and does one thing. That is what makes a new ability a configuration entry: sixteen small
 * pieces that compose, rather than eighteen classes that each do everything once.
 *
 * <p>Periodic effects are not a separate primitive. An {@link rpg.core.ability.EffectSpec} with an
 * interval simply applies repeatedly over its duration - damage over time is {@code DAMAGE} with an
 * interval, the mana potion is {@code MANA_RESTORE} with one. All running instances share a single
 * sweep; one per target would be a recurring task per entity and would break Constitution II, which
 * is exactly why damage over time was refused in its first shape and accepted in this one (ADR-025).
 *
 * <p>An exception thrown out of an application is caught, logged with the ability's id and confined
 * to that one event - the same barrier B01 uses for modules, B04 for base stat contributors and B05
 * for interceptors (FR-017).
 */
package rpg.core.ability.effect;
