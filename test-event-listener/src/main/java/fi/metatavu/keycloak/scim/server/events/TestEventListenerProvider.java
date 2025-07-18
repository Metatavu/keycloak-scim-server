package fi.metatavu.keycloak.scim.server.events;

import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.util.JsonSerialization;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.UUID;

/**
 * Test event listener provider that logs admin events to files
 * <p>
 * This is intended only for debugging purposes and should not be used in production.
 */
public class TestEventListenerProvider implements EventListenerProvider {

    @Override
    public void onEvent(org.keycloak.events.Event event) {
    }

    @Override
    public void onEvent(AdminEvent adminEvent, boolean b) {
        try {
            File outputFolder = new File("/tmp/testdata/admin-events/");
            if (!outputFolder.exists()) {
                Files.createDirectories(outputFolder.toPath());
            }

            File outputFile = new File(outputFolder, UUID.randomUUID() + ".json");
            try (FileOutputStream outputStream = new FileOutputStream(outputFile)) {
                outputStream.write(JsonSerialization.writeValueAsBytes(adminEvent));
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() {

    }

}
