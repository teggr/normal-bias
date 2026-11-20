package dev.normalbias.api;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class MusicBrainzProviderTest {
    HttpServer server;
    MusicBrainzProvider provider;
    AtomicInteger calls = new AtomicInteger();
    int status = 200;
    String body = "{\"releases\":[{\"id\":\"11111111-1111-1111-1111-111111111111\",\"title\":\"Album\",\"date\":\"1994\",\"country\":\"GB\",\"artist-credit\":[{\"name\":\"Artist\"}],\"media\":[{\"track-count\":12}]}]}";
    Map<String,String> headers = new HashMap<>();
    String receivedQuery, agent;
    @BeforeEach void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/", exchange -> {
            calls.incrementAndGet(); receivedQuery=java.net.URLDecoder.decode(Objects.toString(exchange.getRequestURI().getRawQuery(), ""), java.nio.charset.StandardCharsets.UTF_8); agent=exchange.getRequestHeaders().getFirst("User-Agent");
            headers.forEach((key,value)->exchange.getResponseHeaders().set(key,value));
            byte[] bytes=body.getBytes(); exchange.sendResponseHeaders(status,bytes.length);
            try(var output=exchange.getResponseBody()) { output.write(bytes); }
        }); server.start();
        URI origin=URI.create("http://127.0.0.1:"+server.getAddress().getPort());
        provider=new MusicBrainzProvider(origin,origin,0);
    }
    @AfterEach void close() { server.stop(0); }
    @Test void parsesReleaseEditionsAndSendsOnlyEscapedIdentificationMetadata() throws Exception {
        var found=provider.search("Artist","Album"); assertEquals(12,found.getFirst().trackCount());
        assertEquals("1994",found.getFirst().date()); assertTrue(agent.contains("https://github.com/teggr/normal-bias"));
        assertTrue(receivedQuery.contains("release:\"Album\" AND artist:\"Artist\"")); assertTrue(receivedQuery.contains("limit=10"));
    }
    @Test void throttlingHonoursCooldownWithoutAutomaticRetries() throws Exception {
        status=429; headers.put("Retry-After","120"); assertThrows(IOException.class,()->provider.search("Artist","Album"));
        assertThrows(IOException.class,()->provider.search("Artist","Album")); assertEquals(1,calls.get());
    }
    @Test void unavailableAndMalformedAndExcessiveResponsesFailBoundedly() throws Exception {
        status=503; assertThrows(IOException.class,()->provider.search("Artist","Album")); assertEquals(1,calls.get());
    }
    @Test void malformedJsonIsNotAnEmptyMatch() {
        body="broken"; assertThrows(IOException.class,()->provider.search("Artist","Album"));
    }
    @Test void excessiveJsonIsRejected() {
        body=" ".repeat(1024*1024+1); assertThrows(IOException.class,()->provider.search("Artist","Album"));
    }
    @Test void missingMalformedAndUntrustedCoverRedirectsCannotBeAccepted() throws Exception {
        String id="11111111-1111-1111-1111-111111111111";
        status=404; assertNull(provider.cover(id));
        status=200; body="malformed image"; assertNull(provider.cover(id));
        status=307; headers.put("Location","https://evil.test/image.jpg"); assertThrows(IOException.class,()->provider.cover(id));
        headers.put("Location","/loop"); assertThrows(IOException.class,()->provider.cover(id));
    }
}
