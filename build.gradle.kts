import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.2.20"
    id("com.gradleup.shadow") version "9.4.2"
}

group = "com.wimbli.WorldBorder"
version = "2.0.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc"
    }
    maven("https://repo.mikeprimm.com/") {
        name = "dynmap"
    }
    maven("https://repo.extendedclip.com/releases/") {
        name = "placeholderapi"
    }
}

dependencies {
    // Server API (provided at runtime by the server, hence compileOnly)
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")

    // Optional soft-dependency, only used by DynMapFeatures. The API surface we use is stable, so the
    // proven 2.5 artifact compiles fine and stays binary-compatible with modern Dynmap 3.x at runtime.
    // isTransitive = false drops its ancient org.bukkit:bukkit:1.7.10 transitive dep, which would
    // otherwise conflict with paper-api's bukkit capability.
    compileOnly("us.dynmap:dynmap-api:2.5") {
        isTransitive = false
    }

    // Optional soft-dependency: PlaceholderAPI. Only referenced at runtime when the server has it installed.
    compileOnly("me.clip:placeholderapi:2.11.6")

    // bStats metrics — shaded into the jar and relocated (bStats refuses to start unless relocated).
    implementation("org.bstats:bstats-bukkit:3.2.1")

    // Kotlin stdlib is added automatically by the kotlin("jvm") plugin and
    // is shaded into the final jar (see shadowJar relocation below).

    // Unit tests (the Bukkit/Adventure API is provided on the test classpath here so BorderData loads)
    testImplementation("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// The kotlin("jvm") plugin also applies the Java plugin; keep its (no-op) compileJava task on the same
// bytecode target as Kotlin so the JVM-target consistency check passes when Gradle runs on a newer JDK.
java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
    compilerOptions {
        // Paper 1.21 runs on Java 21; emit 21 bytecode regardless of the JDK
        // used to run Gradle.
        jvmTarget = JvmTarget.JVM_21
    }
}

tasks {
    test {
        useJUnitPlatform()
    }

    processResources {
        // Inject the project version into plugin.yml at build time
        val props = mapOf("version" to project.version.toString())
        inputs.properties(props)
        filteringCharset = "UTF-8"
        filesMatching("plugin.yml") {
            expand(props)
        }
    }

    shadowJar {
        archiveClassifier.set("")
        // Relocate the bundled Kotlin stdlib so this plugin never clashes with
        // another Kotlin plugin shipping a different stdlib version.
        relocate("kotlin", "com.wimbli.WorldBorder.libs.kotlin")
        relocate("org.bstats", "com.wimbli.WorldBorder.libs.bstats")
        mergeServiceFiles()
    }

    // `gradle build` should produce the runnable (shaded) jar
    build {
        dependsOn(shadowJar)
    }

    jar {
        // The plain jar is redundant once shadowJar runs; keep classifier so the
        // shaded artifact is the canonical "WorldBorder.jar".
        archiveClassifier.set("plain")
    }
}
