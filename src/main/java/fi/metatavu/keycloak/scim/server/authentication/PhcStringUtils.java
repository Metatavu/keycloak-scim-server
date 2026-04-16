package fi.metatavu.keycloak.scim.server.authentication;

import org.jboss.logging.Logger;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.keycloak.common.util.Base64;
import org.keycloak.models.credential.PasswordCredentialModel;

public class PhcStringUtils {

    private static final Logger logger = Logger.getLogger(PhcStringUtils.class.getName());

    public static final String ARGON_2_PREFIX = "argon2";
    public static final String PBKDF2_PREFIX = "pbkdf2";

    private PhcStringUtils() {
    }

    /**
     * Parses a PHC String back into a PasswordCredentialModel for verification.
     */
    public static PasswordCredentialModel fromPHCString(String phcString) {
        if (phcString == null || !phcString.startsWith("$")) {
            throw new IllegalArgumentException("Invalid PHC string format: " + phcString);
        }

        // Use limit=-1 to preserve trailing empty strings
        String[] parts = phcString.split("\\$", -1);

        // Minimum valid structure check (at least alg must exist)
        if (parts.length < 2) {
            throw new IllegalArgumentException("Invalid PHC string format: " + phcString);
        }

        String algId = parts[1];

        logger.debugf("Parsing PHC string with algorithm: %s", algId);

        if (algId.startsWith(ARGON_2_PREFIX)) {
            return parseArgon2(parts);
        } else if (algId.startsWith(PBKDF2_PREFIX)) {
            return parsePbkdf2(parts);
        } else {
            logger.warnf("Unknown algorithm in PHC string: %s", algId);
            throw new IllegalArgumentException("Unknown algorithm in PHC string: " + algId);
        }
    }

    // --- ARGON2 HANDLERS ---

    private static PasswordCredentialModel parseArgon2(String[] parts) {
        // Expected Argon2 format (6 parts due to leading empty string):
        // [0] ""
        // [1] alg (argon2id)
        // [2] version (v=19)
        // [3] params (m=65536,t=3,p=1)
        // [4] salt
        // [5] hash

        if (parts.length < 6) {
            throw new IllegalArgumentException("Invalid Argon2 PHC string: missing parameters, salt, or hash");
        }

        String alg = parts[1];
        String type = extractTypeSuffix(alg, ARGON_2_PREFIX);
        String ver = getValue("v", parts[2]);
        ver = switch (ver) {
            case "19" -> "1.3";
            case "16" -> "1.0";
            default -> throw new IllegalArgumentException("Unknown Argon2 version: " + ver + ". Must be 19 (v1.3) or 16 (v1.0).");
        };
        String[] params = parts[3].split(",");
        String mem = getValue("m", params);
        String iter = getValue("t", params);
        String para = getValue("p", params);
        String salt = parts[4];

        String hashB64 = restorePadding(parts[5]);
        String hashLength = String.valueOf(getHashLength(hashB64));

        Map<String, List<String>> additionalParams = new HashMap<>();
        additionalParams.put("type", List.of(type));
        additionalParams.put("version", List.of(ver));
        additionalParams.put("memory", List.of(mem));
        additionalParams.put("iterations", List.of(iter));
        additionalParams.put("parallelism", List.of(para));
        additionalParams.put("hashLength", List.of(hashLength));

        byte[] saltBytes;
        try {
            String paddedSalt = restorePadding(salt);
            saltBytes = Base64.decode(paddedSalt);
        } catch (Exception e) {
            throw new RuntimeException("Failed to decode salt from PHC string.", e);
        }

        return PasswordCredentialModel.createFromValues(
            alg,
            saltBytes,
            Integer.parseInt(iter),
            additionalParams,
            hashB64
        );
    }

    private static int getHashLength(String hashB64) {
        if (hashB64 == null || hashB64.isEmpty()) {
            return 0;
        }
        byte[] hashBytes;
        try {
            hashBytes = Base64.decode(hashB64);
        } catch (Exception e) {
            throw new RuntimeException("Failed to decode hashB64", e);
        }
        return hashBytes.length;
    }

    public static String extractTypeSuffix(String algorithmString, String algPrefix) {
        if (algorithmString == null || !algorithmString.startsWith(algPrefix)) {
            throw new IllegalArgumentException("Input string must be a valid Argon2 algorithm identifier.");
        }

        return algorithmString.substring(algPrefix.length());
    }

    // --- PBKDF2 HANDLERS ---

    private static PasswordCredentialModel parsePbkdf2(String[] parts) {
        // Expected PBKDF2 format (5 parts):
        // [0] ""
        // [1] alg
        // [2] iterations
        // [3] salt
        // [4] hash

        if (parts.length < 5) {
            throw new IllegalArgumentException("Invalid PBKDF2 PHC string: missing parameters, salt, or hash");
        }

        String alg = parts[1];
        String iterStr = getValue("i", parts[2]);
        int iterations = Integer.parseInt(iterStr);
        String salt = parts[3];
        String hash = parts[4];

        byte[] saltBytes;
        try {
            String paddedSalt = restorePadding(salt);
            saltBytes = Base64.decode(paddedSalt);
        } catch (Exception e) {
            throw new RuntimeException("Failed to decode PBKDF2 salt", e);
        }

        return PasswordCredentialModel.createFromValues(
            alg,
            saltBytes,
            iterations,
            Collections.emptyMap(),
            restorePadding(hash)
        );
    }

    private static String getValue(String key, String... items) {
        for (String item : items) {
            if (item.startsWith(key + "=")) {
                return item.substring(key.length() + 1);
            }
        }
        throw new IllegalArgumentException("Missing parameter: " + key);
    }

    private static String restorePadding(String base64) {
        if (base64 == null) return null;
        StringBuilder sb = new StringBuilder(base64);
        while (sb.length() % 4 != 0) {
            sb.append('=');
        }
        return sb.toString();
    }
}
