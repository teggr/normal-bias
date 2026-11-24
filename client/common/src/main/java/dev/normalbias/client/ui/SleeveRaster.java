package dev.normalbias.client.ui;

/** The established sleeve design as immutable pixels; no mutable native mask context. */
final class SleeveRaster {
    private static final int[] COLORS = {0x79919c, 0xeebd40, 0xdf754c, 0xa3a878};
    private SleeveRaster() { }
    static int[] create(int size, boolean round, int variation, int[] cover, int coverWidth, int coverHeight) {
        int[] pixels = new int[size * size];
        for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) {
            double dx = x + .5 - size * .5, dy = y + .5 - size * .425;
            pixels[y * size + x] = 0xff000000 | (dx * dx + dy * dy <= size * size * .09 ? COLORS[variation] : 0xeee8db);
        }
        int stroke = Math.max(1, size / 120);
        for (int row = 0; row < 10; row++) for (int x = size / 8; x < size * 7 / 8; x++) {
            int y = (int) (size * (.45 + row * .032 + .018 * Math.sin(x * 9.0 / size + row * .2)));
            for (int line = 0; line < stroke; line++) if (y + line >= 0 && y + line < size) pixels[(y + line) * size + x] = 0xff202323;
        }
        for (int y = size * 84 / 100; y < size * 84 / 100 + Math.max(1, size / 150); y++) {
            for (int x = size / 8; x < size / 8 + size / 5; x++) pixels[y * size + x] = 0xff202323;
            for (int x = size * 69 / 100; x < size * 69 / 100 + size / 6; x++) pixels[y * size + x] = 0xff202323;
        }
        double sourceSize = Math.min(coverWidth, coverHeight);
        for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) {
            int index = y * size + x, color = pixels[index];
            if (cover != null && sourceSize > 0) {
                double sx = (coverWidth - sourceSize) / 2 + (x + .5) * sourceSize / size - .5;
                double sy = (coverHeight - sourceSize) / 2 + (y + .5) * sourceSize / size - .5;
                color = composite(sample(cover, coverWidth, coverHeight, sx, sy), color);
            }
            double radius = round ? size / 2.0 : Math.max(1, size / 40.0);
            double dx = round ? x + .5 - radius : Math.max(0, Math.max(radius - x - .5, x + .5 - (size - radius)));
            double dy = round ? y + .5 - radius : Math.max(0, Math.max(radius - y - .5, y + .5 - (size - radius)));
            double coverage = !round && (dx == 0 || dy == 0) ? 1 : Math.max(0, Math.min(1, radius + .5 - Math.sqrt(dx * dx + dy * dy)));
            pixels[index] = ((int) Math.round(255 * coverage) << 24) | (color & 0xffffff);
        }
        return pixels;
    }
    private static int sample(int[] image, int width, int height, double x, double y) {
        x = Math.max(0, Math.min(width - 1, x)); y = Math.max(0, Math.min(height - 1, y));
        int x0 = (int) x, y0 = (int) y, x1 = Math.min(width - 1, x0 + 1), y1 = Math.min(height - 1, y0 + 1);
        double fx = x - x0, fy = y - y0;
        int result = 0;
        for (int shift = 0; shift <= 24; shift += 8) {
            double top = ((image[y0 * width + x0] >>> shift) & 255) * (1 - fx) + ((image[y0 * width + x1] >>> shift) & 255) * fx;
            double bottom = ((image[y1 * width + x0] >>> shift) & 255) * (1 - fx) + ((image[y1 * width + x1] >>> shift) & 255) * fx;
            result |= (int) Math.round(top * (1 - fy) + bottom * fy) << shift;
        }
        return result;
    }
    private static int composite(int foreground, int background) {
        int alpha = foreground >>> 24, result = 0xff000000;
        for (int shift = 0; shift <= 16; shift += 8)
            result |= ((((foreground >>> shift) & 255) * alpha + ((background >>> shift) & 255) * (255 - alpha) + 127) / 255) << shift;
        return result;
    }
}
