package fi.metatavu.keycloak.scim.server.organization.keycloak;

import static fi.metatavu.keycloak.scim.server.users.UsersController.getEmailDomain;

import fi.metatavu.keycloak.scim.server.ScimContext;
import fi.metatavu.keycloak.scim.server.organization.*;
import org.keycloak.models.FederatedIdentityModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.IdentityProviderModel;
import org.keycloak.models.OrganizationModel;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.organization.OrganizationProvider;
import org.keycloak.models.utils.ModelToRepresentation;
import java.net.URI;
import java.util.Collections;
import java.util.stream.Stream;
import org.jboss.logging.Logger;

/**
 * SCIM context for Keycloak organizations.
 */
public class KeycloakOrganizationScimContext extends OrganizationScimContext {

  private static final Logger logger = Logger.getLogger(KeycloakOrganizationScimContext.class);
  
  protected final OrganizationModel organization;

  public KeycloakOrganizationScimContext(URI baseUri, KeycloakSession session, RealmModel realm, OrganizationScimConfig config, OrganizationModel organization) {
    super(baseUri, session, realm, organization.getId(), config);
    this.organization = organization;
  }

  @Override
  public Stream<UserModel> getMembersStream(Integer first, Integer max) {
    return getSession().getProvider(OrganizationProvider.class).getMembersStream(organization, Collections.emptyMap(), true, null, null);
  }
  
  @Override
  public UserModel findUser(String userId) {
    return getSession().getProvider(OrganizationProvider.class).getMemberById(organization, userId);
  }
  
  @Override
  public boolean addMember(UserModel user) {
    return getSession().getProvider(OrganizationProvider.class).addManagedMember(organization, user);
  }
  
  @Override
  public boolean isMember(UserModel user) {
    return getSession().getProvider(OrganizationProvider.class).isManagedMember(organization, user);
  }

  @Override
  public boolean removeMember(UserModel user) {
    return getSession().getProvider(OrganizationProvider.class).removeMember(organization, user);
  }
  
  @Override
  public boolean linkUserIdp(UserModel user, String scimUserEmail, String scimUserName, String scimExternalId) {
    if (scimUserEmail == null) {
      logger.warn("User email is not set. Cannot link user to identity provider");
      return false;
    }

    if (scimExternalId == null) {
      logger.warn("User externalId is not set. Cannot link user to identity provider");
      return false;
    }

    String emailDomain = getEmailDomain(scimUserEmail);
    if (emailDomain == null) {
      logger.warn("User email domain is not set. Cannot link user to identity provider");
      return false;
    }

    IdentityProviderModel identityProvider =
        getSession().getProvider(OrganizationProvider.class).getIdentityProviders(organization)
        .filter(identityProviderModel -> {
            String identityProviderDomain = identityProviderModel.getConfig().get("kc.org.domain");
            return identityProviderDomain != null && identityProviderDomain.equals(emailDomain);
          })
        .findFirst()
        .orElse(null);

    if (identityProvider == null) {
      logger.warn("No identity provider found for email domain: " + emailDomain + ". Cannot link user to identity provider");
      return false;
    }

    if (getSession().users().getFederatedIdentity(getRealm(), user, identityProvider.getAlias()) == null) {
      logger.info("Linking user to identity provider: " + identityProvider.getAlias());
      FederatedIdentityModel identityModel = new FederatedIdentityModel(identityProvider.getAlias(), scimExternalId, scimUserName);
      getSession().users().addFederatedIdentity(getRealm(), user, identityModel);
      return true;
    }

    return false;
  }
  
  @Override
  public Object toRepresentation() {
    return ModelToRepresentation.toRepresentation(organization);
  }
}
