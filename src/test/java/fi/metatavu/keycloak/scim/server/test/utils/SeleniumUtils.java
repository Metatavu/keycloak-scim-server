package fi.metatavu.keycloak.scim.server.test.utils;

import org.testcontainers.containers.BrowserWebDriverContainer.VncRecordingMode;

import java.io.File;
import java.util.Objects;

/**
 * Selenium test utilities
 */
public class SeleniumUtils {

    /**
     * Returns VNC recording mode
     *
     * @return VNC recording mode
     */
    public static VncRecordingMode getRecordingMode() {
        String recordingMode = System.getenv("TEST_RECORDING_MODE");
        if (recordingMode != null) {
            return VncRecordingMode.valueOf(recordingMode);
        }

        return VncRecordingMode.RECORD_FAILING;
    }

    /**
     * Returns recording path
     *
     * @return recording path
     */
    public static File getRecordingPath() {
        String recordingPath = System.getenv("TEST_RECORDING_PATH");
        return new File(Objects.requireNonNullElse(recordingPath, "/tmp/video"));

    }

}