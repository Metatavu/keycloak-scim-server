import java.util.*
import com.vanniktech.maven.publish.JavaLibrary
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.SonatypeHost
import org.openapitools.generator.gradle.plugin.tasks.GenerateTask

abstract class FixAdditionalPropertyModels : DefaultTask() {

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val inputDir: DirectoryProperty

    @TaskAction
    fun fix() {
        inputDir.get().asFileTree.matching {
            include("**/*.java")
        }.forEach { file ->
            val content = file.readText()
            val fixed = content.replace("public class User extends HashMap<String, Object>", "public class User")
            if (content != fixed) {
                file.writeText(fixed)
            }
        }
    }
}

plugins {
    `java-library`
    `maven-publish`
    signing
    jacoco
    id("org.openapi.generator") version "7.2.0"
    id("org.sonarqube") version "6.2.0.5505"
    id("com.vanniktech.maven.publish") version "0.30.0"
}

repositories {
    mavenLocal()
    mavenCentral()
}

val keycloakVersion: String by project
val testContainersVersion: String by project
val testContainersKeycloakVersion: String by project
val junitVersion: String by project
val awaitilityVersion: String by project
val seleniumRemoteDriverVersion: String by project
val seleniumVersion: String by project
val jacocoVersion: String by project

val jacocoRuntime: Configuration by configurations.creating

dependencies {
    implementation(enforcedPlatform("org.keycloak.bom:keycloak-bom-parent:$keycloakVersion"))
    compileOnly("org.keycloak:keycloak-services:$keycloakVersion")

    testImplementation("org.keycloak:keycloak-services:$keycloakVersion")
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.junit.jupiter:junit-jupiter:$junitVersion")

    testImplementation("org.testcontainers:testcontainers:$testContainersVersion")
    testImplementation("org.testcontainers:junit-jupiter:$testContainersVersion")
    testImplementation("com.github.dasniko:testcontainers-keycloak:$testContainersKeycloakVersion")
    testImplementation("org.testcontainers:selenium:$testContainersVersion")
    testImplementation("org.awaitility:awaitility:$awaitilityVersion")
    testImplementation("org.seleniumhq.selenium:selenium-remote-driver:$seleniumRemoteDriverVersion")
    testImplementation("org.seleniumhq.selenium:selenium-java:$seleniumVersion")

    jacocoRuntime("org.jacoco:org.jacoco.agent:$jacocoVersion:runtime")
}

group = "fi.metatavu.keycloak.scim.server"

java {
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

jacoco {
    toolVersion = jacocoVersion
}

val generateModelsCode = tasks.register("generateModelsCode", GenerateTask::class) {
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
    this.configOptions.put("additionalModelTypeAnnotations", "@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)")
}

val scimModelsOutputDir = layout.projectDirectory.dir("build/generated/scim-models/src/main/java")
val generateModels = tasks.register<FixAdditionalPropertyModels>("generateModels") {
    dependsOn(generateModelsCode)
    group = "build"
    description = "Fixes SCIM model classes after OpenAPI generation"
    inputDir.set(scimModelsOutputDir)
}

val scimClientOutputDir = layout.projectDirectory.dir("build/generated/scim-client/src/main/java")
val generateScimClientCode = tasks.register("generateScimClientCode",GenerateTask::class){
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

val generateScimClient = tasks.register<FixAdditionalPropertyModels>("generateScimClient") {
    dependsOn(generateScimClientCode)
    group = "build"
    description = "Fixes SCIM client classes after OpenAPI generation"
    inputDir.set(scimClientOutputDir)
}

tasks.named("compileJava") {
    dependsOn(generateModels)
}

tasks.named("compileTestJava") {
    dependsOn(generateScimClient)
}

tasks.named<Test>("test") {
    val jacocoAgent = configurations["jacocoRuntime"].singleFile

    environment("BUILD_DIR", getLayout().buildDirectory.asFile.get().absolutePath)
    environment("TEST_EVENTS_LISTENER_BUILD_DIR", getLayout().projectDirectory.dir("test-event-listener/build").asFile.absolutePath)
    environment("KEYCLOAK_VERSION", keycloakVersion)
    environment("JACOCO_AGENT", jacocoAgent)

    useJUnitPlatform()
}

tasks.register<JacocoReport>("jacocoIntegrationReport") {
    dependsOn("test")

    val execFiles = fileTree("build/jacoco") {
        include("**/*.exec")
    }

    sourceDirectories.setFrom(files("src/main/java"))
    executionData.setFrom(execFiles)

    classDirectories.setFrom(fileTree("build/classes/java/main") {
        exclude("fi/metatavu/keycloak/scim/server/model/**")
    })

    reports {
        xml.required.set(true)
        html.required.set(true)
    }
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

mavenPublishing {
    configure(JavaLibrary(javadocJar = JavadocJar.Javadoc(), sourcesJar = true))

    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL, automaticRelease = true)

    coordinates(project.group.toString(), "keycloak-scim-server", project.version.toString())

    pom {
        name.set("Keycloak SCIM Server")
        description.set("SCIM 2.0 server extension for Keycloak")
        inceptionYear.set("2024")
        url.set("https://github.com/Metatavu/keycloak-scim-server")

        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("repo")
            }
        }

        developers {
            developer {
                id.set("Metatavu")
                name.set("Metatavu Oy")
                url.set("https://github.com/Metatavu/")
            }
        }

        scm {
            url.set("https://github.com/Metatavu/keycloak-scim-server")
            connection.set("scm:git:git://github.com/Metatavu/keycloak-scim-server.git")
            developerConnection.set("scm:git:ssh://git@github.com/Metatavu/keycloak-scim-server.git")
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
}

signing {
    val signingKey: String? = providers.environmentVariable("ORG_GRADLE_PROJECT_signingInMemoryKey")
        .orElse(providers.gradleProperty("signingInMemoryKey")).orNull
    val signingPassword: String? = providers.environmentVariable("ORG_GRADLE_PROJECT_signingInMemoryKeyPassword")
        .orElse(providers.gradleProperty("signingInMemoryKeyPassword")).orNull
    if (signingKey != null && signingPassword != null) {
        useInMemoryPgpKeys(signingKey, signingPassword)
        sign(publishing.publications)
    }
}

