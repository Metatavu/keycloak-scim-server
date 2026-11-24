# Keycloak SCIM 2.0 Extension

This project provides a **SCIM 2.0-compliant extension** for [Keycloak](https://www.keycloak.org/), enabling SCIM-based user and group provisioning. It supports:

- **Realm-level SCIM APIs**:
  `/realms/{realm}/scim/v2`
- **Organization-level SCIM APIs** (Keycloak 26+ with Organizations):
  `/realms/{realm}/scim/v2/organizations/{organizationId}`

## Table of Contents

- [Version Compatibility](#version-compatibility)
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
- [Group responses and large groups](#group-responses-and-large-groups)
- [Organization groups](#organization-groups)
- [SCIM-Managed Users](#scim-managed-users)
- [License](#license)

## Version Compatibility

| Plugin Version | Keycloak Version        | Status |
|---|-------------------------|---|
| 1.5.0 | 26.3.x (tested: 26.3.5) | Stable |
| 1.6.0 | 26.3.x (tested: 26.3.5) | Stable |
| 1.7.0 | 26.6.x (tested: 26.6.3) | Stable |
| 1.8.0 | 26.6.x (tested: 26.6.3) | Stable |
| 1.9.0 | 26.6.x (tested: 26.6.3) | Stable |
| 1.9.1 | 26.8.x (tested: 26.8.2) | Stable |
| 1.9.2 | 26.8.x (tested: 26.8.2) | In development |

> **Note:** Organization-level SCIM APIs require Keycloak **26+** with the Organizations feature enabled.

### Upgrading to 1.10.0

Organization-level `/Groups` endpoints now manage Keycloak organization groups; before, they answered
`501 Not Implemented`. See [Organization groups](#organization-groups).

The realm-level `/Groups` endpoints now only work with realm groups. Organization groups, including the internal group
Keycloak keeps for every organization, are no longer listed and answer `404` on `GET`, `PUT`, `PATCH` and `DELETE`
`/Groups/{id}`. Before, a realm-level SCIM client could read, rename or delete an organization group whose id it knew.

`POST /Groups` with a `displayName` that another group at the same level already uses still answers `409 Conflict`, but
now with a SCIM Error body instead of Keycloak's own error format. The `Location` header of a created group is now the
absolute URL from `meta.location`.

### Upgrading to 1.9.2

1.9.2 is a bug-fix release with security fixes. Several fixes change responses that clients
may have relied on, so read the notes below before upgrading. No configuration changes are
needed.

In external token authentication mode (`SCIM_EXTERNAL_JWKS_URI`), the server now checks
the token's `exp` and `nbf` claims, with 60 seconds of allowed clock skew. Expired tokens,
tokens that are not yet valid and tokens without an `exp` claim are rejected with
`401 Unauthorized`. Previously any token signed by the configured JWKS with the right
issuer and audience was accepted indefinitely. Tokens without an `iss` or `aud` claim are
now rejected with `401` instead of failing with `500` (unless the corresponding setting is
the `*` wildcard). Make sure your SCIM client sends tokens that carry an `exp` claim.

Realm-level `GET /Users?filter=<attribute> eq "<value>"` now returns only exact matches.
Before, a single `eq` comparison on `userName`, `email`, `name.givenName`,
`name.familyName` or a user profile attribute was a substring match, so
`userName eq "bob"` also returned `bobby`. Comparisons stay case-insensitive, as the
user schema advertises `caseExact: false` for every attribute (`userName eq "BOB"`
still finds `bob`). Organization-level user lists already matched exactly.

`GET`, `PUT` and `PATCH` on `/Users/{id}` now apply the same scope as `GET /Users`: a user that SCIM cannot
list (no `scim-managed` role while `SCIM_MANAGE_ALL_USERS` is off, a client service account, or, in
organization mode, a user that is not a member of the organization) answers `404 User not found`, as if it
did not exist. Before 1.9.2 any realm user could be read and modified by id, so a SCIM client could for
example deactivate or rename an administrator, and an organization's SCIM client could modify users of
other organizations. Clients that relied on this now get `404`: assign the `scim-managed` role to those
users, or enable `SCIM_MANAGE_ALL_USERS`. `DELETE /Users/{id}` is unchanged and still answers `403` for users SCIM does not manage.

List endpoints (`GET /Users` and `GET /Groups`) now treat `startIndex` as
1-based, as RFC 7644 §3.4.2.4 defines it. Before 1.9.2 it was used as a
0-based offset, so a client paging from `startIndex=1` never received the
first resource of a listing, and a group lookup such as
`filter=displayName eq "Engineering"&startIndex=1` returned no group at all
(the offset was applied twice). A missing `startIndex`, or one below 1, means
the first page, so clients that sent `startIndex=0` get the same first page as
before. A client that paged with 0-based offsets beyond the first page
(`startIndex=0`, then `count`, then `2 * count`) now gets each page shifted by
one resource and should switch to 1-based indexes.

List responses now report the 1-based `startIndex` and set `itemsPerPage` to the
number of resources on the page rather than the requested `count`. A `count`
above 200, the `maxResults` the server advertises in `/ServiceProviderConfig`,
is capped to 200.

Group endpoints now follow the same managed-user rule as `GET /Users` (see
[Which users SCIM can see](#which-users-scim-can-see)). Group responses only list members that
SCIM manages, and SCIM can only add or remove those members: a `PATCH` that references a user
without the `scim-managed` role, or a service account, is rejected with `400` like an unknown
id, and `POST`/`PUT` drop such references as they already did for unknown ids. A `PUT`, or a
`PATCH` that replaces `members`, no longer removes members that were added outside SCIM. If a
SCIM client relied on putting users created in the admin console into groups, grant those users
the `scim-managed` role or enable `SCIM_MANAGE_ALL_USERS`.

Group `PATCH` requests are now applied as a whole or not at all (RFC 7644 §3.5.2).
When any operation in a multi-operation `PATCH /Groups/{id}` fails, the earlier
operations are rolled back, no membership admin events are emitted, and the
response is the error. An `add` or `replace` operation without a `value` now fails
the request with `400 invalidValue`; before, it silently skipped that operation and
every operation after it while still answering `204`.

### Upgrading to 1.9.1

1.9.1 adds support for Keycloak 26.8. Keycloak 26.8 moved the domain-to-identity-provider
mapping from the identity provider's `kc.org.domain` config to the organization domain
itself, which broke organization-level identity provider linking for SCIM-provisioned
users (`linkUserIdp` logged `No identity provider found for email domain` and the user
landed on the "Account already exists" page). The server now resolves the identity
provider through the organization domain and falls back to `kc.org.domain` on older
Keycloak versions, so no configuration changes are needed. The Docker image and the
test suite now target Keycloak 26.8.2.

### Upgrading to 1.9.0

`DELETE /Users/{id}` now fails closed when the `scim-managed` realm role does
not exist: every delete is rejected with `403` until the role is created. Before
1.9.0 a missing role let every delete through. Realms that provision users
through SCIM already have the role, since the server grants it on every SCIM
create, so this only affects realms where the role was removed by hand.

1.9.0 also adds the opt-in `SCIM_MANAGE_ALL_USERS` setting, which lets SCIM list
and delete users that were not created through SCIM (see
[Which users SCIM can see](#which-users-scim-can-see)), and accepts the
filtered `emails[type eq "work"].value` PATCH paths that Microsoft Entra ID
sends for email updates.

### Upgrading to 1.8.0

`PATCH /Groups/{id}` now returns **`204 No Content`** instead of `200` with the
full group representation. This is a breaking change for any client that read
the member list out of the PATCH response.

The reason is cost: group membership is provisioned one member at a time, so
returning the whole group on every request made provisioning a group O(n²) and
could exhaust the heap of a Keycloak serving a large group.

If you need the updated resource back, name the attributes you want and the
server answers `200` with a body, as RFC 7644 §3.5.2 requires:

```http
PATCH /Groups/{id}?attributes=members
```

Group reads also now honour `attributes` and `excludedAttributes` (RFC 7644
§3.9). A plain `GET /Groups/{id}` is unchanged and still returns the full
membership. See [Group responses and large groups](#group-responses-and-large-groups).

## Prerequisites

- **Keycloak**: See the [Version Compatibility](#version-compatibility) matrix above for supported versions.
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
| `SCIM_MANAGE_ALL_USERS`      | Exposes every realm user through SCIM instead of only users created through SCIM. See [Which users SCIM can see](#which-users-scim-can-see). Defaults to `false`.                                                          |

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
    "scim.identity.provider.alias": "string",
    "scim.manage.all.users": "true|false"
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
| `SCIM_MANAGE_ALL_USERS`      | Exposes every organization member through SCIM instead of only members created through SCIM. See [Which users SCIM can see](#which-users-scim-can-see). Defaults to `false`.|

### Which users SCIM can see

By default the SCIM API only works with users it created itself. Every user created through SCIM is granted the
`scim-managed` realm role, and users without that role are hidden from `GET /Users` and rejected with `403` on
`DELETE /Users/{id}`. Users that already existed in Keycloak, or that were created through the admin console, are
therefore not listed and cannot be deleted through SCIM, and `GET`, `PUT` and `PATCH` on `/Users/{id}` answer `404`
for them. In organization mode the user must also be a member of the organization.

Set `SCIM_MANAGE_ALL_USERS` (or the `scim.manage.all.users` realm attribute) to `true` to let the SCIM client see and
manage those users as well:

* `GET /Users` lists every user in the realm (or every member of the organization), with filters and paging applied as usual.
* `GET`, `PUT` and `PATCH` on `/Users/{id}` work for any user in the realm (or any member of the organization).
* `DELETE /Users/{id}` is accepted for any user. In realm mode the user is deleted. In organization mode the user is removed
  from the organization: Keycloak deletes the account of a member the organization owns (created through SCIM or an
  organization identity provider) and only drops the membership of a member that was added from the realm.
* Group members follow the same rule: only users SCIM can see are listed in group responses or can be added to and
  removed from groups through SCIM. Other members are left in place on `PUT` or a `members` replace.
* Service account users of clients are never listed, read, updated, deleted or put into groups through SCIM, regardless of this setting.

> **Caution:** with this setting enabled, a deprovisioning rule on the identity provider side can delete any realm user,
> including administrators and users from federated user storage such as LDAP (the delete is passed on to the storage
> provider). Make sure the SCIM client's user assignment is scoped correctly before enabling it.

The organization attribute is read independently of the realm attribute and the environment variable: enabling the setting
on a realm does not enable it for the organizations in that realm.

Users created through SCIM still receive the `scim-managed` role, so switching the setting back to `false` restores the
default scoping.

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

## Group responses and large groups

Group membership is provisioned one member at a time: an identity provider that
adds 10,000 users to a group sends 10,000 separate `PATCH /Groups/{id}`
requests. Returning the whole group on each of those makes the cost of
provisioning a group quadratic in its size, which is enough to exhaust a
Keycloak container's heap on a group of a few tens of thousands.

Group responses are therefore kept proportional to what the client asked for:

### `PATCH /Groups/{id}` answers `204 No Content`

A successful group PATCH returns no body. RFC 7644 §3.5.2 allows this
explicitly, and Microsoft Entra ID documents it as the response it expects
("Returning a body with a list of all the members isn't advisable"). Okta
accepts it as well.

To get the updated resource back, name the attributes you want:

```http
PATCH /Groups/{id}?attributes=members
```

That returns `200 OK` with a body carrying `id`, `schemas`, `meta` and the
attributes named — which RFC 7644 §3.5.2 requires whenever `attributes` is
supplied.

### Group reads honour `attributes` and `excludedAttributes`

`GET /Groups` and `GET /Groups/{id}` both support the two attribute-selection
parameters from RFC 7644 §3.9. The useful one for large groups is:

```http
GET /Groups/{id}?excludedAttributes=members
GET /Groups?excludedAttributes=members&filter=displayName eq "Engineering"
```

Both return the group without its membership, at a cost independent of how many
members it has. Entra ID sends this form on every group read.

The parameters are mutually exclusive; supplying both returns `400` with a SCIM
Error body.

Note that a plain `GET /Groups/{id}` with neither parameter still returns the
full membership: `members` is `"returned": "default"` in the core Group schema
(RFC 7643 §8.7.1), so omitting it by default would be non-conformant. Clients
reading very large groups should pass `excludedAttributes=members`.

## Organization groups

On an organization endpoint, `/realms/{realm}/scim/v2/organizations/{organizationId}/Groups` manages the Keycloak
organization groups of that organization. Requests, filters, paging, attribute selection and `PATCH` behave as on the realm endpoint.

* `POST /Groups` creates a top-level organization group. SCIM has no notion of group hierarchy, so groups are created
  flat; a `displayName` already used by another top-level group of the organization answers `409`.
* `GET /Groups` lists every group of the organization, including subgroups created in the admin console. The same group
  name may exist in several organizations; each endpoint only sees its own.
* A group of another organization, a realm group and the internal group Keycloak keeps for every organization answer
  `404` on `GET`, `PUT`, `PATCH` and `DELETE /Groups/{id}`.
* Only members of the organization can join its groups. On top of the [managed-user rule](#which-users-scim-can-see), a
  `PATCH` that references a user outside the organization is rejected with `400` like an unknown id, and `POST`/`PUT`
  drop such references. Group responses only list members of the organization.
* Admin events use the resource types `ORGANIZATION_GROUP` and `ORGANIZATION_GROUP_MEMBERSHIP` with the paths of the
  Keycloak admin API (`organizations/{organizationId}/groups/{groupId}[/members/{userId}]`), as Keycloak itself does for
  changes made in the admin console.

When a user leaves the organization, Keycloak removes them from the organization's groups.


## SCIM-Managed Users

By default, the SCIM server only exposes users who are explicitly assigned the `scim-managed` role within the realm. This ensures that only users intended to be managed through SCIM are returned or modifiable via SCIM API operations.

This prevents accidental exposure or modification of users that were:
- created manually via the Keycloak admin UI
- imported from external identity providers
- or otherwise not intended to be managed through SCIM

If you want to expose all users, set `SCIM_MANAGE_ALL_USERS` (or the `scim.manage.all.users` realm attribute) to `true` as
described in [Which users SCIM can see](#which-users-scim-can-see). Alternatively, assign the `scim-managed` role to every
user that should be reachable through SCIM.

This role-based filtering applies to:
- `GET /Users`
- `GET`, `PUT` and `PATCH` on `/Users/{id}` (users outside the scope answer `404`)
- `DELETE /Users/{id}` (users SCIM does not manage answer `403`)
- group members in `/Groups` responses and membership changes through `/Groups`

The `scim-managed` role must exist in the realm. Without it, `GET /Users` fails and `DELETE /Users/{id}` is rejected for every user.

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
