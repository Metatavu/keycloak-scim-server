package fi.metatavu.keycloak.scim.server.metadata;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import fi.metatavu.keycloak.scim.server.model.SchemaAttribute;

import org.keycloak.models.UserModel;

public class EmailsUserAttribute extends UserAttribute<List<Map<String, Object>>> {

    public EmailsUserAttribute() {
        super(
            Source.USER_MODEL,
            UserModel.EMAIL,
            "emails",
            "Emails",
            SchemaAttribute.TypeEnum.COMPLEX,
            SchemaAttribute.MutabilityEnum.READWRITE,
            SchemaAttribute.UniquenessEnum.NONE,

            user -> {
                String email = user.getEmail();
                if (email == null || email.isBlank()) {
                    return List.of();
                }

                Map<String, Object> emailObject = new LinkedHashMap<>();
                emailObject.put("value", email);
                emailObject.put("primary", true);

                return List.of(emailObject);
            },

            (user, values) -> {
                if (values == null || values.isEmpty()) {
                    user.setEmail(null);
                    return;
                }

                String firstEmail = null;
                String primaryEmail = null;

                for (Map<String, Object> entry : values) {
                    if (entry == null) continue;

                    Object valueObj = entry.get("value");
                    if (valueObj == null) continue;

                    String email = String.valueOf(valueObj);

                    if (firstEmail == null) {
                        firstEmail = email;
                    }

                    Object primaryObj = entry.get("primary");
                    boolean primary =
                        primaryObj instanceof Boolean b ? b :
                        primaryObj instanceof String s && Boolean.parseBoolean(s);

                    if (primary) {
                        primaryEmail = email;
                        break;
                    }
                }

                user.setEmail(primaryEmail != null ? primaryEmail : firstEmail);
            }
        );
    }
}