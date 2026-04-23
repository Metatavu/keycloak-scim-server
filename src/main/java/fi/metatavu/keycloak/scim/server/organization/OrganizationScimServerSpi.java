package fi.metatavu.keycloak.scim.server.organization;

import org.keycloak.provider.Provider;
import org.keycloak.provider.ProviderFactory;
import org.keycloak.provider.Spi;

public class OrganizationScimServerSpi implements Spi {

  @Override
  public boolean isInternal() {
    return false;
  }

  @Override
  public String getName() {
    return "organizationScimServerProvider";
  }

  @Override
  public Class<? extends Provider> getProviderClass() {
    return OrganizationScimServerProvider.class;
  }

  @Override
  @SuppressWarnings("rawtypes")
  public Class<? extends ProviderFactory> getProviderFactoryClass() {
    return OrganizationScimServerProviderFactory.class;
  }
}
