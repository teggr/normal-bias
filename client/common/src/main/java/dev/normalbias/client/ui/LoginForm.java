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
    private final String browserOrigin, developmentOrigin;
    private boolean busy;

    public LoginForm(String origin, String browserOrigin, String developmentOrigin, Submit submit, java.util.function.Consumer<String> useLink) {
        super("Normal Bias", new BorderLayout());
        this.browserOrigin = browserOrigin; this.developmentOrigin = developmentOrigin;
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
        retry.addActionListener(event -> submit(submit, true));
        redeem.addActionListener(event -> {
            password.setText("");
            try { useLink.accept(ServiceAddress.selected(address.getText(), developmentOrigin)); }
            catch (IllegalArgumentException invalid) { status(invalid.getMessage(), false); }
        });
        address.addDataChangedListener((type, index) -> { password.setText(""); updateActions(); });
        Container content = BoxLayout.encloseY(new RecordMark(), new Label("Normal Bias", "Brand"),
                new Label("Your music, wherever you are", "NowTitle"), message,
                new Label("Service address", "ConnectionCaption"), address, serviceHint,
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
        login.setEnabled(!busy); address.setEnabled(!busy);
        username.setEnabled(!busy && !remote); password.setEnabled(!busy && !remote);
        retry.setEnabled(!busy && !remote); redeem.setEnabled(!busy && !remote);
        serviceHint.setText(remote ? "Open this service’s Normal Bias page to sign in there." : browserOrigin != null ? "Service address comes from this page." : "The last connected service is saved on this device.");
        revalidate();
    }
    public void busy(String text) { busy = true; message.setText(text); updateActions(); }
    public void focusUsername() {
        username.requestFocus();
    }
    public void status(String text, boolean connectionFailed) {
        busy = false; message.setText(text);
        retry.setHidden(!connectionFailed); retry.setVisible(connectionFailed);
        updateActions();
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
