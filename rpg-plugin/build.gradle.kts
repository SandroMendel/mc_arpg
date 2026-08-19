// T005: bootstrap module. Wires everything together; depends on all other modules.
// Direction is one-way only: plugin -> platform -> core (Constitution III.2).
dependencies {
    implementation(project(":rpg-platform"))
    implementation(project(":rpg-persistence"))
    implementation(project(":rpg-content"))
    implementation(project(":rpg-core"))
    compileOnly(libs.paper.api)

    testImplementation(libs.paper.api)
    testImplementation(libs.mockbukkit)

    // The full bootstrap is the one thing only this module can prove: modules starting in the right
    // order, listeners registered, configuration and message keys resolving. All of that needs a
    // real database, so the test starts the same container rpg-persistence uses.
    testImplementation(testFixtures(project(":rpg-persistence")))
    testRuntimeOnly(libs.postgresql)
    testRuntimeOnly(libs.hikaricp)
    testRuntimeOnly(libs.flyway.core)
    testRuntimeOnly(libs.flyway.postgresql)
}

// plugin.yml carries the project version, so Paper reports the same version the build produced.
tasks.named<ProcessResources>("processResources") {
    val pluginVersion = project.version.toString()
    inputs.property("version", pluginVersion)
    filesMatching("plugin.yml") {
        expand("version" to pluginVersion)
    }
}

// plugin.yml is read by MockBukkit when it loads the plugin class in tests
tasks.named<Test>("test") {
    dependsOn(tasks.named("processResources"))
}

// The deployable artifact. A Paper plugin is loaded as ONE jar, so rpg-core, rpg-platform,
// rpg-persistence and rpg-content have to travel inside it - otherwise the server cannot resolve
// rpg.core.* at load time.
//
// Only this project's own modules are bundled. Paper already provides the Paper API and SnakeYAML,
// so no third-party class ends up in the jar and there is nothing to relocate or shade - which is
// what keeps us clear of the classloader conflicts research.md flags for a shared Bukkit process.
tasks.named<Jar>("jar") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
    // The runtime classpath is resolved lazily, which hides the producing jar tasks from Gradle's
    // dependency graph - declare them so a clean build orders the module jars before this one.
    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) }
    })
}
