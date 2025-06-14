package fi.metatavu.keycloak.scim.server.test.utils;

import java.io.File;
import java.nio.file.Files;
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
     * Returns test data directory
     *
     * @return test data directory
     */
    public static File getTestDataDir() {
        File result = new File(getBuildDir(), "testdata");
        if (!result.exists()) {
            try {
                Files.createDirectories(result.toPath());
            } catch (Exception e) {
                throw new RuntimeException("Failed to create test data directory: " + result.getAbsolutePath(), e);
            }
        }

        return result;
    }

    /**
     * Returns test data directory for admin events
     *
     * @return test data directory for admin events
     */
    public static File getAdminEventsDir() {
        return new File(getTestDataDir(), "admin-events");
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
