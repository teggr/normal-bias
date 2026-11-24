package dev.normalbias.client.ui;

import com.codename1.ui.CN;
import com.codename1.ui.Graphics;
import com.codename1.ui.Image;
import com.codename1.ui.Stroke;
import com.codename1.ui.geom.GeneralPath;

/** Shared navigation mark: a rounded play outline with a restrained amber centre. */
final class NowPlayingIcon {
    private NowPlayingIcon() {}

    static Image create() {
        int size = CN.convertToPixels(4.2f);
        Image image = Image.createImage(size, size, 0);
        Graphics g = image.getGraphics();
        g.setAntiAliased(true);
        GeneralPath triangle = new GeneralPath();
        triangle.moveTo(size * .32f, size * .19f);
        triangle.quadTo(size * .23f, size * .14f, size * .23f, size * .26f);
        triangle.lineTo(size * .23f, size * .74f);
        triangle.quadTo(size * .23f, size * .86f, size * .33f, size * .80f);
        triangle.lineTo(size * .78f, size * .55f);
        triangle.quadTo(size * .87f, size * .50f, size * .78f, size * .45f);
        triangle.closePath();
        g.setColor(0xf3f3f1);
        g.drawShape(triangle, new Stroke(Math.max(1.5f, size * .07f), Stroke.CAP_ROUND, Stroke.JOIN_ROUND, 1));
        int dot = Math.max(2, size * 14 / 100);
        g.setColor(0xeac23f);
        g.fillArc(size * 46 / 100 - dot / 2, size / 2 - dot / 2, dot, dot, 0, 360);
        return image;
    }
}
