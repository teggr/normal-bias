package dev.normalbias.api;

import com.mpatric.mp3agic.ID3v2TagFactory;
import org.springframework.stereotype.Service;
import javax.imageio.ImageIO;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.LinkedHashMap;
import java.util.Map;

/** Derived, bounded artwork cache. Original MP3s remain the only durable source. */
@Service
public class EmbeddedArtwork {
    private final TrackStore store;
    private final Map<String, byte[]> cache = new LinkedHashMap<>(16, 0.75f, true);
    public EmbeddedArtwork(TrackStore store) { this.store = store; }

    // Serialize extraction to bound concurrent image/MP3 decoding memory. Never hold the store monitor.
    public synchronized byte[] get(String id) throws IOException {
        Track track = store.get(id); // Check existence even for cached covers after deletion.
        if (cache.containsKey(id)) return cache.get(id);
        byte[] result = null;
        try {
            byte[] embedded = readImage(store.path(track));
            if (embedded != null && embedded.length > 0 && embedded.length <= 8 * 1024 * 1024) {
                result = normalize(embedded);
            }
        } catch (com.mpatric.mp3agic.InvalidDataException | com.mpatric.mp3agic.UnsupportedTagException
                 | com.mpatric.mp3agic.NoSuchTagException | IllegalArgumentException ignored) {
            // Invalid/unsupported embedded data is optional, not an audio failure.
        }
        cache.put(id, result);
        if (cache.size() > 16) cache.remove(cache.keySet().iterator().next());
        return result;
    }

    private static byte[] readImage(java.nio.file.Path path) throws IOException,
            com.mpatric.mp3agic.InvalidDataException, com.mpatric.mp3agic.UnsupportedTagException,
            com.mpatric.mp3agic.NoSuchTagException {
        // Read just the bounded ID3v2 block, never scan/decode the MP3 audio on cover requests.
        try (var file = new RandomAccessFile(path.toFile(), "r")) {
            if (file.length() < 10) return null;
            byte[] header = new byte[10];
            file.readFully(header);
            if (header[0] != 'I' || header[1] != 'D' || header[2] != '3') return null;
            int size = 0;
            for (int i = 6; i < 10; i++) {
                if ((header[i] & 0x80) != 0) return null;
                size = (size << 7) | (header[i] & 0x7f);
            }
            if (size > 16 * 1024 * 1024 || size + 10L > file.length()) return null;
            byte[] tag = new byte[size + 10];
            System.arraycopy(header, 0, tag, 0, 10);
            file.readFully(tag, 10, size);
            return ID3v2TagFactory.createTag(tag).getAlbumImage();
        }
    }

    static byte[] normalize(byte[] bytes) throws IOException {
        try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) return null;
            var reader = readers.next();
            try {
                String format = reader.getFormatName();
                if (!format.equalsIgnoreCase("JPEG") && !format.equalsIgnoreCase("PNG")) return null;
                reader.setInput(input, true, true);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width < 1 || height < 1 || width > 4096 || height > 4096
                        || (long) width * height > 16_000_000) return null;
                BufferedImage source = reader.read(0);
                double scale = Math.min(1.0, 512.0 / Math.max(width, height));
                BufferedImage image = new BufferedImage(Math.max(1, (int) (width * scale)),
                        Math.max(1, (int) (height * scale)), BufferedImage.TYPE_INT_ARGB);
                var graphics = image.createGraphics();
                try {
                    graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    graphics.drawImage(source, 0, 0, image.getWidth(), image.getHeight(), null);
                } finally { graphics.dispose(); }
                var output = new ByteArrayOutputStream();
                ImageIO.write(image, "png", output);
                return output.toByteArray();
            } catch (IOException | RuntimeException invalidImage) {
                return null;
            } finally { reader.dispose(); }
        }
    }
}
