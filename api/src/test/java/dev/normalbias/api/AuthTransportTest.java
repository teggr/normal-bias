package dev.normalbias.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import java.net.*;
import java.net.http.*;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class AuthTransportTest {
    @TempDir Path data;
    @Test void requiredModeRejectsPlainHttpAndDoesNotTrustForwardedTlsClaims() throws Exception {
        check("", data.resolve("root")); check("/sub", data.resolve("context"));
    }
    private void check(String prefix, Path directory) throws Exception {
        try (var context = new SpringApplicationBuilder(NormalBiasApplication.class).run("--server.port=0",
                "--normalbias.data-dir=" + directory, "--server.servlet.context-path=" + prefix,
                "--server.ssl.enabled=false", "--normalbias.auth.require-https=true", "--normalbias.artwork.background-enabled=false"); var client = HttpClient.newHttpClient()) {
            int port = ((ServletWebServerApplicationContext) context).getWebServer().getPort();
            var response = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + prefix + "/api"))
                    .header("X-Forwarded-Proto", "https").header("Forwarded", "proto=https").GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(426, response.statusCode()); assertTrue(response.body().contains("HTTPS required"));
        }
    }
}
