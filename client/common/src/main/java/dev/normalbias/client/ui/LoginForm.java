package dev.normalbias.client.ui;

import com.codename1.ui.*;
import com.codename1.components.SpanLabel;
import com.codename1.ui.layouts.*;
import dev.normalbias.client.net.ServiceAddress;

/** One shared connection/sign-in page. Session and endpoint policy live in the lifecycle. */
public final class LoginForm extends Form {
    public interface Submit { void run(String address, String username, String password, boolean retry); }
    private final TextField address, username, password;
    private final Button login = new Button("Sign in"), retry = new Button("Retry connection"), redeem = new Button("Use invitation or reset link");
    private final SpanLabel message = new SpanLabel("Sign in to your music. Your service address is remembered on this device.", "ConnectionMessage");
    private final SpanLabel serviceHint = new SpanLabel("", "Muted");
    private final Label connectionBadge = new Label();
    private final java.util.function.Consumer<String> check;
    private final String browserOrigin, developmentOrigin;
    private boolean busy;
    private boolean checking, verified, advanceAfterCheck;
    private java.util.Timer debounce;

    public LoginForm(String origin, String browserOrigin, String developmentOrigin, Submit submit,
                     java.util.function.Consumer<String> check, java.util.function.Consumer<String> useLink) {
        super("Normal Bias", new BorderLayout());
        this.browserOrigin = browserOrigin; this.developmentOrigin = developmentOrigin;
        this.check = check;
        getToolbar().setHidden(true); getContentPane().setSafeArea(true);
        address = new TextField(origin, "https://music.example.com", 30, TextField.URL);
        address.setName("Service address"); address.setMaxSize(300); address.setUIID("ConnectionField");
        username = new TextField("", "Username", 24, TextField.NON_PREDICTIVE);
        username.setName("Username"); username.setMaxSize(64); username.setUIID("ConnectionField");
        password = new TextField("", "Password", 24, TextField.PASSWORD);
        password.setName("Password"); password.setMaxSize(256); password.setUIID("ConnectionField");
        address.setAccessibilityText("Service address"); address.getSemantics().setLabel("Service address");
        username.setAccessibilityText("Username"); username.getSemantics().setLabel("Username");
        password.setAccessibilityText("Password"); password.getSemantics().setLabel("Password");
        login.setName("Sign in"); login.setUIID("ConnectionPrimary");
        retry.setName("Retry connection"); retry.setUIID("ConnectionSecondary");
        redeem.setUIID("ConnectionSecondary");
        login.addActionListener(event -> submit(submit, false));
        retry.addActionListener(event -> checkNow());
        redeem.addActionListener(event -> {
            password.setText("");
            try { useLink.accept(ServiceAddress.selected(address.getText(), developmentOrigin)); }
            catch (IllegalArgumentException invalid) { status(invalid.getMessage(), false); }
        });
        address.addDataChangedListener((type, index) -> {
            addressEdited(); scheduleCheck();
        });
        // Some native editors commit text only when editing ends. Revoke the old tick on entry too.
        address.addFocusListener(new com.codename1.ui.events.FocusListener() {
            @Override public void focusGained(Component component) { addressEdited(); }
            @Override public void focusLost(Component component) { if (!verified && !checking) scheduleCheck(); }
        });
        address.addActionListener(event -> { if (!verified && !checking) scheduleCheck(); });
        Component[] order = {address, username, password, login, retry, redeem};
        for (int i = 0; i < order.length; i++) order[i].setPreferredTabIndex(i + 1);
        address.setDoneListener(event -> {
            advanceAfterCheck = true;
            address.stopEditing(() -> { if (verified) focusAfterCheck(); else checkNow(); });
        });
        username.setDoneListener(event -> username.stopEditing(() -> {
            if (password.isEnabled()) { password.requestFocus(); password.startEditingAsync(); }
        }));
        password.setDoneListener(event -> password.stopEditing(login::requestFocus));
        connectionBadge.setFocusable(false);
        badge(FontImage.MATERIAL_MORE_HORIZ, "Service not checked", "ConnectionBadge");
        Container endpoint = BorderLayout.center(address).add(BorderLayout.EAST, connectionBadge);
        Container content = BoxLayout.encloseY(new RecordMark(), new Label("Normal Bias", "Brand"),
                new Label("Your music, wherever you are", "NowTitle"),
                new Label("Service address", "ConnectionCaption"), endpoint, message, serviceHint,
                new Label("Username", "ConnectionCaption"), username,
                new Label("Password", "ConnectionCaption"), password, login, retry, redeem);
        content.setUIID("ConnectionPanel");
        Container bounded = new Container(new FlowLayout(Component.CENTER, Component.TOP));
        bounded.add(content); bounded.setScrollableY(true); bounded.setScrollVisible(false);
        add(BorderLayout.CENTER, bounded);
        Runnable fit = () -> {
            int available = getWidth() > 0 ? getWidth() : CN.getDisplayWidth();
            int width = Math.min(available, CN.convertToPixels(82));
            content.setWidth(width); content.setPreferredW(width); content.setShouldCalcPreferredSize(true);
            message.setPreferredW(Math.max(1, width - CN.convertToPixels(12)));
            serviceHint.setPreferredW(Math.max(1, width - CN.convertToPixels(12)));
        };
        fit.run();
        addSizeChangedListener(event -> { fit.run(); revalidate(); });
        retry.setHidden(true); retry.setVisible(false);
        updateActions();
    }

    private void submit(Submit submit, boolean retrying) {
        if (busy) return;
        String selected;
        try { selected = ServiceAddress.selected(address.getText(), developmentOrigin); }
        catch (IllegalArgumentException invalid) { status(invalid.getMessage(), false); return; }
        if (!remote() && !verified) { checkNow(); return; }
        if (!remote() && !retrying && (username.getText().trim().isEmpty() || password.getText().isEmpty())) {
            status("Enter your username and password, or retry the connection to restore your sign-in.", false); return;
        }
        String secret = password.getText(); password.setText("");
        submit.run(selected, username.getText().trim(), secret, retrying);
    }

    private boolean remote() {
        if (browserOrigin == null) return false;
        try { return !ServiceAddress.selected(address.getText(), developmentOrigin).equals(browserOrigin); }
        catch (IllegalArgumentException invalid) { return true; }
    }
    private void updateActions() {
        boolean remote = remote();
        login.setText(remote ? "Open service" : "Sign in"); login.setName(remote ? "Open service" : "Sign in");
        login.setEnabled(!busy && !checking && (remote || verified)); fieldEnabled(address, !busy);
        fieldEnabled(username, !busy && verified && !checking && !remote); fieldEnabled(password, !busy && verified && !checking && !remote);
        retry.setEnabled(!busy && !checking && !remote); redeem.setEnabled(!busy && verified && !checking && !remote);
        address.setNextFocusDown(verified && !remote ? username : retry.isVisible() ? retry : login);
        username.setNextFocusUp(address); username.setNextFocusDown(password);
        password.setNextFocusUp(username); password.setNextFocusDown(login);
        login.setNextFocusUp(password); login.setNextFocusDown(retry.isVisible() ? retry : redeem);
        retry.setNextFocusUp(login.isEnabled() ? login : address); retry.setNextFocusDown(redeem);
        serviceHint.setText(remote ? "Open this service’s Normal Bias page to sign in there." : browserOrigin != null ? "Service address comes from this page." : "The last connected service is saved on this device.");
        revalidate();
    }
    private static void fieldEnabled(TextField field, boolean enabled) {
        field.setEnabled(enabled);
        // The pinned port renders the hint as a separate Label, with its own disabled style.
        field.getHintLabel().setEnabled(enabled);
    }
    public void busy(String text) { busy = true; message.setText(text); updateActions(); }
    private void checkNow() {
        if (busy) return;
        if (debounce != null) { debounce.cancel(); debounce = null; }
        String origin;
        try { origin = ServiceAddress.selected(address.getText(), developmentOrigin); }
        catch (IllegalArgumentException invalid) { connectionFailed(invalid.getMessage()); return; }
        if (remote()) {
            checking = false; verified = false;
            badge(FontImage.MATERIAL_OPEN_IN_NEW, "Open service to check connection", "ConnectionBadge");
            message.setText("Open this service’s Normal Bias page to check its connection and sign in there.");
            updateActions(); return;
        }
        check.accept(origin);
    }
    private void addressEdited() {
        password.setText(""); verified = false; checking = false; advanceAfterCheck = false;
        check.accept(null);
        if (debounce != null) debounce.cancel();
        badge(FontImage.MATERIAL_MORE_HORIZ, "Service not checked", "ConnectionBadge");
        message.setText("Finish entering the service address to check its connection.");
        updateActions();
    }
    private void scheduleCheck() {
        if (debounce != null) debounce.cancel();
        debounce = CN.setTimeout(450, () -> { if (CN.getCurrentForm() == this) checkNow(); });
    }
    private void badge(char symbol, String label, String uiid) {
        connectionBadge.setUIID(uiid); connectionBadge.setName(label);
        connectionBadge.setAccessibilityText(label); connectionBadge.getSemantics().setLabel(label);
        FontImage.setMaterialIcon(connectionBadge, symbol, 4);
    }
    public void checking() {
        busy = false; checking = true; verified = false;
        badge(FontImage.MATERIAL_MORE_HORIZ, "Checking service", "ConnectionBadge");
        message.setText("Checking service…"); retry.setHidden(true); retry.setVisible(false); updateActions();
    }
    public void connected() {
        busy = false; checking = false; verified = true;
        badge(FontImage.MATERIAL_CHECK, "Service reachable", "ConnectionReady");
        retry.setHidden(true); retry.setVisible(false); updateActions();
    }
    public void connectionFailed(String text) {
        verified = false; checking = false;
        badge(FontImage.MATERIAL_CLOSE, "Service unavailable", "ConnectionFailed");
        status(text, true);
    }
    public void focusAfterCheck() {
        if (!username.isEnabled() || address.isEditing() && !advanceAfterCheck) return;
        username.requestFocus();
        if (advanceAfterCheck) { advanceAfterCheck = false; username.startEditingAsync(); }
    }
    public void focusUsername() {
        if (username.isEnabled()) username.requestFocus();
    }
    public void status(String text, boolean connectionFailed) {
        busy = false; message.setText(text);
        retry.setHidden(!connectionFailed); retry.setVisible(connectionFailed);
        updateActions();
    }
    @Override protected void deinitialize() {
        if (debounce != null) debounce.cancel();
        super.deinitialize();
    }

    /** Small original record emblem; the established player remains untouched. */
    private static final class RecordMark extends Component {
        RecordMark() { setUIID("ConnectionMark"); setFocusable(false); }
        @Override protected com.codename1.ui.geom.Dimension calcPreferredSize() {
            return new com.codename1.ui.geom.Dimension(CN.convertToPixels(16), CN.convertToPixels(20));
        }
        @Override public void paint(Graphics g) {
            g.setAntiAliased(true);
            int size = CN.convertToPixels(14), x = getX(), y = getY() + CN.convertToPixels(3);
            g.setColor(0x141414); g.fillArc(x, y, size, size, 0, 360);
            g.setColor(0x3d3d3d);
            for (int i = 1; i <= 3; i++) { int inset = size * i / 10; g.drawArc(x + inset, y + inset, size - inset * 2, size - inset * 2, 0, 360); }
            g.setColor(0xeac23f); g.fillArc(x + size * 3 / 8, y + size * 3 / 8, size / 4, size / 4, 0, 360);
            g.setColor(0x202124); g.fillArc(x + size * 15 / 32, y + size * 15 / 32, Math.max(2, size / 16), Math.max(2, size / 16), 0, 360);
        }
    }
}
