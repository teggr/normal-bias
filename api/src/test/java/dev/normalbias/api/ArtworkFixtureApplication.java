package dev.normalbias.api;

import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import java.util.*;
import java.io.*;

/** Test-classpath-only server for real browser/phone flows with synthetic provider responses. */
public class ArtworkFixtureApplication {
    public static void main(String[] args) { new SpringApplicationBuilder(NormalBiasApplication.class, Fixtures.class).run(args); }
    @TestConfiguration
    static class Fixtures {
        @Bean @Primary AlbumProvider fixtureProvider() {
            return new AlbumProvider() {
                public List<Candidate> search(String artist,String album) {
                    if (album.equals("No match")) return List.of();
                    if (album.equals("Offline")) throw new IllegalStateException("Synthetic provider outage");
                    return List.of(new Candidate("11111111-1111-1111-1111-111111111111",album,artist,"1994","GB","Original edition (synthetic)",3,"group","https://musicbrainz.org"),
                            new Candidate("22222222-2222-2222-2222-222222222222",album,artist,"2014","US","Deluxe edition (synthetic)",24,"group","https://musicbrainz.org"));
                }
                public byte[] cover(String id) throws IOException {
                    var image=new java.awt.image.BufferedImage(320,320,java.awt.image.BufferedImage.TYPE_INT_RGB);
                    var g=image.createGraphics(); g.setColor(new java.awt.Color(id.startsWith("1")?0x204ac8:0xc84025));g.fillRect(0,0,320,320);
                    g.setColor(java.awt.Color.WHITE); g.fillOval(55,55,210,210);g.setColor(java.awt.Color.BLACK);
                    g.setFont(new java.awt.Font("SansSerif",java.awt.Font.BOLD,26));g.drawString("SYNTHETIC",78,160);g.dispose();
                    var bytes=new ByteArrayOutputStream();javax.imageio.ImageIO.write(image,"png",bytes);return bytes.toByteArray();
                }
            };
        }
    }
}
