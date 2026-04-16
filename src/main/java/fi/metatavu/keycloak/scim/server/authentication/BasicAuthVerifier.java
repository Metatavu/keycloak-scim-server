package fi.metatavu.keycloak.scim.server.authentication;

import java.util.List;
import java.util.Optional;
import org.jboss.logging.Logger;
import org.keycloak.common.util.MultivaluedHashMap;
import org.keycloak.credential.hash.PasswordHashProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.credential.PasswordCredentialModel;

/**
 * Verifies Basic Auth credentials (username and password)
 */
public class BasicAuthVerifier implements Verifier {

    private static final Logger logger = Logger.getLogger(BasicAuthVerifier.class);

    private final KeycloakSession session;
    private final String expectedUsername;
    private final String hashedPassword;

    /**
     * Constructor
     *
     * @param session Keycloak Session
     * @param expectedUsername expected username
     * @param hashedPassword password hash in PHC String format
     */
    public BasicAuthVerifier(KeycloakSession session, String expectedUsername, String hashedPassword) {
        this.session = session;
        this.expectedUsername = expectedUsername;
        this.hashedPassword = hashedPassword;
    }

    /**
     * Verifies the given credentials.
     *
     * @param credentials username:password string (already Base64-decoded)
     * @return true if the credentials are valid, false otherwise
     */
    @Override
    public boolean verify(String credentials) {
        int colonIndex = credentials.indexOf(':');
        if (colonIndex < 0) {
            logger.warn("Basic auth credentials missing colon separator");
            return false;
        }

        String username = credentials.substring(0, colonIndex);
        String password = credentials.substring(colonIndex + 1);

        if (!expectedUsername.equals(username)) {
            logger.warn("Basic auth username mismatch");
            return false;
        }

        if (hashedPassword == null || hashedPassword.isBlank()) {
            logger.warn("Basic auth password hash is null or blank");
            return false;
        }

        PasswordCredentialModel model = PhcStringUtils.fromPHCString(hashedPassword);
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

        return hashProvider.verify(password, model);
    }

}
