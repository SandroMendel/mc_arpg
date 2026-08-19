rootProject.name = "vuntex-rpg"

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/") {
            name = "papermc"
        }
    }
}

// B01 - Core & Platform: the five modules defined in plan.md.
// Dependency direction is enforced by the Gradle module graph: plugin -> platform -> core.
include(
    ":rpg-core",
    ":rpg-persistence",
    ":rpg-platform",
    ":rpg-content",
    ":rpg-plugin",
)
