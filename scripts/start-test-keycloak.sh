#!/bin/sh

docker run -p 8080:8080 \
  -e KC_BOOTSTRAP_ADMIN_USERNAME=admin \
  -e KC_BOOTSTRAP_ADMIN_PASSWORD=admin \
  -v ./src/test/resources/kc-test.json:/opt/keycloak/data/import/kc-test.json \
  -v ./build/libs/:/opt/keycloak/providers/ \
  quay.io/keycloak/keycloak:26.1.2 start-dev --import-realm


