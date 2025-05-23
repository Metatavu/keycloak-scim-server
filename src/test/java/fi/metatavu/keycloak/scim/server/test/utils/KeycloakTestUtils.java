package fi.metatavu.keycloak.scim.server.test.utils;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Keycloak test utils
 */
public class KeycloakTestUtils {

    /**
     * Returns build providers
     *
     * @return build providers
     */
    public static List<File> getBuildProviders() {
        File buildDir = new File(getBuildDir(), "libs");
        return Arrays.stream(Objects.requireNonNull(buildDir.listFiles((dir, name) -> name.endsWith(".jar")))).toList();
    }

    /**
     * Returns build directory
     *
     * @return build directory
     */
    private static String getBuildDir() {
        return System.getenv("BUILD_DIR");
    }

}
