package dev.normalbias.client;

import com.codename1.ai.Tool;
import com.codename1.car.*;
import com.codename1.impl.javase.JavaSEPort;
import com.codename1.impl.javase.JavaSECarBridge;
import com.codename1.impl.javase.record.RecordingOptions;
import com.codename1.impl.javase.record.SimulatorRecorder;
import com.codename1.io.JSONParser;
import com.codename1.mcp.MCP;
import com.codename1.ui.CN;
import com.codename1.ui.Display;
import dev.normalbias.client.player.Player;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.StringReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

/** Desktop-only probe: compile separately, never package in the application.
 * Exercises the framework's real simulated head unit and template callbacks.
 * Reflection accesses pinned simulator internals absent from its public MCP tools.
 */
public final class CarPlayProbe extends NormalBias {
    private SimulatorRecorder recorder;
    private JavaSEPort port;
    private File output;

    private static Object field(Object owner, Class<?> type, String name) throws Exception {
        Field f = type.getDeclaredField(name);
        f.setAccessible(true);
        return f.get(owner);
    }
    @Override public void runApp() {
        super.runApp();
        try {
            port = (JavaSEPort) field(null, Display.class, "impl");
            output = new File(System.getProperty("normalbias.carProbeOutput"));
            output.mkdirs();
            MCP.addTool(new Tool("car_probe", "Desktop-only CarPlay smoke test and actual head-unit recording",
                    "{\"type\":\"object\",\"properties\":{\"op\":{\"type\":\"string\"},\"title\":{\"type\":\"string\"}},\"required\":[\"op\"]}", this::probe));
        } catch (Exception e) { throw new RuntimeException(e); }
    }
    private void simulatorMethod(String name, Class<?>[] types, Object... args) throws Exception {
        Method method = JavaSEPort.class.getDeclaredMethod(name, types);
        method.setAccessible(true);
        SwingUtilities.invokeAndWait(() -> {
            try { method.invoke(port, args); } catch (Exception e) { throw new RuntimeException(e); }
        });
    }
    private BufferedImage frame() {
        BufferedImage[] result = new BufferedImage[1];
        try {
            SwingUtilities.invokeAndWait(() -> {
                try {
                    JFrame window = (JFrame) field(port.getCarBridge(), JavaSECarBridge.class, "frame");
                    result[0] = new BufferedImage(window.getWidth(), window.getHeight(), BufferedImage.TYPE_INT_RGB);
                    var graphics = result[0].createGraphics();
                    window.printAll(graphics);
                    graphics.dispose();
                } catch (Exception e) { throw new RuntimeException(e); }
            });
            return result[0];
        } catch (Exception e) { throw new RuntimeException(e); }
    }
    private String probe(String json) throws Exception {
        Map<String, Object> args = new JSONParser().parseJSON(new StringReader(json));
        String op = (String) args.get("op");
        if (op.equals("connect")) {
            simulatorMethod("connectSimulatedCar", new Class<?>[]{JavaSECarBridge.Style.class}, JavaSECarBridge.Style.CARPLAY);
        } else if (op.equals("disconnect")) {
            simulatorMethod("disconnectSimulatedCar", new Class<?>[0]);
        } else if (op.equals("record")) {
            recorder = new SimulatorRecorder(ignored -> frame());
            RecordingOptions options = new RecordingOptions();
            options.setPath(new File(output, "carplay.mp4").getAbsolutePath());
            options.setFps(10);
            options.setAudio(RecordingOptions.Audio.APP);
            options.setMaxSeconds(120);
            recorder.start(options);
        } else if (op.equals("stop")) {
            SimulatorRecorder.Result result = recorder.stop();
            return result.file + " durationMs=" + result.durationMs + " audio=" + result.audio;
        } else if (op.equals("screenshot")) {
            String name = (String) args.get("title");
            if (name == null) name = "head-unit";
            if (!name.matches("[a-z-]+")) throw new IllegalArgumentException("Invalid screenshot name");
            ImageIO.write(frame(), "png", new File(output, name + ".png"));
        }
        String[] result = new String[1];
        Exception[] error = new Exception[1];
        CN.callSeriallyAndWait(() -> {
            try {
                CarContext context = Car.getCurrentContext();
                if (context == null) { result[0] = "Disconnected"; return; }
                if (op.equals("back")) context.popScreen();
                if (op.equals("tap")) {
                    CarListTemplate list = (CarListTemplate) context.getTopScreen().dispatchCreateTemplate();
                    CarRow selected = null;
                    for (CarSection section : list.getSections()) for (CarRow row : section.getRows()) {
                        if (row.getTitle().equals(args.get("title"))) selected = row;
                    }
                    CarActionListener action = selected == null ? null : selected.getOnAction();
                    if (action == null && list.getHeaderActions() != null) {
                        for (CarAction header : list.getHeaderActions().getActions()) {
                            if (header.getTitle().equals(args.get("title"))) action = header.getOnAction();
                        }
                    }
                    if (action == null) throw new IllegalStateException("No action: " + args.get("title"));
                    action.actionPerformed(context);
                }
                CarTemplate template = context.getTopScreen().dispatchCreateTemplate();
                StringBuilder text = new StringBuilder(template.getClass().getSimpleName());
                if (template instanceof CarListTemplate) {
                    CarListTemplate list = (CarListTemplate) template;
                    text.append(" title=").append(list.getTitle());
                    if (list.getHeaderActions() != null) for (CarAction action : list.getHeaderActions().getActions()) text.append(" | ").append(action.getTitle());
                    for (CarSection section : list.getSections()) {
                        for (CarRow row : section.getRows()) text.append(" | ").append(row.getTitle()).append(row.getImage() == null ? "" : " [artwork]");
                    }
                }
                Player player = (Player) field(Car.getApplication(), Car.getApplication().getClass(), "player");
                text.append("; queue=").append(player.queue.size()).append(" index=").append(player.queue.index())
                        .append(" playing=").append(player.isPlaying()).append(" time=").append(player.time())
                        .append(" status=").append(player.status());
                result[0] = text.toString();
            } catch (Exception e) { error[0] = e; }
        });
        if (error[0] != null) throw error[0];
        return result[0];
    }
}
