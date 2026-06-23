# Keycloak SCIM 2.0 Extension

This project provides a **SCIM 2.0-compliant extension** for [Keycloak](https://www.keycloak.org/), enabling SCIM-based user and group provisioning. It supports:

- **Realm-level SCIM APIs**:
  `/realms/{realm}/scim/v2`
- **Organization-level SCIM APIs** (Keycloak 26+ with Organizations):
  `/realms/{realm}/scim/v2/organizations/{organizationId}`

## Table of Contents

- [Prerequisites](#prerequisites)
- [Installation](#installation)
  - [Option 1: Include from GitHub Release](#option-1-include-it-directly-from-github-release)
  - [Option 2: Install from GitHub Packages](#option-2-install-from-github-packages-recommended)
  - [Option 3: Build from Source](#option-3-build-from-source)
- [Configuration](#configuration)
  - [Instance-Level Configuration](#instance-level-configuration)
  - [Realm-Level Configuration](#realm-level-configuration)
  - [Organization-Level Configuration](#organization-level-configuration)
- [Authentication](#authentication)
  - [Keycloak Authentication](#keycloak-authentication)
  - [External JWT (JWKS) Authentication](#external-jwt-jwks-authentication)
  - [External Shared Secret (Bearer Token) Authentication](#external-shared-secret-bearer-token-authentication)
  - [External Basic Auth Authentication](#external-basic-auth-authentication)
- [Vendor Configuration Guides](#vendor-configuration-guides)
  - [Microsoft Entra ID](#microsoft-entra-id)
  - [Okta](#okta)
- [Identity Provider Linking](#identity-provider-linking)
  - [Identity Provider Linking with Azure Entra ID](#identity-provider-linking-with-azure-entra-id)
- [SCIM-Managed Users](#scim-managed-users)
- [License](#license)

## Prerequisites

- **Keycloak**: This extension is developed for Keycloak **26.3.5**. It may work with other versions, but compatibility is not guaranteed.
- **Java**: Java **21** is required to build the project.

## Installation

### Option 1: Include it directly from GitHub Release

You can reference the JAR file from a GitHub Release directly in your init container or Dockerfile.

For example, using a Helm `values.yaml`:
```yaml
extraInitContainers: |
  - name: download-scim-plugin
    image: alpine:latest
    command:
      - sh
      - -c
      - >
        apk add --no-cache curl &&
        curl -L -o /extensions/keycloak-scim-server-<version>.jar https://github.com/Metatavu/keycloak-scim-server/releases/download/v<version>/keycloak-scim-server-<version>.jar
    volumeMounts:
      - name: extensions
        mountPath: /extensions

extraVolumeMounts: |
  - name: extensions
    mountPath: /opt/keycloak/providers

extraVolumes: |
  - name: extensions
    emptyDir: {}
```

### Option 2: Install from GitHub Packages (recommended)

Download the JAR file from GitHub packages.

1. Download the latest JAR from: [GitHub Packages](https://github.com/Metatavu/keycloak-scim-server/packages/2454996)
2. Copy it to your Keycloak instance:
```bash
cp keycloak-scim-server-*.jar $KEYCLOAK_HOME/providers/
```
3. Restart Keycloak.

### Option 3: Build from Source

1. Build the extension:
```bash
./gradlew build
```
2. Copy the built JAR file from `build/libs/keycloak-scim-server-<version>.jar` to the Keycloak providers directory:
```bash
cp build/libs/keycloak-scim-server-*.jar $KEYCLOAK_HOME/providers/
```

## Configuration

All settings can be applied at three levels. Settings at a more specific level override broader ones (organization > realm > instance).

### Instance-Level Configuration

Configuration on instance level is done by defining environment variables on the Keycloak server.

| Setting                      | Description                                                                                                                                                                                                                |
|------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `SCIM_AUTHENTICATION_MODE`   | Authentication mode for SCIM API. Possible values are `KEYCLOAK` and `EXTERNAL`. If not set the server will respond unauthorized for all requests.                                                                         |
| `SCIM_EXTERNAL_ISSUER`       | Issuer for external JWT authentication. Used to validate the JWT token issuer claim.                                                                                                                                       |
| `SCIM_EXTERNAL_AUDIENCE`     | Audience for external JWT authentication. Used to validate the JWT token audience claim.                                                                                                                                   |
| `SCIM_EXTERNAL_JWKS_URI`     | JWKS URI for external JWT authentication. Used to fetch public keys for JWT token signature validation.                                                                                                                    |
| `SCIM_EXTERNAL_SHARED_SECRET`| Shared secret in [PHC String Format](https://github.com/P-H-C/phc-string-format/blob/master/phc-sf-spec.md) used for bearer token authentication/validation.                                                              |
| `SCIM_BASIC_AUTH_USERNAME`   | Username for HTTP Basic authentication.                                                                                                                                                                                    |
| `SCIM_BASIC_AUTH_PASSWORD`   | Password hash in [PHC String Format](https://github.com/P-H-C/phc-string-format/blob/master/phc-sf-spec.md) for HTTP Basic authentication.                                                                                |
| `SCIM_LINK_IDP`              | Enables support for linking realm identity provider with user.                                                                                                                                                             |
| `SCIM_IDENTITY_PROVIDER_ALIAS`| Alias of Identity Provider to be linked to the user.                                                                                                                                                                      |

### Configuration on Realm level

The following REST call can be made through the Keycloak Admin API to store settings as realm attributes. Realm-level settings override instance-level settings.

PUT `/admin/realms/{realm}`
```json
{
  "attributes": {
    "scim.authentication.mode": "EXTERNAL|KEYCLOAK",
    "scim.external.issuer": "string",
    "scim.external.jwks.uri": "string",
    "scim.external.audience": "string",
    "scim.external.shared.secret": "string",
    "scim.external.shared.secret.hash.algorithm": "string",
    "scim.basic.auth.username": "string",
    "scim.basic.auth.password": "string",
    "scim.link.idp": "true|false",
    "scim.identity.provider.alias": "string"
  }
}
```

### Configuration on Organization level

Configuration on organization level is done by defining organization attributes in the Keycloak server. Only `EXTERNAL` authentication mode is supported at the organization level.

| Setting                      | Description                                                                                                                                         |
|------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------|
| `SCIM_AUTHENTICATION_MODE`   | Must be `EXTERNAL`. Only external authentication is supported at the organization level.                                                            |
| `SCIM_EXTERNAL_ISSUER`       | Issuer for external JWT authentication.                                                                                                             |
| `SCIM_EXTERNAL_AUDIENCE`     | Audience for external JWT authentication.                                                                                                           |
| `SCIM_EXTERNAL_JWKS_URI`     | JWKS URI for external JWT authentication.                                                                                                           |
| `SCIM_EXTERNAL_SHARED_SECRET`| Shared secret in PHC String Format for bearer token authentication.                                                                                 |
| `SCIM_BASIC_AUTH_USERNAME`   | Username for HTTP Basic authentication.                                                                                                             |
| `SCIM_BASIC_AUTH_PASSWORD`   | Password hash in PHC String Format for HTTP Basic authentication.                                                                                   |
| `SCIM_LINK_IDP`              | Enables support for linking organization identity provider with user.                                                                               |
| `SCIM_EMAIL_AS_USERNAME`     | Forces server to use email as username instead of actual username. When enabled, username will be unaffected by update operations. Organization-level only. |

## Authentication

The SCIM server supports four authentication methods. For `EXTERNAL` mode, the method is determined automatically based on the authorization header and configuration.

### Keycloak Authentication

Uses Keycloak's built-in service account authentication. The SCIM client authenticates using an OAuth2 client credentials flow against Keycloak itself, and the resulting access token is validated natively.

**Required settings:**

| Setting                    | Value       |
|----------------------------|-------------|
| `SCIM_AUTHENTICATION_MODE` | `KEYCLOAK`  |

**Requirements:**
- A Keycloak client with **Service Accounts Enabled**
- The client's service account must have the `scim-access` realm role

**Example:** The SCIM client obtains a token via the Keycloak token endpoint and sends it as a bearer token:
```
Authorization: Bearer <keycloak-access-token>
```

### External JWT (JWKS) Authentication

Validates a JWT bearer token issued by an external identity provider. The token signature is verified against public keys fetched from the configured JWKS endpoint, and the issuer and audience claims are validated.

**Required settings:**

| Setting                    | Value                                      |
|----------------------------|--------------------------------------------|
| `SCIM_AUTHENTICATION_MODE` | `EXTERNAL`                                 |
| `SCIM_EXTERNAL_ISSUER`     | Expected `iss` claim (e.g., `https://sts.windows.net/<tenant-id>/`) |
| `SCIM_EXTERNAL_AUDIENCE`   | Expected `aud` claim                       |
| `SCIM_EXTERNAL_JWKS_URI`   | URL to the JWKS endpoint for public keys   |

**Example request:**
```
Authorization: Bearer <jwt-token>
```

### External Shared Secret (Bearer Token) Authentication

Validates a static bearer token against a pre-configured hash. The client sends the raw secret as a bearer token, and the server verifies it against the stored hash using Keycloak's password hashing infrastructure.

**Required settings:**

| Setting                      | Value                                                |
|------------------------------|------------------------------------------------------|
| `SCIM_AUTHENTICATION_MODE`   | `EXTERNAL`                                           |
| `SCIM_EXTERNAL_SHARED_SECRET`| Hashed token in PHC String Format (e.g., Argon2id)   |

The hash must be in [PHC String Format](https://github.com/P-H-C/phc-string-format/blob/master/phc-sf-spec.md) using a [Keycloak-supported hash algorithm](https://www.keycloak.org/docs/26.1.5/server_admin/index.html#hashalgorithm) (e.g., `argon2id`, `pbkdf2-sha512`).

**Example:** If the shared secret is `my-secret-token`, hash it using Argon2id and configure the resulting PHC string:
```
SCIM_EXTERNAL_SHARED_SECRET=$argon2id$v=19$m=16,t=2,p=1$<salt>$<hash>
```

### Microsoft Entra ID SCIM Configuration
The client then sends the raw secret:
```
Authorization: Bearer my-secret-token
```

### External Basic Auth Authentication

Validates credentials sent via HTTP Basic Authentication. The client sends a Base64-encoded `username:password` pair, and the server verifies the username against the configured value and the password against a stored hash.

**Required settings:**

| Setting                    | Value                                                |
|----------------------------|------------------------------------------------------|
| `SCIM_AUTHENTICATION_MODE` | `EXTERNAL`                                           |
| `SCIM_BASIC_AUTH_USERNAME`  | Expected username                                    |
| `SCIM_BASIC_AUTH_PASSWORD`  | Password hash in PHC String Format (e.g., Argon2id)  |

The password hash must be in [PHC String Format](https://github.com/P-H-C/phc-string-format/blob/master/phc-sf-spec.md) using a [Keycloak-supported hash algorithm](https://www.keycloak.org/docs/26.1.5/server_admin/index.html#hashalgorithm).

**Example:** If the username is `scim-admin` and password is `my-password`, hash the password using Argon2id and configure:
```
SCIM_BASIC_AUTH_USERNAME=scim-admin
SCIM_BASIC_AUTH_PASSWORD=$argon2id$v=19$m=16,t=2,p=1$<salt>$<hash>
```

The client then sends:
```
Authorization: Basic <base64("scim-admin:my-password")>
```

## Vendor Configuration Guides

### Microsoft Entra ID

Entra ID supports two authentication options when provisioning to this SCIM server:

* SCIM_AUTHENTICATION_MODE enables external authentication support for the SCIM server. In this case the external authentication source will be the Microsoft Entra ID.
* SCIM_EXTERNAL_ISSUER ensures the JWT token was issued by your tenant.
* SCIM_EXTERNAL_AUDIENCE must be exactly 8adf8e6e-67b2-4cf2-a259-e3dc5476c621 — this is the default audience used by Entra ID for non-gallery applications.
* SCIM_EXTERNAL_JWKS_URI allows Keycloak to fetch public keys for token validation.
#### Option A: JWT Authentication (recommended)

Entra ID sends a bearer token signed by Microsoft's identity platform. The SCIM server validates it using Microsoft's JWKS endpoint.

**Keycloak settings:**

| Setting                    | Value                                                                |
|----------------------------|----------------------------------------------------------------------|
| `SCIM_AUTHENTICATION_MODE` | `EXTERNAL`                                                           |
| `SCIM_EXTERNAL_ISSUER`     | `https://sts.windows.net/<your-tenant-id>/`                          |
| `SCIM_EXTERNAL_AUDIENCE`   | `8adf8e6e-67b2-4cf2-a259-e3dc5476c621`                               |
| `SCIM_EXTERNAL_JWKS_URI`   | `https://login.microsoftonline.com/<your-tenant-id>/discovery/v2.0/keys` |

Replace `<your-tenant-id>` with your Azure tenant ID. The audience `8adf8e6e-67b2-4cf2-a259-e3dc5476c621` is the default used by Entra ID for non-gallery applications.

#### Option B: Shared Secret Authentication

Entra ID sends a static bearer token that you generate and configure on both sides.

**Keycloak settings:**

| Setting                      | Value                      |
|------------------------------|----------------------------|
| `SCIM_AUTHENTICATION_MODE`   | `EXTERNAL`                 |
| `SCIM_EXTERNAL_SHARED_SECRET`| `<token_hashed_value>`     |

Replace `<token_hashed_value>` with the PHC String Format hash of your token.

#### Azure Portal Setup

1. Sign in to the [Azure portal](https://portal.azure.com).
2. Go to **Identity > Applications > Enterprise applications**.
3. Click **+ New application > + Create your own application**.
4. Enter a name for your application (e.g., "My Keycloak SCIM").
5. Choose **Integrate any other application you don't find in the gallery**.
6. Click **Create**.
7. In the application's left-hand menu, select **Provisioning**.
8. Click **+ New configuration**.
9. Fill in the following:
   - **Tenant URL** (realm): `https://mykeycloak.example.com/realms/my-realm/scim/v2`
   - **Tenant URL** (organization): `https://mykeycloak.example.com/realms/my-realm/scim/v2/organizations/{organizationId}`
   - **Secret Token**: Leave empty for JWT authentication (Option A), or enter the raw shared secret (Option B).
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

#### Identity Provider Linking with Microsoft Entra ID
16. Go back and open **Provision Microsoft Entra ID Users**.
17. Define mappings. The following are required:
    - `userName`
    - `active`
    - `emails[type eq "work"].value`
    - `name.givenName`
    - `name.familyName`
18. Click **Save**.
19. Go back to **Provisioning**.
20. Set **Provisioning Status** to **On**.
21. Click **Save**.
22. Reload the page to ensure the configuration was saved.
23. Navigate to **Manage > Users and groups > + Add user/group**.
24. Select the user you want to provision and click **Assign**.
25. Navigate to **Provision on demand**.
26. Find the user you just assigned.
27. Click on the user and select **Provision**.
28. Verify that the provisioning completes successfully.

For more information, refer to the [Microsoft Entra ID SCIM provisioning documentation](https://learn.microsoft.com/en-us/entra/identity/saas-apps/tutorial-list).

### Okta

Okta supports three authentication methods when provisioning to a SCIM server. All three are supported by this extension.

For general Okta SCIM setup, refer to the [Okta SCIM provisioning documentation](https://help.okta.com/en-us/content/topics/apps/apps_app_integration_wizard_scim.htm).

#### Option A: HTTP Header (Bearer Token)

Okta sends a static bearer token in the `Authorization` header. This uses the shared secret authentication method.

**Keycloak settings:**

| Setting                      | Value                                    |
|------------------------------|------------------------------------------|
| `SCIM_AUTHENTICATION_MODE`   | `EXTERNAL`                               |
| `SCIM_EXTERNAL_SHARED_SECRET`| PHC String Format hash of your API token |

**Okta setup:**
1. In the Okta Admin Console, go to **Applications > Applications**.
2. Select your SCIM application.
3. Go to the **Provisioning** tab and click **Configure API Integration**.
4. Select **HTTP Header** as the authentication mode.
5. In the **Authorization** field, paste the raw API token (the unhashed value).
6. Set the **SCIM connector base URL** to: `https://mykeycloak.example.com/realms/my-realm/scim/v2`
7. Click **Test API Credentials** to verify.
8. Click **Save**.

#### Option B: Basic Auth

Okta sends credentials via HTTP Basic Authentication (Base64-encoded `username:password`).

**Keycloak settings:**

| Setting                    | Value                                          |
|----------------------------|-------------------------------------------------|
| `SCIM_AUTHENTICATION_MODE` | `EXTERNAL`                                      |
| `SCIM_BASIC_AUTH_USERNAME`  | The username you want Okta to authenticate with |
| `SCIM_BASIC_AUTH_PASSWORD`  | PHC String Format hash of the password           |

**Okta setup:**
1. In the Okta Admin Console, go to **Applications > Applications**.
2. Select your SCIM application.
3. Go to the **Provisioning** tab and click **Configure API Integration**.
4. Select **Basic Auth** as the authentication mode.
5. Enter the **Username** and **Password** (the raw, unhashed values).
6. Set the **SCIM connector base URL** to: `https://mykeycloak.example.com/realms/my-realm/scim/v2`
7. Click **Test API Credentials** to verify.
8. Click **Save**.

#### Option C: OAuth2

Okta obtains an access token from an OAuth2 token endpoint, then sends it as a bearer token. Since Keycloak is itself an OAuth2 provider, you can point Okta at Keycloak's token endpoint. The resulting JWT is then validated by the SCIM server using the JWKS authentication method.

**Keycloak prerequisites:**
1. Create a Keycloak client with **Client Authentication** enabled (confidential client).
2. Enable **Service Accounts Enabled** on the client.
3. Note the client ID and client secret.

**Keycloak settings:**

| Setting                    | Value                                                                              |
|----------------------------|------------------------------------------------------------------------------------|
| `SCIM_AUTHENTICATION_MODE` | `EXTERNAL`                                                                         |
| `SCIM_EXTERNAL_ISSUER`     | `https://mykeycloak.example.com/realms/my-realm`                                   |
| `SCIM_EXTERNAL_AUDIENCE`   | The client ID of the Keycloak client, or `account`                                 |
| `SCIM_EXTERNAL_JWKS_URI`   | `https://mykeycloak.example.com/realms/my-realm/protocol/openid-connect/certs`     |

**Okta setup:**
1. In the Okta Admin Console, go to **Applications > Applications**.
2. Select your SCIM application.
3. Go to the **Provisioning** tab and click **Configure API Integration**.
4. Select **OAuth2** as the authentication mode.
5. Configure the following:
   - **Access Token Endpoint**: `https://mykeycloak.example.com/realms/my-realm/protocol/openid-connect/token`
   - **Client ID**: The Keycloak client ID
   - **Client Secret**: The Keycloak client secret
6. Set the **SCIM connector base URL** to: `https://mykeycloak.example.com/realms/my-realm/scim/v2`
7. Click **Test API Credentials** to verify.
8. Click **Save**.

## Identity Provider Linking

### Identity Provider Linking with Azure Entra ID

Identity Provider linking with Entra ID requires a few additional configuration steps on both the Entra and Keycloak sides.

**Step 1: Add externalId**

In the Keycloak admin console, ensure that you have an `externalId` attribute defined in your **Realm Settings > User Profile**. This attribute is used to store the user's external ID in Keycloak and without it the Identity Provider linking will fail.

**Step 2: Map externalId in SCIM provisioning**

In Entra ID, make sure that the `objectId` from Entra ID is mapped into the SCIM `externalId` field:

1. Navigate to your **Enterprise Application > Provisioning > Attribute Mapping (Preview) > Provision Microsoft Entra ID Users**.
2. Click **Add New Mapping**.
3. Set:
   - **Source attribute**: `objectId`
   - **Target attribute**: `externalId`
4. Click **Save**.

This ensures that during SCIM provisioning, the Entra `objectId` is stored in Keycloak as the user's `externalId`, which will later be used for identity linking.

**Step 3: Configure Keycloak Identity Provider to Use Object ID**

Configure your Entra ID Identity Provider in Keycloak to use the `oid` claim from the login token instead of the default `sub` claim (which is app-specific).

1. Navigate to **Identity Providers** > select your **Entra ID provider**.
2. Go to the **Mappers** tab.
3. Click **Add Mapper**.
4. Fill in the mapper details:
   - **Name**: `map_oid_as_brokerid` (or any descriptive name)
   - **Sync Mode**: Force
   - **Mapper Type**: Username Template Importer
   - **Template**: `${CLAIM.oid}`
   - **Target**: BROKER_ID
5. Click **Save**.

This mapper tells Keycloak to use the Entra `oid` claim as the Broker ID, ensuring that the login user is matched correctly with the SCIM-provisioned user.

**Step 4: Enable Identity Provider Linking in SCIM**

Add the following settings to your SCIM configuration:

```
SCIM_LINK_IDP=true
```

If you want to link users to a realm-level identity provider, also add:

```
SCIM_IDENTITY_PROVIDER_ALIAS=<your-idp-alias>
```

This ensures that when a user is provisioned via SCIM, a corresponding Identity Provider link is created automatically based on the `externalId` / `oid`.

## SCIM-Managed Users

By default, the SCIM server only exposes users who are explicitly assigned the `scim-managed` role within the realm. This ensures that only users intended to be managed through SCIM are returned or modifiable via SCIM API operations.

This prevents accidental exposure or modification of users that were:
- created manually via the Keycloak admin UI
- imported from external identity providers
- or otherwise not intended to be managed through SCIM

If you want to expose all users (i.e., bypass filtering), you can simply assign the `scim-managed` role to every user. This effectively disables the filter, making the SCIM behavior equivalent to an unfiltered list.

This role-based filtering applies to all SCIM operations, including:
- `GET /Users`
- `PATCH /Users/{id}`
- `DELETE /Users/{id}`

Users without the `scim-managed` role will be invisible to SCIM clients -- they won't be listed, updated, or removed through SCIM.

This filtering mechanism is designed to improve safety, especially in complex deployments involving federated users, legacy accounts, or overlapping identity sources (such as Entra ID + local users).

This design does mean that provisioning a user through SCIM who previously existed without the role may cause conflicts or provisioning failures if role assignment isn't handled correctly. However, this is a deliberate design choice to provide fine-grained control over which users are SCIM-visible.


## User attributes for SCIM provisioning

This section explains how to provision custom user attributes (e.g., `job`, `department`, `employeeId`) from an external
identity provider (such as Microsoft Entra ID) into Keycloak via SCIM.

By default, the SCIM server only exposes built-in user attributes (`userName`, `email`, `name.givenName`,
`name.familyName`, `active`). To provision additional custom attributes, you need to configure Keycloak to accept
unmanaged attributes and define identity provider mappers that tell the SCIM server which attributes to expose.

### Prerequisites

Before custom attributes can be provisioned, ensure the following conditions are met:

1. **Unmanaged Attribute Policy**: The realm's User Profile must have `UnmanagedAttributePolicy` set to `ENABLED`. This
   allows Keycloak to store attributes that are not explicitly defined in the User Profile schema.

2. **Identity Provider Alias**: The `SCIM_IDENTITY_PROVIDER_ALIAS` environment variable (or realm/organization
   attribute) must be configured with the alias of your identity provider.

3. **Identity Provider Mappers**: User attribute mappers must be configured on the identity provider to define which
   attributes should be provisioned.

### How It Works

When a SCIM provisioning request is received, the SCIM server:

1. Checks if `UnmanagedAttributePolicy` is set to `ENABLED` in the realm
2. Looks up the identity provider specified by `SCIM_IDENTITY_PROVIDER_ALIAS`
3. Reads all user attribute mappers configured on that identity provider
4. Exposes those mapped attributes as valid SCIM attributes that can be provisioned

This means the identity provider mappers serve as the **source of truth** for which custom attributes are available via
SCIM.

### Step-by-Step Configuration

#### Step 1: Enable Unmanaged Attributes in Keycloak

1. Navigate to **Realm Settings** > **User Profile**
2. Click **JSON Editor**
3. Add or update the `unmanagedAttributePolicy` field:

```json
{
  "unmanagedAttributePolicy": "ENABLED",
  "attributes": [
  ]
}
```

4. Click **Save**

#### Step 2: Configure the Identity Provider Alias

Add the following environment variable to your Keycloak server:

```bash
SCIM_IDENTITY_PROVIDER_ALIAS=<your-idp-alias>
```

Or set it as a realm attribute via the Admin API:

```json
{
  "attributes": {
    "scim.identity.provider.alias": "<your-idp-alias>"
  }
}
```

The alias must match the alias of your configured identity provider (e.g., `entra-id`, `keycloak-oidc`).

#### Step 3: Create User Attribute Mappers on the Identity Provider

For each custom attribute you want to provision via SCIM:

1. Navigate to **Identity Providers** > select your provider (e.g., Entra ID)
2. Go to the **Mappers** tab
3. Click **Add Mapper**
4. Configure the mapper:

| Field              | Value                                                   |
|--------------------|---------------------------------------------------------|
| **Name**           | A descriptive name (e.g., `map-job-attribute`)          |
| **Sync Mode**      | `INHERIT` or `FORCE`                                    |
| **Mapper Type**    | `Attribute Importer`                                    |
| **Claim** (OIDC)   | The claim name from the external IdP (e.g., `jobTitle`) |
| **User Attribute** | The Keycloak attribute name (e.g., `job`)               |

5. Click **Save**
6. Repeat for each attribute you want to provision (e.g., `department`, `employeeId`)

#### Step 4: Map Attributes in Your SCIM Client (e.g., Entra ID)

In your SCIM client (e.g., Microsoft Entra ID Enterprise Application):

1. Navigate to **Provisioning** > **Attribute Mapping (Preview)** > **Provision Microsoft Entra ID Users**
2. Click **Add New Mapping**
3. Map the source attribute to the custom SCIM attribute:

| Source Attribute | Target Attribute |
|------------------|------------------|
| `jobTitle`       | `job`            |
| `department`     | `department`     |

4. Click **Save**

The target attribute name must match the `User Attribute` value configured in the Keycloak identity provider mapper.

### Example: Provisioning a "job" Attribute

This example shows how to provision the `jobTitle` attribute from Microsoft Entra ID to Keycloak as a `job` attribute.

**Keycloak Configuration:**

1. Enable `UnmanagedAttributePolicy` in the realm's User Profile
2. Set `SCIM_IDENTITY_PROVIDER_ALIAS=entra-id`
3. Create an identity provider mapper:
    - **Mapper Type**: Attribute Importer
    - **Claim**: `jobTitle`
    - **User Attribute**: `job`

**Microsoft Entra Configuration:**

1. In the Enterprise Application provisioning settings, add a mapping:
    - **Source attribute**: `jobTitle`
    - **Target attribute**: `job`

When Entra ID provisions a user, the `job` attribute will be stored in Keycloak and available on the user's attributes.

## License

[Apache License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0)

---

<div id="metatavu-custom-footer"><div align="center">
    <img src="https://metatavu.fi/wp-content/uploads/2024/02/cropped-metatavu-favicon.jpg" alt="Organization Logo" width="100">
    <p>&copy; 2025 Metatavu. All rights reserved.</p>
    <p>
        <a href="https://www.metatavu.fi">Website</a> |
        <a href="https://twitter.com/metatavu">Twitter</a> |
        <a href="https://fi.linkedin.com/company/metatavu">LinkedIn</a>
    </p>
</div></div>
