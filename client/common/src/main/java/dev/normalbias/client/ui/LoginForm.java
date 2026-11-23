package dev.normalbias.client.ui;

import com.codename1.ui.*;
import com.codename1.components.SpanLabel;
import com.codename1.ui.layouts.*;
import dev.normalbias.client.net.AuthSession;

/** Shared login UI; the server decides which login action is available. */
public final class LoginForm extends Form {
    public LoginForm(AuthSession session, Runnable ready) { this(session, ready, () -> {}); }
    public LoginForm(AuthSession session, Runnable ready, Runnable useLink) {
        super("Normal Bias", new BorderLayout());
        getToolbar().setHidden(true); getContentPane().setSafeArea(true);
        TextField username = new TextField("", "Username", 24, TextField.NON_PREDICTIVE);
        username.setName("Username"); username.setMaxSize(64);
        TextField password = new TextField("", "Password", 24, TextField.PASSWORD);
        password.setName("Password"); password.setMaxSize(256);
        SpanLabel message = new SpanLabel("Sign in to your library. This device will stay signed in until you sign out or the server session expires.", "Muted");
        Button login = new Button("Sign in"); login.setName("Sign in");
        login.addActionListener(event -> {
            if (username.getText().trim().isEmpty() || password.getText().isEmpty()) { message.setText("Enter your username and password."); revalidate(); return; }
            login.setEnabled(false); message.setText("Signing in…"); revalidate();
            String secret = password.getText(); password.setText("");
            session.login(username.getText().trim(), secret, ready, text -> { login.setEnabled(true); message.setText(text); revalidate(); });
        });
        Container content = BoxLayout.encloseY(new Label("Normal Bias", "Brand"), new Label("Your music, signed in", "NowTitle"), message,
                new Label("Username", "Muted"), username, new Label("Password", "Muted"), password, login);
        Button redeem = new Button("Use invitation or reset link");
        redeem.addActionListener(event -> useLink.run()); content.add(redeem);
        content.setUIID("LoginPanel"); content.setScrollableY(true);
        add(BorderLayout.CENTER, content);
    }
}
