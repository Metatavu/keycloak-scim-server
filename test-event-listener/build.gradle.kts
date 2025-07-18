plugins {
    `java-library`
}

repositories {
    mavenLocal()
    mavenCentral()
}

val keycloakVersion: String by project

dependencies {
    implementation(enforcedPlatform("org.keycloak.bom:keycloak-bom-parent:$keycloakVersion"))
    compileOnly("org.keycloak:keycloak-services:$keycloakVersion")
}

group = "fi.metatavu.keycloak.scim.server.test.events"

java {
    targetCompatibility = JavaVersion.VERSION_21
    sourceCompatibility = JavaVersion.VERSION_21

    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

