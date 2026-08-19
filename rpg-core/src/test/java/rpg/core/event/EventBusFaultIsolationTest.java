package rpg.core.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * T027 / FR-006a: a subscriber throwing must not deprive the other subscribers of the same event, and
 * must not make {@code publish} itself fail.
 */
class EventBusFaultIsolationTest {

    /** Domain event carrying its publisher for diagnostics. */
    record DamageDealt(String publishedByModuleId, double amount) implements Event {}

    record Unrelated(String publishedByModuleId) implements Event {}

    private DefaultEventBus bus;

    @BeforeEach
    void setUp() {
        Logger logger = Logger.getLogger(EventBusFaultIsolationTest.class.getName());
        logger.setLevel(Level.OFF); // the assertion is about delivery, not about the log line
        bus = new DefaultEventBus(logger);
    }

    @Test
    void everySubscriberIsNotifiedEvenWhenOneOfThemThrows() {
        List<String> notified = new ArrayList<>();
        bus.subscribe(DamageDealt.class, event -> notified.add("first"));
        bus.subscribe(
                DamageDealt.class,
                event -> {
                    notified.add("throwing");
                    throw new IllegalStateException("subscriber is broken");
                });
        bus.subscribe(DamageDealt.class, event -> notified.add("third"));

        assertThatCode(() -> bus.publish(new DamageDealt("combat", 12.5d)))
                .doesNotThrowAnyException();

        assertThat(notified).containsExactlyInAnyOrder("first", "throwing", "third");
    }

    @Test
    void allSubscribersMayThrowWithoutFailingThePublisher() {
        bus.subscribe(
                DamageDealt.class,
                event -> {
                    throw new IllegalStateException("a");
                });
        bus.subscribe(
                DamageDealt.class,
                event -> {
                    throw new IllegalStateException("b");
                });

        assertThatCode(() -> bus.publish(new DamageDealt("combat", 1d))).doesNotThrowAnyException();
    }

    @Test
    void subscribersOfOtherEventTypesAreNotNotified() {
        List<String> notified = new ArrayList<>();
        bus.subscribe(DamageDealt.class, event -> notified.add("damage"));
        bus.subscribe(Unrelated.class, event -> notified.add("unrelated"));

        bus.publish(new DamageDealt("combat", 3d));

        assertThat(notified).containsExactly("damage");
    }

    @Test
    void publisherAndSubscriberNeverReferenceEachOther() {
        // The subscriber below names only the event type. Nothing in this test refers to a publishing
        // module's class, which is the decoupling FR-006 asks for.
        List<Double> received = new ArrayList<>();
        bus.subscribe(DamageDealt.class, event -> received.add(event.amount()));

        bus.publish(new DamageDealt("combat", 42d));

        assertThat(received).containsExactly(42d);
    }

    @Test
    void closingASubscriptionStopsFurtherDelivery() {
        List<String> notified = new ArrayList<>();
        Subscription subscription = bus.subscribe(DamageDealt.class, event -> notified.add("hit"));

        bus.publish(new DamageDealt("combat", 1d));
        subscription.close();
        bus.publish(new DamageDealt("combat", 2d));

        assertThat(notified).containsExactly("hit");
        assertThat(bus.subscriberCount(DamageDealt.class)).isZero();
    }

    @Test
    void closingASubscriptionTwiceIsANoOp() {
        Subscription first = bus.subscribe(DamageDealt.class, event -> {});
        Subscription second = bus.subscribe(DamageDealt.class, event -> {});

        first.close();
        first.close();

        assertThat(bus.subscriberCount(DamageDealt.class)).isEqualTo(1);
        second.close();
        assertThat(bus.subscriberCount(DamageDealt.class)).isZero();
    }

    @Test
    void publishingWithNoSubscribersIsHarmless() {
        assertThatCode(() -> bus.publish(new Unrelated("zones"))).doesNotThrowAnyException();
    }

    @Test
    void aSubscriberUnsubscribingDuringDeliveryDoesNotBreakTheDispatch() {
        List<String> notified = new ArrayList<>();
        List<Subscription> subscriptions = new ArrayList<>();

        subscriptions.add(
                bus.subscribe(
                        DamageDealt.class,
                        event -> {
                            notified.add("self-removing");
                            subscriptions.get(0).close();
                        }));
        subscriptions.add(bus.subscribe(DamageDealt.class, event -> notified.add("second")));

        assertThatCode(() -> bus.publish(new DamageDealt("combat", 1d))).doesNotThrowAnyException();

        assertThat(notified).containsExactlyInAnyOrder("self-removing", "second");
        assertThat(bus.subscriberCount(DamageDealt.class)).isEqualTo(1);
    }
}
