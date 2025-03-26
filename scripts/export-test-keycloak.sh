#!/bin/sh

REALM=test
CONTAINER_ID=$(docker ps -q --filter ancestor=quay.io/keycloak/keycloak:26.1.2)

docker exec -ti $CONTAINER_ID cp -rp /opt/keycloak/data/h2 /tmp
docker exec -e JDBC_PARAMS='?useSSL=false'  -ti $CONTAINER_ID /opt/keycloak/bin/kc.sh export --file /tmp/my_realm.json --realm $REALM --db dev-file --db-url 'jdbc:h2:file:/tmp/h2/keycloakdb;NON_KEYWORDS=VALUE'
docker cp $CONTAINER_ID:/tmp/my_realm.json ./src/test/resources/kc-test.json