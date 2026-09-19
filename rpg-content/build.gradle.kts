// B16 content module. Definitions, versioned defaults and server-free policy/contracts live here;
// YAML parsing and Paper runtime wiring remain in rpg-platform/rpg-plugin.
dependencies {
    api(project(":rpg-core"))
    // T028 fixture parsing must exercise the canonical production YAML loader owned by rpg-platform.
    testImplementation(project(":rpg-platform"))
    // Fixture comparison only: production content remains parser-free and the runtime parser
    // stays owned by rpg-platform.
    testImplementation(libs.snakeyaml)
}
