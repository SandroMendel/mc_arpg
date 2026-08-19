// B02: the JDBC side of persistence. Depends on core, never the other way round
// (Constitution III.2: plugin -> persistence -> core).
//
// Deliberately NO Paper/Bukkit dependency: the whole module is verifiable against a real
// PostgreSQL instance without starting a Minecraft server.
//
// Driver, pool and Flyway are compileOnly because Paper resolves them at runtime from the
// `libraries:` section of plugin.yml (ADR-010). Shading them would put a second JDBC driver into
// a shared Bukkit classloader, which is the conflict research.md warns about.
// The PostgreSQL test container is shared with rpg-plugin, which needs a real database to prove the
// full bootstrap works. Test fixtures are the sanctioned way to share test-only code between
// modules; the alternative - a second copy of the container setup - would drift.
plugins {
    `java-test-fixtures`
}

dependencies {
    api(project(":rpg-core"))

    // api, not implementation: consumers of the fixture call PostgreSQLContainer methods through it.
    testFixturesApi(libs.testcontainers.postgresql)
    testFixturesApi(libs.postgresql)

    compileOnly(libs.postgresql)
    compileOnly(libs.hikaricp)
    compileOnly(libs.flyway.core)
    compileOnly(libs.flyway.postgresql)

    testImplementation(libs.postgresql)
    testImplementation(libs.hikaricp)
    testImplementation(libs.flyway.core)
    testImplementation(libs.flyway.postgresql)

    // Only the core Testcontainers module - NOT `junit-jupiter`. The extension is built against
    // JUnit 5 while this project runs Jupiter 6, and an incompatibility there would surface as a
    // silently *skipped* test rather than a failing one (see B01, MockBukkit). The singleton
    // container in test/support sidesteps the question entirely.
    testImplementation(libs.testcontainers.postgresql)
}
