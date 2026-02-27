#!/bin/sh

SCIM_EXTENSION_JAR=$(ls ./build/libs/*.jar | head -n 1)
TEST_EVENT_LISTENER_JAR=$(ls ./test-event-listener/build/libs/*.jar | head -n 1)

docker run -p 8080:8080 \
  -e KC_BOOTSTRAP_ADMIN_USERNAME=admin \
  -e KC_BOOTSTRAP_ADMIN_PASSWORD=admin \
  -v ./src/test/resources/kc-test.json:/opt/keycloak/data/import/kc-test.json \
  -v ./src/test/resources/kc-external.json:/opt/keycloak/data/import/kc-external.json \
  -v ./src/test/resources/kc-organizations.json:/opt/keycloak/data/import/kc-organizations.json \
  -v $SCIM_EXTENSION_JAR:/opt/keycloak/providers/keycloak-scim-server.jar \
  -v $TEST_EVENT_LISTENER_JAR:/opt/keycloak/providers/test-event-listener.jar \
  quay.io/keycloak/keycloak:26.3.5 start-dev --import-realm


