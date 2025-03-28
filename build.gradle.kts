import java.util.*
import org.openapitools.generator.gradle.plugin.tasks.GenerateTask

plugins {
    `java-library`
    `maven-publish`
    id("org.openapi.generator") version "7.2.0"
}

repositories {
    mavenLocal()
    mavenCentral()
}

val keycloakVersion: String by project
val testContainersVersion: String by project
val testContainersKeycloakVersion: String by project
val restAssuredVersion: String by project
val junitVersion: String by project
val awaitilityVersion: String by project

dependencies {
    implementation(enforcedPlatform("org.keycloak.bom:keycloak-bom-parent:$keycloakVersion"))
    compileOnly("org.keycloak:keycloak-services:$keycloakVersion")

    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.junit.jupiter:junit-jupiter:$junitVersion")

    testImplementation("org.testcontainers:testcontainers:$testContainersVersion")
    testImplementation("org.testcontainers:junit-jupiter:$testContainersVersion")
    testImplementation("com.github.dasniko:testcontainers-keycloak:$testContainersKeycloakVersion")
    testImplementation("org.awaitility:awaitility:$awaitilityVersion")
}

group = "fi.metatavu.keycloak.scim.server"

java {
    version = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
    sourceCompatibility = JavaVersion.VERSION_21

    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

sourceSets["main"].java {
    srcDir("build/generated/scim-models/src/main/java/fi/metatavu/keycloak/scim/server/model")
}

sourceSets["test"].java {
    srcDir("build/generated/scim-client/src/main/java")
}

val generateModels = tasks.register("generateModels", GenerateTask::class) {
    setProperty("generatorName", "java")
    setProperty("library", "native")
    setProperty("inputSpec", "$rootDir/scim-openapi.yaml")
    setProperty("outputDir", "$buildDir/generated/scim-models")
    setProperty("modelPackage", "${project.group}.model")

    this.configOptions.put("dateLibrary", "string")
    this.configOptions.put("collectionType", "array")
    this.configOptions.put("serializationLibrary", "jackson")
    this.configOptions.put("enumPropertyNaming", "UPPERCASE")
    this.configOptions.put("openApiNullable", "false")
    this.configOptions.put("useJakartaEe", "true")
}

val generateScimClient = tasks.register("generateScimClient",GenerateTask::class){
    setProperty("generatorName", "java")
    setProperty("library", "native")
    setProperty("inputSpec",  "$rootDir/scim-openapi.yaml")
    setProperty("outputDir", "$buildDir/generated/scim-client")
    setProperty("apiPackage", "${project.group}.test.client.api")
    setProperty("modelPackage", "${project.group}.test.client.model")

    this.configOptions.put("dateLibrary", "string")
    this.configOptions.put("collectionType", "array")
    this.configOptions.put("serializationLibrary", "jackson")
    this.configOptions.put("enumPropertyNaming", "UPPERCASE")
    this.configOptions.put("openApiNullable", "false")
    this.configOptions.put("useJakartaEe", "true")
}

tasks.named("compileJava") {
    dependsOn(generateModels)
}

tasks.named("compileTestJava") {
    dependsOn(generateScimClient)
}

tasks.named<Test>("test") {
    environment("BUILD_DIR", getLayout().buildDirectory.asFile.get().absolutePath)
    useJUnitPlatform()
}

tasks.register("nextReleaseVersion") {
    val propsFile = project.rootDir.resolve("gradle.properties")

    doLast {
        val props = Properties().apply {
            load(propsFile.reader())
        }

        val currentVersion = props.getProperty("version")
        println("currentVersion: $currentVersion")

        if (!currentVersion.endsWith("-SNAPSHOT")) {
            println("Current version is not a snapshot version.")
        } else {
            val newVersion = currentVersion.substring(0, currentVersion.length - "-SNAPSHOT".length)
            props.setProperty("version", newVersion)
            props.store(propsFile.writer(), null)
            println("Version updated to: $newVersion")
        }
    }
}

tasks.register("nextDevelopVersion") {
    val propsFile = project.rootDir.resolve("gradle.properties")

    doLast {
        val props = Properties().apply {
            load(propsFile.reader())
        }

        val currentVersion = props.getProperty("version")
        println("currentVersion: $currentVersion")

        if (!currentVersion.endsWith("-SNAPSHOT")) {
            println("Current version is not a snapshot version.")
        } else {
            val newVersion = currentVersion.substring(0, currentVersion.length - "-SNAPSHOT".length) + "-develop"
            props.setProperty("version", newVersion)
            props.store(propsFile.writer(), null)
            println("Version updated to: $newVersion")
        }
    }
}

tasks.register("nextSnapshotVersion") {
    val propsFile = project.rootDir.resolve("gradle.properties")

    doLast {
        val props = Properties().apply {
            load(propsFile.reader())
        }

        val currentVersion = props.getProperty("version")
        val baseVersion = if (!currentVersion.endsWith("-SNAPSHOT")) {
            currentVersion
        } else {
            currentVersion.substring(0, currentVersion.length - "-SNAPSHOT".length)
        }

        println("currentVersion: $currentVersion")

        val versionComponents = baseVersion.split('.').map { it.toInt() }.toMutableList()
        if (versionComponents.size >= 3) {
            versionComponents[2] = versionComponents[2] + 1
            val newVersion = versionComponents.joinToString(".") + "-SNAPSHOT"
            props.setProperty("version", newVersion)
            props.store(propsFile.writer(), null)
            println("Version updated to: $newVersion")
        } else {
            println("Invalid version format")
        }
    }
}

publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/Metatavu/keycloak-scim-server")
            credentials {
                username = System.getenv("USERNAME")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
    publications {
        register<MavenPublication>("gpr") {
            artifact(tasks["jar"])
        }
    }
}

