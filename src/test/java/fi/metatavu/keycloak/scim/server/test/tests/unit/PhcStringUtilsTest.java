package fi.metatavu.keycloak.scim.server.test.tests.unit;

import fi.metatavu.keycloak.scim.server.authentication.PhcStringUtils;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.keycloak.models.credential.PasswordCredentialModel;
import org.keycloak.models.credential.dto.PasswordCredentialData;
import org.keycloak.models.credential.dto.PasswordSecretData;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PhcStringUtilsTest {

    // Helper to encode strings to Base64 without padding (simulating PHC format)
    private String toBase64NoPad(String input) {
        return Base64.getEncoder().encodeToString(input.getBytes(StandardCharsets.UTF_8))
            .replace("=", "");
    }

    @Test
    @DisplayName("Should successfully parse a valid Argon2id string with unpadded Base64")
    void testParseArgon2id_Valid_Unpadded() {
        // Setup: m=65536, t=2, p=1, salt="somesalt", hash="somehash"
        String saltRaw = "somesalt";
        String hashRaw = "somehash";
        String saltB64 = toBase64NoPad(saltRaw);
        String hashB64 = toBase64NoPad(hashRaw);

        // $argon2id$v=19$m=65536,t=2,p=1$<salt>$<hash>
        String phc = String.format("$argon2id$v=19$m=65536,t=2,p=1$%s$%s", saltB64, hashB64);

        // Execute
        PasswordCredentialModel model = PhcStringUtils.fromPHCString(phc);

        // Verify Structure
        assertNotNull(model);
        PasswordCredentialData credentialData = model.getPasswordCredentialData();
        PasswordSecretData secretData = model.getPasswordSecretData();

        // Verify Algorithm and Params
        assertEquals("argon2id", credentialData.getAlgorithm());
        assertEquals(2, credentialData.getHashIterations()); // 't' param

        var params = credentialData.getAdditionalParameters();
        assertEquals("id", params.getFirst("type"));
        assertEquals("1.3", params.getFirst("version")); // v=19 -> 1.3
        assertEquals("65536", params.getFirst("memory"));
        assertEquals("1", params.getFirst("parallelism"));

        // Verify Salt (Should be decoded back to raw bytes)
        assertArrayEquals(saltRaw.getBytes(StandardCharsets.UTF_8), secretData.getSalt(), "Salt bytes should match original input");

        // Verify Hash (Should be stored as PADDED Base64 string in Keycloak model)
        String expectedHashPadded = Base64.getEncoder().encodeToString(hashRaw.getBytes(StandardCharsets.UTF_8));
        assertEquals(expectedHashPadded, secretData.getValue(), "Hash should be stored with standard padding restored");

        // Verify calculated Hash Length (length of 'somehash' bytes is 8)
        assertEquals("8", params.getFirst("hashLength"));
    }

    @Test
    @DisplayName("Should parse Argon2d with version 1.0 (v=16)")
    void testParseArgon2d_Version10() {
        // v=16 -> 1.0
        String phc = "$argon2d$v=16$m=4096,t=3,p=1$c2FsdA$aGFzaA"; // salt="salt", hash="hash"

        PasswordCredentialModel model = PhcStringUtils.fromPHCString(phc);

        assertEquals("argon2d", model.getPasswordCredentialData().getAlgorithm());
        assertEquals("1.0", model.getPasswordCredentialData().getAdditionalParameters().getFirst("version"));
        assertEquals("d", model.getPasswordCredentialData().getAdditionalParameters().getFirst("type"));
    }

    @Test
    @DisplayName("Should successfully parse PBKDF2 string with unpadded inputs")
    void testParsePbkdf2_Valid() {
        // $pbkdf2-sha256$i=27500$<salt>$<hash>
        String saltRaw = "mysalt";
        String hashRaw = "myhash";
        String saltB64 = toBase64NoPad(saltRaw); // Unpadded
        String hashB64 = toBase64NoPad(hashRaw); // Unpadded

        String phc = String.format("$pbkdf2-sha256$i=10000$%s$%s", saltB64, hashB64);

        PasswordCredentialModel model = PhcStringUtils.fromPHCString(phc);

        // Verify
        assertEquals("pbkdf2-sha256", model.getPasswordCredentialData().getAlgorithm());
        assertEquals(10000, model.getPasswordCredentialData().getHashIterations());

        // Salt Check
        assertArrayEquals(saltRaw.getBytes(StandardCharsets.UTF_8), model.getPasswordSecretData().getSalt());

        // Hash Check (Expect restored padding)
        String expectedHashPadded = Base64.getEncoder().encodeToString(hashRaw.getBytes(StandardCharsets.UTF_8));
        assertEquals(expectedHashPadded, model.getPasswordSecretData().getValue());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "",
        "not-starting-with-dollar",
        "$",
        "$argon2id", // Not enough parts
        "$argon2id$v=19$m=1,t=1,p=1$salt", // Missing hash
        "$unknown-algo$v=1$..."
    })
    @DisplayName("Should throw IllegalArgumentException for malformed PHC strings")
    void testInvalidFormats(String invalidPhc) {
        assertThrows(IllegalArgumentException.class, () ->
            PhcStringUtils.fromPHCString(invalidPhc)
        );
    }

    @Test
    @DisplayName("Should throw RuntimeException if Salt is not valid Base64")
    void testInvalidBase64Salt() {
        // "!!!!" is not valid Base64
        String phc = "$argon2id$v=19$m=65536,t=2,p=1$!!!!$hash";

        RuntimeException e = assertThrows(RuntimeException.class, () ->
            PhcStringUtils.fromPHCString(phc)
        );
        assertTrue(e.getMessage().contains("Failed to decode salt"));
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException for unknown Argon2 version")
    void testInvalidArgon2Version() {
        // v=99 is unknown
        String phc = "$argon2id$v=99$m=65536,t=2,p=1$c2FsdA$aGFzaA";

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
            PhcStringUtils.fromPHCString(phc)
        );
        assertTrue(e.getMessage().contains("Unknown Argon2 version"));
    }

    @Test
    @DisplayName("Should parse even if padding is already present (Hybrid case)")
    void testParseWithExistingPadding() {
        // Salt "salt" -> c2FsdA==
        // Hash "hash" -> aGFzaA==
        String phc = "$argon2id$v=19$m=1024,t=1,p=1$c2FsdA==$aGFzaA==";

        assertDoesNotThrow(() -> PhcStringUtils.fromPHCString(phc));

        PasswordCredentialModel model = PhcStringUtils.fromPHCString(phc);
        // Ensure double padding didn't happen
        assertEquals("c2FsdA==", Base64.getEncoder().encodeToString(model.getPasswordSecretData().getSalt()));
    }

    @Test
    @DisplayName("Should throw exception if input is null")
    void testNullInput() {
        assertThrows(IllegalArgumentException.class, () -> PhcStringUtils.fromPHCString(null));
    }
}
