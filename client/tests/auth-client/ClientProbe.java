package dev.normalbias.client;

import com.codename1.ai.Tool;
import com.codename1.mcp.MCP;
import com.codename1.ui.*;
import dev.normalbias.client.ui.LibraryForm;
import dev.normalbias.client.player.Player;
import java.util.*;
import java.util.List;

/** Test-only controls injected into an ignored simulator build; never shipped. */
public final class ClientProbe {
    private static boolean started;
    public static void start() {
        if (started) return; started = true;
        MCP.addTool(new Tool("auth_client_probe", "Inspect and operate the actual synthetic-client UI, without returning credentials",
                "{\"type\":\"object\",\"properties\":{\"op\":{\"type\":\"string\"},\"name\":{\"type\":\"string\"},\"value\":{\"type\":\"string\"}},\"required\":[\"op\"]}", ClientProbe::control));
        MCP.startSocketServer(18868);
    }
    private static String control(String json) throws Exception {
        Map<String, Object> input = new com.codename1.io.JSONParser().parseJSON(new java.io.StringReader(json));
        String[] result = new String[1];
        CN.callSeriallyAndWait(() -> result[0] = com.codename1.io.JSONParser.mapToJson(controlOnEdt(input)));
        return result[0];
    }
    private static Map<String, Object> controlOnEdt(Map<String, Object> input) {
        Form form = CN.getCurrentForm();
        String op = (String) input.get("op");
        if (op.equals("text")) {
            Component field = find(form, (String) input.get("name"));
            if (!(field instanceof TextField)) throw new IllegalArgumentException("No such input");
            ((TextField) field).setText((String) input.get("value"));
        } else if (op.equals("tap")) {
            Component component = find(form, (String) input.get("name"));
            if (component == null) throw new IllegalArgumentException("No such control");
            int x = component.getAbsoluteX() + component.getWidth()/2, y = component.getAbsoluteY()+component.getHeight()/2;
            form.pointerPressed(x, y); form.pointerReleased(x, y);
        } else if (form instanceof LibraryForm) {
            Player player = ((LibraryForm) form).probePlayer();
            if (op.equals("play-first")) findButton(form, "Morning Circuit");
            if (op.equals("pause")) player.pause();
            if (op.equals("resume")) player.resume();
            if (op.equals("seek")) player.seekTime(Integer.parseInt((String) input.get("value")));
            if (op.equals("next")) player.next();
        }
        Map<String, Object> result = new HashMap<>();
        result.put("platform", CN.getPlatformName()); result.put("simulator", CN.isSimulator());
        result.put("form", form.getTitle()); result.put("library", form instanceof LibraryForm);
        List<String> labels = new ArrayList<>(); labels(form, labels); result.put("labels", labels);
        if (form instanceof LibraryForm) {
            Player player = ((LibraryForm) form).probePlayer();
            result.put("time", player.time()); result.put("playing", player.isPlaying()); result.put("loading", player.isLoading());
            result.put("status", player.status()); result.put("queue", player.queue.size());
        }
        return result;
    }
    private static void findButton(Form form, String name) {
        Component component = find(form, name);
        if (component != null) { int x=component.getAbsoluteX()+component.getWidth()/2,y=component.getAbsoluteY()+component.getHeight()/2;form.pointerPressed(x,y);form.pointerReleased(x,y); }
    }
    private static Component find(Component component, String name) {
        if (name.equals(component.getName()) || component instanceof Button && name.equals(((Button)component).getText())) return component;
        if (component instanceof Container) for (Component child : (Container)component) { Component found=find(child,name);if(found!=null)return found; }
        return null;
    }
    private static void labels(Component component, List<String> result) {
        if (component instanceof com.codename1.components.SpanLabel) { result.add(((com.codename1.components.SpanLabel)component).getText()); return; }
        if (component instanceof Label) result.add(((Label)component).getText());
        if (component instanceof Container) for (Component child : (Container)component) labels(child,result);
    }
}
