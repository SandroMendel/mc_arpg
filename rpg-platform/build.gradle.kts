// T004: Paper adapter layer. May depend on core, never the other way round
// (Constitution III.2: plugin -> platform -> core).
//
// DEVIATION from tasks.md T004 (paperweight-userdev): B01 uses only the public Paper API
// and no NMS/internals, so the plain `paper-api` artifact is sufficient. paperweight-userdev
// exists to remap against Mojang mappings for NMS access, which Constitution VI restricts to
// documented, single-point-of-encapsulation cases. Adding it here would pull a heavy dev-bundle
// toolchain into the build for capabilities B01 must not use. Introduce it in the block that
// genuinely needs NMS, not in the foundation.
dependencies {
    api(project(":rpg-core"))
    compileOnly(libs.paper.api)
    // Paper ships SnakeYAML itself (paper-api declares it at compile scope), so it must NOT be
    // bundled - a second copy in the plugin jar is exactly the classloader conflict research.md
    // warns about in a shared Bukkit process.
    compileOnly(libs.snakeyaml)

    testImplementation(libs.paper.api)
    testImplementation(libs.mockbukkit)
    testImplementation(libs.snakeyaml)
}
