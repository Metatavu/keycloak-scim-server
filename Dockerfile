FROM gradle:8-jdk21 AS gradle-builder

WORKDIR /workspace

# Copy Gradle metadata first to maximize layer caching.
COPY gradlew gradlew
COPY gradle gradle
COPY settings.gradle.kts settings.gradle.kts
COPY build.gradle.kts build.gradle.kts
COPY gradle.properties gradle.properties
COPY test-event-listener/build.gradle.kts test-event-listener/build.gradle.kts

# Copy project sources required to build plugin jars.
COPY src src
COPY scim-openapi.yaml scim-openapi.yaml
COPY test-event-listener/src test-event-listener/src

RUN ./gradlew --no-daemon clean jar :test-event-listener:jar

FROM quay.io/keycloak/keycloak:26.3.5 AS keycloak-builder

WORKDIR /opt/keycloak
ENV KC_HEALTH_ENABLED=true

COPY --from=gradle-builder /workspace/build/libs/ /opt/keycloak/providers/
COPY --from=gradle-builder /workspace/test-event-listener/build/libs/ /opt/keycloak/providers/

RUN /opt/keycloak/bin/kc.sh build

FROM quay.io/keycloak/keycloak:26.3.5

ENV KC_HEALTH_ENABLED=true
COPY --from=keycloak-builder /opt/keycloak/ /opt/keycloak/