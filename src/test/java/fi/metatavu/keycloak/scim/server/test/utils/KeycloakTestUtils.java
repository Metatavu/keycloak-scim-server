package fi.metatavu.keycloak.scim.server.test.utils;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Keycloak test utils
 */
public class KeycloakTestUtils {

    /**
     * Returns Keycloak image
     *
     * @return Keycloak image
     */
    public static String getKeycloakImage() {
        String keycloakVersion = System.getenv("KEYCLOAK_VERSION");
        if (keycloakVersion == null || keycloakVersion.isEmpty()) {
            throw new IllegalStateException("Environment variable 'KEYCLOAK_VERSION' is not set or is empty.");
        }
        return "quay.io/keycloak/keycloak:" + keycloakVersion;
    }

    /**
     * Returns build Keycloak extensions
     *
     * @return build Keycloak extensions
     */
    public static List<File> getBuildProviders() {
        File mainLibs = new File(getBuildDir(), "libs");
        File testEventListenerLibs = new File(getTestEventsListenerBuildDir(), "libs");

        List<File> result = new ArrayList<>();

        result.addAll(getJarFiles(mainLibs));
        result.addAll(getJarFiles(testEventListenerLibs));

        return result;
    }

    /**
     * Returns build directory
     *
     * @return build directory
     */
    private static String getBuildDir() {
        return System.getenv("BUILD_DIR");
    }

    /**
     * Returns build directory for test events listener
     *
     * @return build directory for test events listener
     */
    private static String getTestEventsListenerBuildDir() {
        return System.getenv("TEST_EVENTS_LISTENER_BUILD_DIR");
    }

    /**
     * Returns a list of JAR files in the specified directory
     *
     * @param dir directory to search for JAR files
     * @return list of JAR files
     */
    private static List<File> getJarFiles(File dir) {
        if (dir == null || !dir.isDirectory()) {
            return new ArrayList<>();
        }
        return Arrays.stream(Objects.requireNonNull(dir.listFiles((d, name) -> name.endsWith(".jar"))))
                .toList();
    }

}
