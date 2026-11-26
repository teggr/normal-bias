package dev.normalbias.client;

import com.codename1.system.Lifecycle;
import com.codename1.ui.CN;
import com.codename1.io.NetworkEvent;
import com.codename1.ui.Form;
import com.codename1.ui.Label;
import dev.normalbias.client.net.TrialOrigin;
import dev.normalbias.client.net.ServiceAddress;
import com.codename1.io.Preferences;
import dev.normalbias.client.net.LibraryApi;
import dev.normalbias.client.net.AuthSession;
import dev.normalbias.client.ui.LoginForm;
import dev.normalbias.client.ui.AccountForm;
import dev.normalbias.client.net.AccountLink;
import dev.normalbias.client.net.BrowserAccountLink;
import com.codename1.system.NativeLookup;
import com.codename1.ui.TextField;
import com.codename1.components.SpanLabel;
import com.codename1.ui.Button;
import dev.normalbias.client.ui.LibraryForm;

// The pinned iOS generator probes optional callback interfaces on this type.
// Keep it extensible so those generated instanceof checks compile.
public class NormalBias extends Lifecycle {
    private LibraryForm library;
    private AuthSession session;
    private boolean connecting;
    private int connectionAttempt;
    private boolean initialized;
    private String browserOrigin;
    private String developmentOrigin;
    private String initialOrigin;
    private LoginForm landing;
    private static final String SERVICE_PREFERENCE = "normalbias.service.origin";
    private String identity;
    private String pendingLink;
    private BrowserAccountLink browserLinks;
    private boolean accountVisible;
    private java.util.Timer linkTimer;
    // This local PoC has no cloud reporting account. Each request supplies its own retry state.
    @Override protected void bindCrashProtection() { }
    @Override protected void handleNetworkError(NetworkEvent event) {
        if (event.getSource() instanceof dev.normalbias.client.net.ApiRequest)
            ((dev.normalbias.client.net.ApiRequest) event.getSource()).networkFailed(event.getError());
        event.consume();
    }
    @Override public void runApp() {
        if (!initialized) {
            initialized = true;
            // Phones keep the full-size portrait player; tablets retain both orientations.
            if ("ios".equals(CN.getPlatformName()) && !CN.isTablet() && CN.canForceOrientation())
                CN.lockOrientation(true);
            CN.setProperty("platformHint.javascript.beforeUnloadMessage", null);
            String browserPage = CN.getProperty("browser.window.location.href", null);
            String bundled = TrialOrigin.bundled();
            if (browserPage != null) {
                int end = browserPage.indexOf('/', browserPage.indexOf("://") + 3);
                browserOrigin = end < 0 ? browserPage : browserPage.substring(0, end);
                if (browserOrigin.startsWith("https://")) browserOrigin = ServiceAddress.normalize(browserOrigin);
                else developmentOrigin = browserOrigin;
                initialOrigin = browserOrigin;
                browserLinks = NativeLookup.create(BrowserAccountLink.class);
                takeBrowserLink();
                linkTimer = CN.setInterval(500, () -> {
                    if (takeBrowserLink() && !connecting && session != null) showSession();
                });
            } else {
                String configured = System.getProperty("normalbias.apiOrigin", null);
                if (configured == null && CN.isSimulator() && !"ios".equals(CN.getPlatformName()) && bundled == null) configured = "http://127.0.0.1:8080";
                String fallback = bundled != null ? bundled : configured;
                if (fallback != null && fallback.startsWith("http://")) developmentOrigin = fallback;
                String saved = Preferences.get(SERVICE_PREFERENCE, "");
                try { initialOrigin = saved.isEmpty() ? fallback : ServiceAddress.normalize(saved); }
                catch (IllegalArgumentException invalid) { initialOrigin = fallback; }
            }
            if (initialOrigin != null && !initialOrigin.isEmpty()) selectSession(initialOrigin);
        }
        if (accountVisible || landing != null && CN.getCurrentForm() == landing) return;
        if (library != null) {
            library.show(); library.resumeArtwork();
            session.restore(() -> {
                if (library != null && (!session.demo() && !session.authenticated() || !identity.equals(session.identity()))) {
                    disposeLibrary(); showSession();
                }
            }, ignored -> {}); // Connectivity failures preserve the current session and playback.
        }
        else connect();
    }
    private void selectSession(String origin) {
        if (session != null && session.origin.equals(origin)) return;
        if (session != null) session.invalidate();
        session = new AuthSession(origin, browserOrigin != null);
        session.onLost(() -> { disposeLibrary(); connect(); });
    }
    private void showLanding() {
        landing = new LoginForm(session == null ? "" : session.origin, browserOrigin, developmentOrigin,
                this::submitConnection, this::checkConnection, origin -> {
                    if (connecting) return;
                    selectSession(origin);
                    connecting = true;
                    landing.busy("Opening account link…");
                    session.restore(() -> { connecting = false; rememberService(); pasteLink(); }, error -> {
                        connecting = false; landing.connectionFailed(error);
                    });
                });
        landing.show();
    }
    private void connect() {
        if (connecting) return;
        showLanding();
        if (session != null) restoreConnection(null, null);
    }
    private void submitConnection(String origin, String username, String password, boolean retry) {
        if (connecting) return;
        if (browserOrigin != null && !browserOrigin.equals(origin)) {
            landing.busy("Opening service…");
            browserLinks.openService(origin + "/");
            return;
        }
        selectSession(origin);
        restoreConnection(retry ? null : username, retry ? null : password);
    }
    private void checkConnection(String origin) {
        if (origin == null) {
            connectionAttempt++; connecting = false;
            if (session != null) session.invalidate();
            return;
        }
        selectSession(origin);
        restoreConnection(null, null);
    }
    private void restoreConnection(String username, String password) {
        connecting = true;
        final int attempt = ++connectionAttempt;
        LoginForm view = landing;
        AuthSession selected = session;
        selected.invalidate();
        view.checking();
        selected.restore(() -> {
            if (session != selected || landing != view || attempt != connectionAttempt) return;
            view.connected();
            rememberService();
            if (selected.authenticated() || selected.demo() || pendingLink != null) {
                connecting = false; showSession();
            } else if (username != null) {
                view.busy("Signing in…");
                selected.login(username, password, () -> { connecting = false; showSession(); }, error -> {
                    if (landing != view || session != selected || attempt != connectionAttempt) return;
                    connecting = false;
                    if (selected.reachable()) view.status(error, false); else view.connectionFailed(error);
                });
            } else {
                connecting = false;
                view.status("Sign in to your music. You’ll stay signed in until you sign out or your session expires.", false);
                view.focusAfterCheck();
            }
        }, error -> {
            if (session != selected || landing != view || attempt != connectionAttempt) return;
            connecting = false;
            view.connectionFailed(error + (selected.hasRememberedSignIn() ? " Your sign-in has been kept." : ""));
        });
    }
    private void rememberService() {
        if (browserOrigin == null) Preferences.set(SERVICE_PREFERENCE, session.origin);
    }
    private void showSession() {
        if (pendingLink != null) { openPendingLink(); return; }
        accountVisible = false;
        if (!session.authenticated() && !session.demo()) { connect(); return; }
        identity = session.identity();
        library = new LibraryForm(new LibraryApi(session), () -> {
            disposeLibrary();
            Form signingOut = new Form("Normal Bias"); signingOut.add(new Label("Signing out…")); signingOut.show();
            session.logout(this::connect, error -> {
                Form failed = new Form("Sign out pending"); failed.add(new com.codename1.components.SpanLabel(error));
                Button retry = new Button("Retry sign out"); retry.addActionListener(event -> connect()); failed.add(retry); failed.show();
            });
        }, session.link("change-password", "POST") == null ? null : () -> {
            accountVisible = true;
            new AccountForm(session, () -> {
                accountVisible = false; disposeLibrary(); connect();
            }, () -> { accountVisible = false; library.show(); }).show();
        });
        library.show(); library.resumeArtwork();
    }
    private boolean takeBrowserLink() {
        if (browserLinks == null) return false;
        String link = browserLinks.takeLink();
        if (link == null || link.isEmpty()) return false;
        pendingLink = link; return true;
    }
    private void pasteLink() {
        accountVisible = true;
        Form form = new Form("Open account link", new com.codename1.ui.layouts.BoxLayout(com.codename1.ui.layouts.BoxLayout.Y_AXIS));
        TextField link = new TextField("", "Invitation or reset link", 30, TextField.URL); link.setName("Account link"); link.setMaxSize(2048);
        SpanLabel message = new SpanLabel("Paste an invitation or reset link from this service.");
        Button open = new Button("Open link"), cancel = new Button("Cancel");
        open.addActionListener(event -> {
            try { AccountLink.parse(link.getText().trim(), session.origin); }
            catch (IllegalArgumentException invalid) { message.setText(invalid.getMessage()); form.revalidate(); return; }
            pendingLink = link.getText().trim(); link.setText(""); openPendingLink();
        });
        cancel.addActionListener(event -> { accountVisible = false; showSession(); });
        form.add(message).add(link).add(open).add(cancel); form.show();
    }
    private void openPendingLink() {
        accountVisible = true;
        if (session.authenticated()) {
            Form confirm = new Form("Account link");
            confirm.add(new SpanLabel("Sign out before using this account link. This stops playback and clears your queue."));
            Button signOut = new Button("Sign out and continue"), cancel = new Button("Cancel");
            signOut.addActionListener(event -> {
                signOut.setEnabled(false); disposeLibrary();
                session.logout(() -> session.restore(this::openPendingLink, error -> { signOut.setEnabled(true); confirm.add(new SpanLabel(error)); confirm.revalidate(); }),
                    error -> { signOut.setEnabled(true); confirm.add(new SpanLabel(error)); confirm.revalidate(); });
            });
            cancel.addActionListener(event -> { pendingLink = null; accountVisible = false; if (library != null) library.show(); else connect(); });
            confirm.add(signOut).add(cancel); confirm.show(); return;
        }
        String value = pendingLink; pendingLink = null;
        try {
            AccountLink link = AccountLink.parse(value, session.origin);
            new AccountForm(session, link, () -> {
                accountVisible = false;
                Form success = new Form("Ready to sign in");
                success.add(new SpanLabel(link.invitation ? "Account created. Sign in with your new username and password." : "Password reset. Sign in with your new password."));
                Button signIn = new Button("Continue to sign in"); signIn.addActionListener(event -> connect()); success.add(signIn); success.show();
            }, () -> { accountVisible = false; connect(); }).show();
        } catch (IllegalArgumentException invalid) {
            Form error = new Form("Account link"); error.add(new SpanLabel(invalid.getMessage()));
            Button back = new Button("Back to sign in"); back.addActionListener(event -> { accountVisible = false; connect(); }); error.add(back); error.show();
        }
    }
    private void disposeLibrary() { if (library != null) { library.disposePlayer(); library = null; } }
    // Backgrounding preserves the active media and queue; destruction still releases them.
    @Override public void destroy() { if (linkTimer != null) linkTimer.cancel(); if (library != null) library.disposePlayer(); }
}
