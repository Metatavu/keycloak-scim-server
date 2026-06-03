```
curl -sS -X GET "https://keycloak.pilot1.sram.surf.nl/realms/test/scim/v2/Users?startIndex=1&count=100" \ 
  -H "Accept: application/scim+json" \
  -H "Authorization: Bearer 1765a236-850a-4ee3-840b-a3f11869435e" |jq
{
  "totalResults": 1,
  "startIndex": 1,
  "itemsPerPage": 100,
  "Resources": [
    {
      "id": "6794995e-e862-4208-aadf-5f0bf411b29d",
      "userName": "testadmin",
      "name": {
        "givenName": "Test",
        "familyName": "Admin"
      },
      "emails": [
        {
          "value": "testadmin@example.com",
          "primary": true
        }
      ],
      "active": true,
      "schemas": [
        "urn:ietf:params:scim:schemas:core:2.0:User"
      ],
      "meta": {
        "location": "https://keycloak.pilot1.sram.surf.nl/realms/test/scim/v2/Users/6794995e-e862-4208-aadf-5f0bf411b29d",
        "resourceType": "User",
        "created": 1742947200000,
        "lastModified": 1743033600000
      }
    }
  ]
}
```