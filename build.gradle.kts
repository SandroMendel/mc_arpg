plugins {
    alias(libs.plugins.spotless) apply false
}

// Shared configuration for every module. Applied to subprojects only; the root project
// carries no production code.
subprojects {
    apply(plugin = "java-library")
    apply(plugin = "com.diffplug.spotless")

    group = "rpg"
    version = "0.1.0-SNAPSHOT"

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(rootProject.libs.versions.java.get()))
        }
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        // -Xlint:-processing: with several annotation processors javac reports "annotations not
        // claimed" in its final round even though the code was generated. Verified false positive.
        options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-processing", "-parameters"))
    }

    dependencies {
        // Lombok and MapStruct are compile-time only: Lombok rewrites the AST during
        // compilation, MapStruct generates plain Java mapper implementations. Neither leaves a
        // runtime dependency behind - which is what keeps the property established in B01 intact
        // that the shipped plugin jar contains no third-party classes.
        //
        // MapStruct stays compileOnly on purpose: generated *Impl classes are instantiated
        // directly (or wired through the module registry), never through Mappers.getMapper(),
        // which is the one API that would pull the runtime jar in. That also matches B01's
        // decision against reflection-based wiring.
        //
        // The binding artifact is required whenever both processors run on the same source set:
        // it makes MapStruct wait for Lombok-generated accessors instead of racing them.
        "compileOnly"(rootProject.libs.lombok)
        "annotationProcessor"(rootProject.libs.lombok)
        "compileOnly"(rootProject.libs.mapstruct)
        "annotationProcessor"(rootProject.libs.mapstruct.processor)
        "annotationProcessor"(rootProject.libs.lombok.mapstruct.binding)

        "testCompileOnly"(rootProject.libs.lombok)
        "testAnnotationProcessor"(rootProject.libs.lombok)
        "testCompileOnly"(rootProject.libs.mapstruct)
        "testAnnotationProcessor"(rootProject.libs.mapstruct.processor)
        "testAnnotationProcessor"(rootProject.libs.lombok.mapstruct.binding)

        "testImplementation"(platform(rootProject.libs.junit.bom))
        "testImplementation"(rootProject.libs.junit.jupiter)
        "testRuntimeOnly"(rootProject.libs.junit.platform.launcher)
        "testImplementation"(rootProject.libs.assertj.core)
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
        }
    }

    // T007: formatting/linting. Only engine-free steps are used so the build stays
    // reproducible without downloading an external formatter distribution.
    extensions.configure<com.diffplug.gradle.spotless.SpotlessExtension> {
        java {
            target("src/**/*.java")
            importOrder("java", "javax", "org", "com", "")
            trimTrailingWhitespace()
            leadingTabsToSpaces(4)
            endWithNewline()
        }
        kotlinGradle {
            target("*.gradle.kts")
            trimTrailingWhitespace()
            endWithNewline()
        }
    }
}
