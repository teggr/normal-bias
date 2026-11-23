package dev.normalbias.api;

import org.springframework.security.core.Authentication;
import java.util.*;

/** One policy supplies both server enforcement and advertised action affordances. */
final class AuthPolicy {
    enum Action { LISTEN, UPLOAD, MANAGE, INSPECT, SESSION }
    record Identity(String credentialId, String accountId, String username, String role, String kind,
                    long idleExpires, long absoluteExpires) { }
    record Link(String href, String method) { }
    static Identity identity(Authentication authentication) {
        return authentication != null && authentication.getPrincipal() instanceof Identity value ? value : null;
    }
    static boolean allows(Identity identity, Action action) {
        if (identity == null || identity.kind().equals("GRANT")) return false;
        if (identity.kind().equals("UPLOAD")) return action == Action.UPLOAD;
        return action == Action.LISTEN || action == Action.SESSION || identity.role().equals("OWNER");
    }
    static Link link(String href, String method) { return new Link(href, method); }
    private AuthPolicy() { }
}
