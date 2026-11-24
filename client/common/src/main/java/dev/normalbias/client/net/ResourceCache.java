package dev.normalbias.client.net;

import java.util.*;

/** Bounded browse metadata plus explicitly pinned session-queue resources. EDT-owned. */
final class ResourceCache {
    private final Map<String, Map<String, Object>> recent = new LinkedHashMap<>();
    private final Map<String, Map<String, Object>> pinned = new HashMap<>();
    Map<String, Object> get(String id) { return pinned.containsKey(id) ? pinned.get(id) : recent.get(id); }
    boolean containsKey(String id) { return get(id) != null; }
    void put(String id, Map<String, Object> value) {
        if (id == null) return;
        recent.remove(id); recent.put(id, value);
        if (pinned.containsKey(id)) pinned.put(id, value);
        while (recent.size() > 150) recent.remove(recent.keySet().iterator().next());
    }
    void retain(Collection<String> ids) {
        pinned.keySet().retainAll(ids);
        for (String id : ids) if (recent.containsKey(id)) pinned.put(id, recent.get(id));
    }
    void clear() { recent.clear(); pinned.clear(); }
    int size() { Set<String> ids = new HashSet<>(recent.keySet()); ids.addAll(pinned.keySet()); return ids.size(); }
}
