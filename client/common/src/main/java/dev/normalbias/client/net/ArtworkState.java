package dev.normalbias.client.net;

import java.util.*;
import java.util.function.Consumer;

/** EDT-owned artwork state. Time and transport are injected for deterministic race tests. */
final class ArtworkState<T> {
    interface Clock { long now(); }
    interface Completion<T> { void complete(T value, boolean definitive); }
    interface Loader<T> { void load(String key, Completion<T> done); }
    private static final long REQUEST_MS=25000, MISSING_MS=30000, FAILURE_MS=5000;
    private final Clock clock;
    private final Consumer<RuntimeException> error;
    private final Map<String, Entry<T>> cache=new LinkedHashMap<>();
    private final Map<String, Pending<T>> pending=new HashMap<>();
    private String revision="0";
    private long serial, revisionRequest, revisionStarted, lastRevisionAttempt=-1;
    private static final class Entry<T> {
        final T value; final long expires;
        Entry(T value,long expires) { this.value=value;this.expires=expires; }
    }
    private static final class Pending<T> {
        final long expires;
        final List<Consumer<T>> listeners=new ArrayList<>();
        Pending(long expires) { this.expires=expires; }
    }
    ArtworkState(Clock clock, Consumer<RuntimeException> error) { this.clock=clock;this.error=error; }
    String key(String id) { return id+"?v="+revision; }
    long beginRevision(boolean force) {
        expirePending();
        long now=clock.now();
        if (!force && revisionRequest!=0 && now-revisionStarted<REQUEST_MS) return 0;
        if (!force && lastRevisionAttempt>=0 && now-lastRevisionAttempt<10000) return 0;
        revisionStarted=now;lastRevisionAttempt=now;
        return revisionRequest=++serial;
    }
    void revisionFailed(long token) { if (revisionRequest==token) revisionRequest=0; }
    boolean revisionLoaded(long token,String next) {
        if (revisionRequest!=token) return false;
        revisionRequest=0;
        if (next==null || next.isEmpty() || next.length()>64 || revision.equals(next)) return false;
        revision=next;cache.clear();
        var obsolete=new ArrayList<>(pending.values());
        pending.clear();
        for (Pending<T> request:obsolete) deliver(request,null);
        return true;
    }
    Runnable get(String id,Consumer<T> listener,Loader<T> loader) {
        expirePending();
        String key=key(id);long now=clock.now();
        Entry<T> entry=cache.get(key);
        if (entry!=null && now<entry.expires) { listener.accept(entry.value);return () -> {}; }
        cache.remove(key);
        Pending<T> request=pending.get(key);
        if (request!=null && now>=request.expires) {
            pending.remove(key);deliver(request,null);request=null;
        }
        if (request!=null) { request.listeners.add(listener); Pending<T> shared=request; return () -> shared.listeners.remove(listener); }
        if (pending.size()>=8) { listener.accept(null); return () -> {}; }
        Pending<T> fresh=new Pending<>(now+REQUEST_MS);
        fresh.listeners.add(listener);pending.put(key,fresh);
        loader.load(key,(value,definitive) -> {
            if (pending.get(key)!=fresh) return; // Timed-out/revision-obsolete response.
            pending.remove(key);
            cache.put(key,new Entry<>(value,value!=null?Long.MAX_VALUE:clock.now()+(definitive?MISSING_MS:FAILURE_MS)));
            if (cache.size()>32) cache.remove(cache.keySet().iterator().next());
            deliver(fresh,value);
        });
        return () -> fresh.listeners.remove(listener);
    }
    private void expirePending() {
        var expired=new ArrayList<Pending<T>>();
        var iterator=pending.entrySet().iterator();
        while (iterator.hasNext()) {
            Pending<T> request=iterator.next().getValue();
            if (clock.now()>=request.expires) { iterator.remove();expired.add(request); }
        }
        for (Pending<T> request:expired) deliver(request,null);
    }
    private void deliver(Pending<T> request,T value) {
        // A failed view must not prevent other views receiving the same decoded image.
        for (Consumer<T> listener:request.listeners) {
            try { listener.accept(value); } catch (RuntimeException ex) { error.accept(ex); }
        }
    }
}
