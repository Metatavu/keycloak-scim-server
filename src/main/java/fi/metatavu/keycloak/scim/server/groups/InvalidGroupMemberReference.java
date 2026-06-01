package fi.metatavu.keycloak.scim.server.groups;

/**
 * Thrown when a SCIM PATCH on a Group references one or more member IDs that
 * do not resolve to a user in the realm. The entire patch is rejected without
 * mutating membership, so the client receives an actionable 400 instead of an
 * empty / truncated group with HTTP 200.
 */
public class InvalidGroupMemberReference extends Exception {

    private static final long serialVersionUID = 1L;

    public InvalidGroupMemberReference(String memberId) {
        super("Unknown group member: " + memberId);
    }
}
