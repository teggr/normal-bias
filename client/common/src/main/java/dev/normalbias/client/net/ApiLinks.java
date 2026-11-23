package dev.normalbias.client.net;

import java.util.Map;

/** Only rooted API links on the configured service may receive credentials. */
public final class ApiLinks {
    private ApiLinks() { }
    public static String href(Map<?, ?> representation, String relation, String method) {
        Object raw = representation == null ? null : representation.get("_links");
        Object action = raw instanceof Map ? ((Map<?, ?>) raw).get(relation) : null;
        if (!(action instanceof Map)) return null;
        Map<?, ?> link = (Map<?, ?>) action;
        if (!method.equals(link.get("method"))) return null;
        String href = (String) link.get("href");
        return checked(href);
    }
    public static String checked(String href) {
        if (href == null || !(href.equals("/api") || href.startsWith("/api/")) || href.contains("\\")
                || href.contains("#") || href.contains("//")) throw new IllegalArgumentException("Invalid API link");
        for (int i = 0; i < href.length(); i++) if (href.charAt(i) <= 32 || href.charAt(i) == 127) throw new IllegalArgumentException("Invalid API link");
        String path = href.split("\\?", 2)[0].toLowerCase();
        if (path.contains("/../") || path.endsWith("/..") || path.contains("/./") || path.contains("%")) throw new IllegalArgumentException("Invalid API path");
        return href;
    }
}
