package fi.metatavu.keycloak.scim.server.test.utils;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.utility.MountableFile;

import java.io.File;
import java.util.*;

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
        String dir = System.getenv("TEST_EVENTS_LISTENER_BUILD_DIR");
        if (dir == null || dir.isEmpty()) {
            throw new IllegalStateException("Environment variable TEST_EVENTS_LISTENER_BUILD_DIR is not set or is empty");
        }
        return dir;
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

    @SuppressWarnings("resource")
    public static KeycloakContainer createInternalAuthRealmKeycloakContainer(Network network) {
        return new KeycloakContainer(KeycloakTestUtils.getKeycloakImage())
                .withNetwork(network)
                .withNetworkAliases("scim-keycloak")
                .withEnv("SCIM_AUTHENTICATION_MODE", "KEYCLOAK")
                .withEnv("SCIM_IDENTITY_PROVIDER_ALIAS", "keycloak-oidc")
                .withProviderLibsFrom(KeycloakTestUtils.getBuildProviders())
                .withRealmImportFile("kc-test.json")
                .withEnv("JAVA_OPTS_APPEND", "-javaagent:/jacoco-agent/org.jacoco.agent-runtime.jar=destfile=/tmp/jacoco.exec")
                .withCopyFileToContainer(
                        MountableFile.forHostPath(getJacocoAgentPath()),
                        "/jacoco-agent/org.jacoco.agent-runtime.jar"
                )
                .withLogConsumer(outputFrame -> System.out.printf("KEYCLOAK: %s", outputFrame.getUtf8String()));
    }

    @SuppressWarnings("resource")
    public static KeycloakContainer createExternalAuthRealmKeycloakContainer(Network network) {
        return new KeycloakContainer(KeycloakTestUtils.getKeycloakImage())
                .withNetwork(network)
                .withNetworkAliases("scim-keycloak")
                .withEnv("SCIM_AUTHENTICATION_MODE", "EXTERNAL")
                .withEnv("SCIM_EXTERNAL_ISSUER", "*") // Just for testing purposes
                .withEnv("SCIM_EXTERNAL_AUDIENCE", "account")
                .withEnv("SCIM_EXTERNAL_JWKS_URI", "http://localhost:8080/realms/external/protocol/openid-connect/certs")
                .withProviderLibsFrom(KeycloakTestUtils.getBuildProviders())
                .withRealmImportFiles("kc-test.json", "kc-external.json")
                .withEnv("JAVA_OPTS_APPEND", "-javaagent:/jacoco-agent/org.jacoco.agent-runtime.jar=destfile=/tmp/jacoco.exec")
                .withCopyFileToContainer(
                        MountableFile.forHostPath(getJacocoAgentPath()),
                        "/jacoco-agent/org.jacoco.agent-runtime.jar"
                )
                .withLogConsumer(outputFrame -> System.out.printf("KEYCLOAK: %s", outputFrame.getUtf8String()));
    }

    @SuppressWarnings("resource")
    public static KeycloakContainer createExternalAuthSharedSecretRealmKeycloakContainer(Network network) {
        return new KeycloakContainer(KeycloakTestUtils.getKeycloakImage())
                .withNetwork(network)
                .withNetworkAliases("scim-keycloak")
                .withEnv("SCIM_AUTHENTICATION_MODE", "EXTERNAL")
                .withEnv("SCIM_EXTERNAL_SHARED_SECRET", "$argon2id$v=19$m=16,t=2,p=1$UUppcFAwQUp0SkQwVGZudQ$j5RwfEzt3Gvwpbqp0VDcJg") // tutu with argon2id
                .withProviderLibsFrom(KeycloakTestUtils.getBuildProviders())
                .withRealmImportFiles("kc-test.json", "kc-external.json")
                .withEnv("JAVA_OPTS_APPEND", "-javaagent:/jacoco-agent/org.jacoco.agent-runtime.jar=destfile=/tmp/jacoco.exec")
                .withCopyFileToContainer(
                        MountableFile.forHostPath(getJacocoAgentPath()),
                        "/jacoco-agent/org.jacoco.agent-runtime.jar"
                )
                .withLogConsumer(outputFrame -> System.out.printf("KEYCLOAK: %s", outputFrame.getUtf8String()));
    }

    @SuppressWarnings("resource")
    public static KeycloakContainer createNoAuthRealmKeycloakContainer(Network network) {
        return new KeycloakContainer(KeycloakTestUtils.getKeycloakImage())
                .withNetwork(network)
                .withNetworkAliases("scim-keycloak")
                .withProviderLibsFrom(KeycloakTestUtils.getBuildProviders())
                .withRealmImportFile("kc-test.json")
                .withEnv("JAVA_OPTS_APPEND", "-javaagent:/jacoco-agent/org.jacoco.agent-runtime.jar=destfile=/tmp/jacoco.exec")
                .withCopyFileToContainer(
                        MountableFile.forHostPath(getJacocoAgentPath()),
                        "/jacoco-agent/org.jacoco.agent-runtime.jar"
                )
                .withLogConsumer(outputFrame -> System.out.printf("KEYCLOAK: %s", outputFrame.getUtf8String()));
    }

    @SuppressWarnings("resource")
    public static KeycloakContainer createOrganizationKeycloakContainer(Network network) {
        return new KeycloakContainer(KeycloakTestUtils.getKeycloakImage())
                .withNetwork(network)
                .withNetworkAliases("scim-keycloak")
                .withProviderLibsFrom(KeycloakTestUtils.getBuildProviders())
                .withRealmImportFiles("kc-organizations.json", "kc-external.json")
                .withEnv("JAVA_OPTS_APPEND", "-javaagent:/jacoco-agent/org.jacoco.agent-runtime.jar=destfile=/tmp/jacoco.exec")
                .withCopyFileToContainer(
                        MountableFile.forHostPath(getJacocoAgentPath()),
                        "/jacoco-agent/org.jacoco.agent-runtime.jar"
                )
                .withLogConsumer(outputFrame -> System.out.printf("KEYCLOAK: %s", outputFrame.getUtf8String()));
    }

    /**
     * Stops the Keycloak container and copies the JaCoCo exec file to the build directory
     *
     * @param keycloakContainer the Keycloak container to stop
     */
    @SuppressWarnings("resource")
    public static void stopKeycloakContainer(KeycloakContainer keycloakContainer) {
        if (keycloakContainer != null) {
            String execFile = UUID.randomUUID().toString();
            keycloakContainer.getDockerClient().stopContainerCmd(keycloakContainer.getContainerId()).exec();
            keycloakContainer.copyFileFromContainer("/tmp/jacoco.exec", "./build/jacoco/" + execFile + ".exec");

            keycloakContainer.stop();
        }
    }

    /**
     * Returns Jacoco agent path
     *
     * @return Jacoco agent path
     */
    private static String getJacocoAgentPath() {
        return System.getenv("JACOCO_AGENT");
    }

}
