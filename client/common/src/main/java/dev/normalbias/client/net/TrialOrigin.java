package dev.normalbias.client.net;

import com.codename1.ui.CN;
import java.io.InputStream;
import java.io.InputStreamReader;

/** Explicit build-time address for the private, unauthenticated iPhone trial. */
public final class TrialOrigin {
    private TrialOrigin() { }

    public static String bundled() {
        try (InputStream input = CN.getResourceAsStream("/normalbias-trial-origin.txt")) {
            if (input == null) return null;
            InputStreamReader reader = new InputStreamReader(input, "UTF-8");
            StringBuilder text = new StringBuilder();
            int next;
            while ((next = reader.read()) != -1 && text.length() < 256) text.append((char) next);
            String origin = text.toString().trim();
            return valid(origin) ? origin : null;
        } catch (Exception failure) { return null; }
    }

    public static boolean valid(String origin) {
        if (origin == null || !origin.startsWith("http://")) return false;
        String address = origin.substring(7);
        int colon = address.indexOf(':');
        if (colon < 0 || address.indexOf(':', colon + 1) >= 0) return false;
        String host = address.substring(0, colon);
        String portText = address.substring(colon + 1);
        if (!digits(portText) || portText.length() > 5) return false;
        int port = Integer.parseInt(portText);
        if (port < 1024 || port > 65535) return false;
        int[] octets = new int[4];
        int start = 0;
        for (int i = 0; i < 4; i++) {
            int end = i == 3 ? host.length() : host.indexOf('.', start);
            if (end < start) return false;
            String part = host.substring(start, end);
            if (!digits(part) || part.length() > 3 || part.length() > 1 && part.charAt(0) == '0') return false;
            octets[i] = Integer.parseInt(part);
            if (octets[i] > 255) return false;
            start = end + 1;
        }
        return octets[0] == 10 || octets[0] == 172 && octets[1] >= 16 && octets[1] <= 31
                || octets[0] == 192 && octets[1] == 168;
    }

    private static boolean digits(String value) {
        if (value.isEmpty()) return false;
        for (int i = 0; i < value.length(); i++) if (value.charAt(i) < '0' || value.charAt(i) > '9') return false;
        return true;
    }
}
