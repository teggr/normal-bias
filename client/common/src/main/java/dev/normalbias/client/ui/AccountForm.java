package dev.normalbias.client.ui;

import com.codename1.ui.*;
import com.codename1.components.SpanLabel;
import com.codename1.ui.layouts.*;
import dev.normalbias.client.net.*;
import java.util.Map;

/** Shared invitation, reset and signed-in password forms; all authority stays on the server. */
public final class AccountForm extends Form {
    private final SpanLabel message = new SpanLabel("", "Muted");
    private boolean left;
    private Map<String, Object> inspected;
    private Button back;

    public AccountForm(AuthSession session, AccountLink link, Runnable done, Runnable cancel) {
        super(link.invitation ? "Create account" : "Reset password", new BorderLayout());
        TextField username = field("Username", false);
        TextField password = field("New password", true), confirm = field("Confirm password", true);
        Button inspect = new Button("Inspect link"), submit = new Button(link.invitation ? "Create account" : "Reset password");
        submit.setEnabled(false);
        Runnable inspection = () -> {
            inspect.setEnabled(false); submit.setEnabled(false); inspected = null;
            message.setText("Checking link…"); revalidate();
            session.inspect(link, data -> {
                if (left) return;
                inspected = data; inspect.setEnabled(true);
                submit.setEnabled(ApiLinks.href(data, link.invitation ? "accept" : "complete", "POST") != null);
                message.setText("Choose a password of 12–256 characters. Link expires: " + data.get("expiresAt")); revalidate();
            }, error -> { if (!left) { inspect.setEnabled(true); message.setText(error); revalidate(); } });
        };
        inspect.addActionListener(event -> inspection.run());
        submit.addActionListener(event -> {
            if (!matching(password, confirm)) return;
            if (link.invitation && username.getText().trim().isEmpty()) { message.setText("Enter a username."); revalidate(); return; }
            submit.setEnabled(false); inspect.setEnabled(false); back.setEnabled(false); message.setText("Submitting…"); revalidate();
            String secret = password.getText(); password.setText(""); confirm.setText("");
            session.complete(link, inspected, username.getText().trim(), secret, () -> { if (!left) { left = true; done.run(); } }, error -> {
                if (left) return;
                inspected = null; inspect.setEnabled(true); back.setEnabled(true);
                message.setText(error + " Use Inspect link to check its current state."); revalidate();
            });
        });
        Container content = panel();
        if (link.invitation) content.add(new Label("Username", "Muted")).add(username);
        content.add(new Label("New password", "Muted")).add(password).add(new Label("Confirm password", "Muted")).add(confirm).add(inspect).add(submit);
        addCancel(content, cancel); add(BorderLayout.CENTER, content);
        // Perform the initial inspection once the form is displayed, without consuming the link.
        CN.callSerially(() -> { if (!left) inspection.run(); });
    }

    public AccountForm(AuthSession session, Runnable done, Runnable cancel) {
        super("Change password", new BorderLayout());
        TextField current = field("Current password", true), password = field("New password", true), confirm = field("Confirm password", true);
        message.setText("Changing your password signs out all your devices. Choose 12–256 characters.");
        Button submit = new Button("Change password");
        submit.addActionListener(event -> {
            if (!matching(password, confirm)) return;
            if (current.getText().isEmpty()) { message.setText("Enter your current password."); revalidate(); return; }
            submit.setEnabled(false); back.setEnabled(false); message.setText("Changing password…"); revalidate();
            String old = current.getText(), secret = password.getText(); current.setText(""); password.setText(""); confirm.setText("");
            session.changePassword(old, secret, () -> { if (!left) { left = true; done.run(); } }, error -> {
                if (!left) { submit.setEnabled(true); back.setEnabled(true); message.setText(error); revalidate(); }
            });
        });
        Container content = panel();
        content.add(new Label("Current password", "Muted")).add(current).add(new Label("New password", "Muted")).add(password)
                .add(new Label("Confirm password", "Muted")).add(confirm).add(submit);
        addCancel(content, cancel); add(BorderLayout.CENTER, content);
    }
    private Container panel() {
        getToolbar().setHidden(true); getContentPane().setSafeArea(true);
        Container content = BoxLayout.encloseY(new Label(getTitle(), "NowTitle"), message);
        content.setUIID("LoginPanel"); content.setScrollableY(true); return content;
    }
    private void addCancel(Container content, Runnable cancel) {
        back = new Button("Cancel"); back.addActionListener(event -> { left = true; cancel.run(); }); content.add(back);
    }
    private boolean matching(TextField password, TextField confirm) {
        if (password.getText().isEmpty() || !password.getText().equals(confirm.getText())) {
            message.setText("Enter a new password and matching confirmation."); revalidate(); return false;
        }
        return true;
    }
    private static TextField field(String name, boolean secret) {
        TextField field = new TextField("", name, 24, secret ? TextField.PASSWORD : TextField.NON_PREDICTIVE);
        field.setName(name); field.setMaxSize(secret ? 256 : 64); return field;
    }
}
