package dev.normalbias.client.net;

/** A transient one-time link. Never persist this value or include it in diagnostics. */
public final class AccountLink {
    public final boolean invitation;
    public final String token;
    private AccountLink(boolean invitation, String token) { this.invitation = invitation; this.token = token; }
    public static AccountLink parse(String value, String origin) {
        String prefix = origin + "/#";
        if (!origin.startsWith("https://") || value == null || !value.startsWith(prefix)) throw new IllegalArgumentException("Use an HTTPS invitation or reset link from this service.");
        String fragment = value.substring(prefix.length());
        boolean invite = fragment.startsWith("invite=");
        if (!invite && !fragment.startsWith("reset=")) throw new IllegalArgumentException("Use an invitation or reset link.");
        String token = fragment.substring(invite ? 7 : 6);
        if (token.length() != 64) throw new IllegalArgumentException("This link is incomplete or invalid. Ask the owner for a new link.");
        for (int i = 0; i < token.length(); i++) if ("0123456789abcdef".indexOf(token.charAt(i)) < 0) throw new IllegalArgumentException("This link is incomplete or invalid. Ask the owner for a new link.");
        return new AccountLink(invite, token);
    }
}
