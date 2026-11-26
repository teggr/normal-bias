package dev.normalbias.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"normalbias.auth.mode=private-demo", "server.address=192.168.1.38", "normalbias.artwork.background-enabled=false"})
@AutoConfigureMockMvc
class PrivateDemoTest {
    static final Path DATA;
    static {
        try { DATA = Files.createTempDirectory("normalbias-private-demo-"); }
        catch (Exception failure) { throw new ExceptionInInitializerError(failure); }
    }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry properties) {
        properties.add("normalbias.data-dir", DATA::toString);
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired TrackStore tracks;

    @Test void httpListeningUsesDirectAudioWithoutAdvertisingAccountOrManagementActions() throws Exception {
        var discovery = json.readTree(mvc.perform(get("/api")).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        assertFalse(discovery.path("authRequired").asBoolean());
        for (String relation : List.of("device-login", "browser-login", "upload", "start-inspection", "logout"))
            assertFalse(discovery.path("_links").has(relation), relation);
        String songs = discovery.path("_links").path("songs").path("href").asText();
        assertEquals("/api/tracks", songs);
        byte[] tone;
        try (var input = getClass().getResourceAsStream("/tone.mp3")) { tone = input.readAllBytes(); }
        Track track = tracks.upload(new java.io.ByteArrayInputStream(tone), "synthetic.mp3", tone.length).track();
        String path = "/api/tracks/" + track.id();
        var row = json.readTree(mvc.perform(get(path)).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        assertTrue(row.path("_links").has("album"));
        for (String relation : List.of("edit", "delete", "playback-grant")) assertFalse(row.path("_links").has(relation));
        mvc.perform(get(songs)).andExpect(status().isOk());
        mvc.perform(get("/api/library/albums")).andExpect(status().isOk());
        mvc.perform(get("/api/library/index?view=songs")).andExpect(status().isOk());
        mvc.perform(get(path + "/audio").header("Range", "bytes=0-31"))
                .andExpect(status().isPartialContent()).andExpect(content().bytes(java.util.Arrays.copyOf(tone, 32)));
        mvc.perform(head(path + "/audio")).andExpect(status().isOk());
        mvc.perform(get(path + "/artwork")).andExpect(status().isNotFound());
        mvc.perform(get("/api/album-artwork/revision")).andExpect(status().isOk());
        for (String target : List.of("/api/auth/device-sessions", "/api/auth/browser-sessions", "/api/auth/invitations/accept",
                "/api/auth/password", path + "/playback-grants", "/api/tracks", "/api/inspections"))
            mvc.perform(post(target).contentType("application/json").content("{}" )).andExpect(status().isUnauthorized());
        mvc.perform(patch(path).contentType("application/json").content("{}" )).andExpect(status().isUnauthorized());
        mvc.perform(delete(path)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/session")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/inspections/unknown")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/album-artwork/suggestions/" + track.id())).andExpect(status().isUnauthorized());
    }
}
