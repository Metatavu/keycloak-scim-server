package fi.metatavu.keycloak.scim.server.authentication;

import java.util.List;
import java.util.Optional;
import org.jboss.logging.Logger;
import org.keycloak.common.util.MultivaluedHashMap;
import org.keycloak.credential.hash.PasswordHashProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.credential.PasswordCredentialModel;

/**
 * Verifies shared secret
 */
public class ExternalSharedSecretVerifier implements Verifier {

    private static final Logger logger = Logger.getLogger(ExternalSharedSecretVerifier.class);

    private final KeycloakSession session;
    private final String sharedSecret;

    /**
     * Constructor
     *
     * @param session Keycloak Session
     * @param sharedSecret shared secret
     */
    public ExternalSharedSecretVerifier(KeycloakSession session, String sharedSecret) {
        this.session = session;
        this.sharedSecret = sharedSecret;
    }

    /**
     * Verifies the given token.
     *
     * @param tokenString shared token string
     * @return true if the token is valid, false otherwise
     */
    @Override
    public boolean verify(String tokenString) {
        if (sharedSecret == null || sharedSecret.isBlank()) {
            logger.warn("Shared secret is null or blank.");
            return false;
        }
        PasswordCredentialModel model = PhcStringUtils.fromPHCString(sharedSecret);
        String algorithm = model.getPasswordCredentialData().getAlgorithm();
        MultivaluedHashMap<String, String> additionalParameters = model.getPasswordCredentialData()
            .getAdditionalParameters();
        String type = Optional.ofNullable(additionalParameters)
            .map(params -> params.get("type"))
            .filter(typeList -> !typeList.isEmpty())
            .map(List::getFirst)
            .orElse("");
        PasswordHashProvider hashProvider = session.getProvider(PasswordHashProvider.class, algorithm.replace(type, ""));

        if (hashProvider == null) {
            throw new RuntimeException(
                String.format(
                    "Hash provider not found with hash algorithm: %s. Only official Keycloak hash algorithms are expected (see README.md).",
                    algorithm
                )
            );
        }

        return hashProvider.verify(tokenString, model);
    }

}
