# Keycloak SCIM 2.0 Extension

This project provides a **SCIM 2.0-compliant extension** for [Keycloak](https://www.keycloak.org/), enabling SCIM-based user and group provisioning. It supports:

- **Realm-level SCIM APIs**:  
  `/realms/{realm}/scim/v2`
- **Organization-level SCIM APIs** (Keycloak 26+ with Organizations):  
  `/realms/{realm}/scim/v2/organizations/{organizationId}`

## Prerequisites

- **Keycloak**: This extension is developed for Keycloak **26.1.4**. It may work with other versions, but compatibility is not guaranteed.
- **Java**: Java **21** is required to build the project.

## Installation

### Option 1: Install from GitHub Packages (recommended)

Easiest way to use the extension is to download a JAR file from GitHub packages. 

1. Download the latest JAR from: [GitHub Packages](https://github.com/Metatavu/keycloak-scim-server/packages/2454996)
2. Copy it to your Keycloak instance:
```bash
   cp keycloak-scim-server-*.jar $KEYCLOAK_HOME/providers/
```
3. Restart Keycloak.


### Option 2: Build from Source

1. Build the extension:
```bash
./gradlew build
```
2. Copy the built JAR file from `build/libs/keycloak-scim-server-<version>.jar` to the Keycloak providers directory:
```bash
cp build/libs/keycloak-scim-server-*.jar $KEYCLOAK_HOME/providers/
```

## Configuration

### Configuation on Realm level

Configuration on realm level is done by defining environment variables in the Keycloak server. 

The following environment variables are available:
| Setting                  | Value                                                                             |
| ------------------------ | --------------------------------------------------------------------------------- |
| SCIM_AUTHENTICATION_MODE | Authentication mode for SCIM API. Possible values are KEYCLOAK and EXTERNAL. If the value is not set the server will respond unauthorzed for all requests. |
| SCIM_EXTERNAL_ISSUER     | Issuer for the external authentication. This is used to validate the JWT token.   |
| SCIM_EXTERNAL_AUDIENCE   | JWKS URI for the external authentication. This is used to validate the JWT token. |
| SCIM_EXTERNAL_JWKS_URI   | Audience for the external authentication. This is used to validate the JWT token. |

### Configuration on Organization level

Configuration on organization level is done by defining organization attributes in the Keycloak server.
The following organization attributes are available:

| Setting                  | Value                                                                             |
| ------------------------ | --------------------------------------------------------------------------------- |
| SCIM_AUTHENTICATION_MODE | Authentication mode for SCIM API. Possible values are KEYCLOAK and EXTERNAL. If the value is not set the server will respond unauthorzed for all requests. Currently on organization level only EXTERNAL is supported. |
| SCIM_EXTERNAL_ISSUER     | Issuer for the external authentication. This is used to validate the JWT token.   |
| SCIM_EXTERNAL_AUDIENCE   | JWKS URI for the external authentication. This is used to validate the JWT token. |
| SCIM_EXTERNAL_JWKS_URI   | Audience for the external authentication. This is used to validate the JWT token. |

### Azure Entra ID SCIM Configuration

This extension is compatible with **Microsoft Entra ID** SCIM provisioning.

**Keycloak Configuration**

Before Entra ID can provision users and groups to Keycloak via SCIM, you need to configure SCIM authentication settings.

These settings can be applied either:

* At the realm level (for /realms/{realm}/scim/v2)
* Or at the organization level (for /realms/organizations/scim/v2/organizations/{organizationId})

For more details, refer to the sections [Configuration on Realm Level] and [Configuration on Organization Level in this document].

SCIM Settings for Entra ID

When using Entra ID settings will be following:

| Setting                  | Value                                                                   |
| ------------------------ | ----------------------------------------------------------------------- |
| SCIM_AUTHENTICATION_MODE | EXTERNAL                                                                |
| SCIM_EXTERNAL_ISSUER     | https://sts.windows.net/<your-tenant-id>                                |
| SCIM_EXTERNAL_AUDIENCE   | 8adf8e6e-67b2-4cf2-a259-e3dc5476c621                                    |
| SCIM_EXTERNAL_JWKS_URI   | https://login.microsoftonline.com/<your-tenant-id>/discovery/v2.0/keys  |

Replace <your-tenant-id> with your actual Azure tenant ID.

* SCIM_AUTHENTICATION_MODE enables external authentication support for the SCIM server. In this case the external authentication source will be the Azure Entra ID.
* SCIM_EXTERNAL_ISSUER ensures the JWT token was issued by your tenant.
* SCIM_EXTERNAL_AUDIENCE must be exactly 8adf8e6e-67b2-4cf2-a259-e3dc5476c621 — this is the default audience used by Entra ID for non-gallery applications.
* SCIM_EXTERNAL_JWKS_URI allows Keycloak to fetch public keys for token validation.

**Azure Configuration**

Step-by-step guide on the Azure:

1. Sign in to the [Azure portal](https://portal.azure.com)
2. Go to **Identity → Applications → Enterprise applications**
3. Click **+ New application → + Create your own application**
4. Enter a name for your application (e.g., My Keycloak SCIM).
5. Choose **Integrate any other application you don't find in the gallery.**
6. Click **Create** to create the application. The application will open automatically in its management screen.
7. In the application's left-hand menu, select **Provisioning**.
8. Click **+ New configuration**.
9. Fill in the following:
 - Tenant URL (realm): https://mykeycloak.example.com/auth/realms/my-realm/scim/v2 or 
 - Tenant URL (organization): https://mykeycloak.example.com/auth/realms/organizations/scim/v2/organizations/{organizationId} 
 - Secret Token: Leave this field empty (the application will use the Entra ID bearer token).
10. Click **Test Connection** to verify the SCIM endpoint.
11. Click **Create**.
12. Navigate to **Attribute Mapping (Preview)**.
13. Open **Provision Microsoft Entra ID Groups**.
14. Set **Enabled** to **No**.
15. Click **Save**.
16. Go back → **open Provision Microsoft Entra ID Users**.
17. Open Provision Microsoft Entra ID Users.
18. Define mappings, following are required for Keycloak extension:
- userName
- active
- emails[type eq "work"].value
- name.givenName
- name.familyName
19. Click Save.
20. Go back to Provisioning.
21. Set Provisioning Status to On.
22. Click Save.
23. Reload the page to ensure the configuration was saved.
24. Navigate to **Manage > Users and groups > + Add user/group**.
25.  Select the user you want to provision and click Assign.
26. Navigate to **Provision on demand**.
27. Find the user you just assigned.
28. Click on the user and select **Provision**.
29. Verify that the provisioning completes successfully.

For more information, refer to the following documents: 

https://learn.microsoft.com/en-us/entra/identity/saas-apps/tutorial-list
