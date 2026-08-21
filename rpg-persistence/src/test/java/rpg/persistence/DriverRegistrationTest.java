package rpg.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.zaxxer.hikari.HikariConfig;

import rpg.core.persistence.PersistenceConfig;

/**
 * Guards the one thing every other persistence test cannot see.
 *
 * <p>On the test classpath the PostgreSQL driver sits on the system classloader, where
 * {@code DriverManager} finds it by itself - so every test that opens a real connection passes
 * whether or not the driver is named explicitly. On a Paper server the driver comes from the
 * {@code libraries:} section of plugin.yml into a classloader {@code DriverManager} never scanned,
 * and the start died with "No suitable driver". These tests assert the configuration instead of the
 * connection, because that is the only difference the two environments actually have.
 */
class DriverRegistrationTest {

    private static PersistenceConfig config() {
        return new PersistenceConfig(
                "localhost",
                5432,
                "vuntex",
                "vuntex",
                "secret",
                8,
                4,
                Duration.ofSeconds(45),
                50_000,
                Duration.ofSeconds(8));
    }

    @Test
    @DisplayName("the pool names the driver class instead of relying on DriverManager")
    void poolNamesTheDriverClass() {
        HikariConfig hikari = ConnectionPools.poolConfig(config(), "rpg-write", 8);

        assertThat(hikari.getDriverClassName())
                .as(
                        "without this Hikari falls back to DriverManager.getDriver(url), which"
                                + " cannot see a driver loaded from Paper's library classloader")
                .isEqualTo("org.postgresql.Driver");
    }

    @Test
    @DisplayName("the named driver class exists and can be instantiated")
    void namedDriverClassResolves() throws Exception {
        Class<?> driver = Class.forName(ConnectionPools.DRIVER_CLASS);

        assertThat(java.sql.Driver.class.isAssignableFrom(driver)).isTrue();
        assertThat(driver.getDeclaredConstructor().newInstance()).isInstanceOf(java.sql.Driver.class);
    }

    @Test
    @DisplayName("both pools carry the same driver and the settings the block promised")
    void bothPoolsAreConfiguredAlike() {
        HikariConfig write = ConnectionPools.poolConfig(config(), "rpg-write", 8);
        HikariConfig login = ConnectionPools.poolConfig(config(), "rpg-login", 4);

        assertThat(login.getDriverClassName()).isEqualTo(write.getDriverClassName());
        assertThat(write.getPoolName()).isEqualTo("rpg-write");
        assertThat(login.getPoolName()).isEqualTo("rpg-login");
        assertThat(write.getMaximumPoolSize()).isEqualTo(8);
        assertThat(login.getMaximumPoolSize()).isEqualTo(4);
        // Keeping the pool warm is what FR-008 is about: no connection opened on the login path.
        assertThat(login.getMinimumIdle()).isEqualTo(4);
        assertThat(write.isAutoCommit()).isFalse();
        assertThat(write.getJdbcUrl()).isEqualTo("jdbc:postgresql://localhost:5432/vuntex");
    }
}
