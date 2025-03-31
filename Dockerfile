FROM quay.io/keycloak/keycloak:26.1.4 as builder

WORKDIR /opt/keycloak
ADD ./build/libs/ /opt/keycloak/providers
ENV KC_HEALTH_ENABLED=true

RUN /opt/keycloak/bin/kc.sh build

FROM quay.io/keycloak/keycloak:26.1.4
ENV KC_HEALTH_ENABLED=true

COPY --from=builder /opt/keycloak/ /opt/keycloak/