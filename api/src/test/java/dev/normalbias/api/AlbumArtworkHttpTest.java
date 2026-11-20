package dev.normalbias.api;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes={NormalBiasApplication.class,ArtworkFixtureApplication.Fixtures.class})
class AlbumArtworkHttpTest {
    static final Path DATA;
    static { try { DATA=Files.createTempDirectory("normalbias-art-http-"); } catch(IOException ex) { throw new ExceptionInInitializerError(ex); } }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) { r.add("normalbias.data-dir",()->DATA.toString()); }
    @LocalServerPort int port;
    final ObjectMapper json=new ObjectMapper();
    HttpClient client;
    @BeforeEach void before() { client=HttpClient.newHttpClient(); }
    @AfterEach void after() { client.close(); }
    HttpResponse<byte[]> request(String path,String body,String method) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path))
                .header("Content-Type","application/json").method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofByteArray());
    }
    JsonNode get(String path) throws Exception { var response=request(path,null,"GET");assertEquals(200,response.statusCode());return json.readTree(response.body()); }
    String upload(byte[] bytes) throws Exception {
        var response=client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/tracks"))
                .header("Content-Type","audio/mpeg").POST(HttpRequest.BodyPublishers.ofByteArray(bytes)).build(),HttpResponse.BodyHandlers.ofByteArray());
        assertTrue(response.statusCode()==201||response.statusCode()==200);return json.readTree(response.body()).path("id").asText();
    }
    @Test void endToEndApiPreservesEmbeddedAudioAndCatalogueAndInvalidatesCachedMissingArt() throws Exception {
        byte[] audio=getClass().getResourceAsStream("/tone.mp3").readAllBytes();String plain=upload(audio);
        var png=new ByteArrayOutputStream();javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(20,20,1),"png",png);
        Path source=DATA.resolve("source.mp3"),output=DATA.resolve("embedded.mp3");Files.write(source,audio);
        var mp3=new com.mpatric.mp3agic.Mp3File(source);var tag=new com.mpatric.mp3agic.ID3v24Tag();
        tag.setTitle("Covered track");tag.setArtist("Synthetic Artist");tag.setAlbum("Synthetic Album");tag.setAlbumImage(png.toByteArray(),"image/png");mp3.setId3v2Tag(tag);mp3.save(output.toString());
        byte[] embeddedAudio=Files.readAllBytes(output);String covered=upload(embeddedAudio);
        assertEquals(200,request("/api/tracks/"+plain,"{\"artist\":\"Synthetic Artist\",\"album\":\"Synthetic Album\"}","PATCH").statusCode());
        JsonNode before=get("/api/tracks/"+plain);
        byte[] embedded=request("/api/tracks/"+covered+"/artwork",null,"GET").body();
        var missing=request("/api/tracks/"+plain+"/artwork",null,"GET");assertEquals(404,missing.statusCode());
        assertEquals("no-store",missing.headers().firstValue("Cache-Control").orElseThrow());
        assertEquals(2,get("/api/album-artwork/suggestions/"+plain+"?limit=1").path("total").asInt());
        var started=request("/api/album-artwork/matches","{\"trackIds\":[\""+plain+"\",\""+covered+"\"]}","POST");assertEquals(202,started.statusCode());
        JsonNode job=json.readTree(started.body());String path="/api/album-artwork/matches/"+job.path("id").asText();
        for(int i=0;i<100 && get(path).path("state").asText().equals("LOOKING_UP");i++) Thread.sleep(10);
        assertEquals("READY",get(path).path("state").asText());assertEquals(2,get(path+"/candidates?limit=1").path("total").asInt());
        String candidate=get(path+"/candidates").path("items").get(0).path("id").asText();
        assertEquals(409,request(path+"/accept","{\"candidateId\":\""+candidate+"\",\"revision\":\""+job.path("revision").asText()+"\"}","POST").statusCode());
        byte[] preview=request(path+"/candidates/"+candidate+"/preview",null,"GET").body();assertNotNull(javax.imageio.ImageIO.read(new ByteArrayInputStream(preview)));
        String body="{\"candidateId\":\""+candidate+"\",\"revision\":\""+job.path("revision").asText()+"\"}";
        assertEquals(200,request(path+"/accept",body,"POST").statusCode());assertEquals(409,request(path+"/accept",body,"POST").statusCode());
        var artwork=request("/api/tracks/"+plain+"/artwork",null,"GET");assertEquals(200,artwork.statusCode());assertArrayEquals(preview,artwork.body());
        assertArrayEquals(embedded,request("/api/tracks/"+covered+"/artwork",null,"GET").body());
        String etag=artwork.headers().firstValue("ETag").orElseThrow();
        var cached=client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/tracks/"+plain+"/artwork"))
                .header("If-None-Match",etag).GET().build(),HttpResponse.BodyHandlers.ofByteArray());assertEquals(304,cached.statusCode());
        assertArrayEquals(audio,request("/api/tracks/"+plain+"/audio",null,"GET").body());
        assertArrayEquals(embeddedAudio,request("/api/tracks/"+covered+"/audio",null,"GET").body());
        var after=get("/api/tracks/"+plain);for(String field:List.of("id","title","artist","album","durationMs","createdAt"))assertEquals(before.get(field),after.get(field));
        assertNotEquals(before.get("artworkRevision"),after.get("artworkRevision"));
        var provenance=get("/api/album-artwork/tracks/"+plain);
        assertEquals(candidate,provenance.path("releaseId").asText());
        assertEquals(after.path("artworkRevision").asText(),provenance.path("revision").asText());
        assertEquals(2,json.readTree(provenance.path("evidence").path("catalogueSnapshot").asText()).size());
    }
}
