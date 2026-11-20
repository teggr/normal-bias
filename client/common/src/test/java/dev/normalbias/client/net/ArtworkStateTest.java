package dev.normalbias.client.net;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ArtworkStateTest {
    long now;
    final List<RuntimeException> errors=new ArrayList<>();
    final ArtworkState<String> state=new ArtworkState<>(() -> now,errors::add);
    final List<ArtworkState.Completion<String>> requests=new ArrayList<>();
    final ArtworkState.Loader<String> loader=(key,done) -> requests.add(done);

    @Test void sharesOneImageAcrossViewsAndIsolatesABrokenListener() {
        var views=new ArrayList<String>();
        state.get("track",image -> { throw new IllegalStateException("broken view"); },loader);
        state.get("track",views::add,loader);state.get("track",views::add,loader);
        assertEquals(1,requests.size());requests.get(0).complete("cover",true);
        assertEquals(List.of("cover","cover"),views);assertEquals(1,errors.size());
        state.get("track",views::add,loader);assertEquals(1,requests.size());
        assertEquals(List.of("cover","cover","cover"),views);
    }
    @Test void missingCoverExpiresEvenIfRevisionNotificationsNeverArrive() {
        var view=new ArrayList<String>();
        state.get("track",view::add,loader);requests.get(0).complete(null,true);
        now=29999;state.get("track",view::add,loader);assertEquals(1,requests.size());
        now=30000;state.get("track",view::add,loader);assertEquals(2,requests.size());
        requests.get(1).complete("arrived later",true);
        assertEquals("arrived later",view.get(view.size()-1));assertEquals("track?v=0",state.key("track"));
    }
    @Test void transientFailureRetriesSoonAndDoesNotBecomePermanentMissingArt() {
        state.get("track",ignored -> {},loader);requests.get(0).complete(null,false);
        now=4999;state.get("track",ignored -> {},loader);assertEquals(1,requests.size());
        now=5000;state.get("track",ignored -> {},loader);assertEquals(2,requests.size());
    }
    @Test void hungImageRequestExpiresAndLateResponseCannotReplaceTheRetry() {
        var view=new ArrayList<String>();
        state.get("track",view::add,loader);
        now=25000;state.get("track",view::add,loader);assertEquals(2,requests.size());
        requests.get(1).complete("new",true);requests.get(0).complete("old",true);
        state.get("track",view::add,loader);
        assertEquals(Arrays.asList(null,"new","new"),view);
    }
    @Test void revisionInvalidatesMissingPositiveAndInflightResultsTogether() {
        var obsolete=new ArrayList<String>();
        state.get("track",obsolete::add,loader);
        long token=state.beginRevision(false);assertTrue(state.revisionLoaded(token,"1"));
        assertEquals(Collections.singletonList(null),obsolete);
        var fresh=new ArrayList<String>();state.get("track",fresh::add,loader);
        requests.get(0).complete("obsolete",true);assertTrue(fresh.isEmpty());
        requests.get(1).complete("current",true);assertEquals(List.of("current"),fresh);
        assertEquals("track?v=1",state.key("track"));
    }
    @Test void hungRevisionChecksRecoverAndForegroundCheckSupersedesOlderReplies() {
        long old=state.beginRevision(false);assertTrue(old>0);
        now=15000;assertEquals(0,state.beginRevision(false));
        now=25000;long retry=state.beginRevision(false);assertTrue(retry>old);
        assertFalse(state.revisionLoaded(old,"stale"));assertTrue(state.revisionLoaded(retry,"1"));
        long resumed=state.beginRevision(true);assertTrue(resumed>retry);
        state.revisionFailed(retry); // An old failure cannot release the newer active request.
        assertEquals(0,state.beginRevision(false));
        assertTrue(state.revisionLoaded(resumed,"2"));assertEquals("track?v=2",state.key("track"));
    }
    @Test void failedRevisionCheckCanRetryAndMalformedReplyPreservesKnownRevision() {
        long token=state.beginRevision(false);state.revisionFailed(token);
        now=10000;long retry=state.beginRevision(false);assertTrue(retry>token);
        assertFalse(state.revisionLoaded(retry,null));assertEquals("track?v=0",state.key("track"));
        now=20000;assertTrue(state.beginRevision(false)>retry);
    }
    @Test void revisionHeartbeatExpiresAnAbandonedPageRequest() {
        var abandoned=new ArrayList<String>();state.get("offscreen",abandoned::add,loader);
        now=25000;state.beginRevision(false);
        assertEquals(Collections.singletonList(null),abandoned);
        requests.get(0).complete("late",true);
        assertEquals(Collections.singletonList(null),abandoned);
    }
}
