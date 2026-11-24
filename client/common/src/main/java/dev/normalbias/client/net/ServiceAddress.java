package dev.normalbias.client.net;

/** Runtime service selection. Unlike TrialOrigin, this is not a private-build gate. */
public final class ServiceAddress {
    private ServiceAddress() { }

    public static String normalize(String value) {
        if (value == null) throw invalid();
        String address = value.trim();
        if (address.endsWith("/")) address = address.substring(0, address.length() - 1);
        if (!address.startsWith("https://")) throw invalid();
        String authority = address.substring(8).toLowerCase();
        if (authority.isEmpty() || authority.length() > 260) throw invalid();
        for (int i = 0; i < authority.length(); i++) {
            char c = authority.charAt(i);
            if (!(c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '.' || c == '-' || c == ':' || c == '[' || c == ']')) throw invalid();
        }
        int colon;
        String host;
        if (authority.startsWith("[")) {
            int close = authority.indexOf(']');
            if (close < 0) throw invalid();
            host = authority.substring(0, close + 1);
            validateIpv6(host.substring(1, host.length() - 1));
            if (close + 1 == authority.length()) colon = -1;
            else {
                colon = close + 1;
                if (authority.charAt(colon) != ':') throw invalid();
            }
        } else {
            colon = authority.indexOf(':');
            host = colon < 0 ? authority : authority.substring(0, colon);
            if (host.length() > 253) throw invalid();
            for (String label : host.split("\\.", -1)) {
                if (label.isEmpty() || label.length() > 63 || label.startsWith("-") || label.endsWith("-")) throw invalid();
                for (int i = 0; i < label.length(); i++) {
                    char c = label.charAt(i);
                    if (!(c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '-')) throw invalid();
                }
            }
            boolean numeric = true;
            for (int i = 0; i < host.length(); i++) if (!(host.charAt(i) >= '0' && host.charAt(i) <= '9' || host.charAt(i) == '.')) numeric = false;
            if (numeric) validateIpv4(host);
        }
        String port = "";
        if (colon >= 0) {
            String raw = authority.substring(colon + 1);
            if (raw.isEmpty() || raw.length() > 5) throw invalid();
            for (int i = 0; i < raw.length(); i++) if (raw.charAt(i) < '0' || raw.charAt(i) > '9') throw invalid();
            int number = Integer.parseInt(raw);
            if (number < 1 || number > 65535) throw invalid();
            if (number != 443) port = ":" + number;
        }
        return "https://" + host + port;
    }

    /** HTTP is accepted only when it is the unchanged, explicit development origin. */
    public static String selected(String value, String developmentOrigin) {
        String trimmed = value == null ? "" : value.trim();
        String development = developmentOrigin == null ? "" : developmentOrigin;
        if (development.endsWith("/")) development = development.substring(0, development.length() - 1);
        if (trimmed.endsWith("/")) trimmed = trimmed.substring(0, trimmed.length() - 1);
        if (development.startsWith("http://") && trimmed.equals(development)) return trimmed;
        return normalize(value);
    }

    private static void validateIpv4(String host) {
        String[] octets = host.split("\\.", -1);
        if (octets.length != 4) throw invalid();
        for (String octet : octets) {
            if (octet.isEmpty() || octet.length() > 3 || octet.length() > 1 && octet.startsWith("0")) throw invalid();
            for (int i = 0; i < octet.length(); i++) if (octet.charAt(i) < '0' || octet.charAt(i) > '9') throw invalid();
            if (Integer.parseInt(octet) > 255) throw invalid();
        }
    }
    private static void validateIpv6(String host) {
        int compression = host.indexOf("::");
        if (compression >= 0 && host.indexOf("::", compression + 2) >= 0 || host.indexOf(':') < 0) throw invalid();
        if (host.startsWith(":") && !host.startsWith("::") || host.endsWith(":") && !host.endsWith("::")) throw invalid();
        int groups = 0;
        String[] parts = host.split(":", -1);
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part.isEmpty()) continue;
            if (part.indexOf('.') >= 0) {
                if (i != parts.length - 1) throw invalid();
                validateIpv4(part); groups += 2; continue;
            }
            if (part.length() > 4) throw invalid();
            for (int j = 0; j < part.length(); j++) {
                char c = part.charAt(j);
                if (!(c >= '0' && c <= '9' || c >= 'a' && c <= 'f')) throw invalid();
            }
            groups++;
        }
        if (host.contains(":::") || (compression >= 0 ? groups >= 8 : groups != 8)) throw invalid();
    }

    private static IllegalArgumentException invalid() {
        return new IllegalArgumentException("Enter an HTTPS service address, such as https://music.example.com, with an optional port and no path.");
    }
}
