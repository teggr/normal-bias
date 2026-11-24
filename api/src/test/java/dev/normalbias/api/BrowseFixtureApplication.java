package dev.normalbias.api;

import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;

/** Test-classpath-only synthetic scale fixture. No administrative HTTP endpoint. */
public class BrowseFixtureApplication {
    public static void main(String[] args) { new SpringApplicationBuilder(NormalBiasApplication.class, Fixtures.class).run(args); }
    @TestConfiguration
    static class Fixtures {
        @Bean ApplicationRunner syntheticCatalogue(JdbcTemplate db) {
            return args -> {
                if (db.queryForObject("SELECT COUNT(*) FROM tracks", Integer.class) != 0) throw new IllegalStateException("Browse fixture requires a new empty catalogue");
                var rows = new ArrayList<Object[]>();
                for (int i = 0; i < 14000; i++) {
                    String initial = i < 13000 ? "" + (char) ('A' + i / 500) : "7";
                    rows.add(new Object[]{String.format("%064x", i), "synthetic-" + i + ".mp3", initial + " track " + String.format("%05d", i),
                            initial + " Artist " + (i % 80), initial + " Album " + (i % 400)});
                }
                db.batchUpdate("INSERT INTO tracks(id, filename, title, artist, album, duration_ms, size_bytes, created_at) VALUES (?, ?, ?, ?, ?, 180000, 1, CURRENT_TIMESTAMP)", rows);
            };
        }
    }
}
