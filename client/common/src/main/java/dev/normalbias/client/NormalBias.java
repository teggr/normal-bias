package dev.normalbias.client;

import com.codename1.system.Lifecycle;
import com.codename1.ui.CN;
import com.codename1.io.NetworkEvent;
import com.codename1.ui.Form;
import com.codename1.ui.Label;
import dev.normalbias.client.net.TrialOrigin;
import dev.normalbias.client.net.LibraryApi;
import dev.normalbias.client.net.AuthSession;
import dev.normalbias.client.ui.LoginForm;
import com.codename1.ui.Button;
import dev.normalbias.client.ui.LibraryForm;

// The pinned iOS generator probes optional callback interfaces on this type.
// Keep it extensible so those generated instanceof checks compile.
public class NormalBias extends Lifecycle {
    private LibraryForm library;
    private AuthSession session;
    private boolean connecting;
    private String identity;
    // This local PoC has no cloud reporting account. Each request supplies its own retry state.
    @Override protected void bindCrashProtection() { }
    @Override protected void handleNetworkError(NetworkEvent event) {
        if (event.getSource() instanceof dev.normalbias.client.net.ApiRequest)
            ((dev.normalbias.client.net.ApiRequest) event.getSource()).networkFailed();
        event.consume();
    }
    @Override public void runApp() {
        if (library == null && session == null) {
            CN.setProperty("platformHint.javascript.beforeUnloadMessage", null);
            String browserPage = CN.getProperty("browser.window.location.href", null);
            String bundled = TrialOrigin.bundled();
            String page = browserPage != null ? browserPage : bundled != null ? bundled
                    : System.getProperty("normalbias.apiOrigin", "http://127.0.0.1:8080/");
            if (browserPage == null && bundled == null && !CN.isSimulator()) {
                Form setup = new Form("Local device trial");
                setup.add(new Label("Build with client/build-ios-trial.sh and a private HTTPS API origin."));
                setup.show(); return;
            }
            session = new AuthSession(page, browserPage != null);
            session.onLost(() -> { disposeLibrary(); connect(); });
        }
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
    private void connect() {
        if (connecting) return;
        connecting = true;
        Form waiting = new Form("Normal Bias"); waiting.add(new Label("Connecting securely…")); waiting.show();
        session.restore(() -> { connecting = false; showSession(); }, error -> {
            connecting = false;
            Form failed = new Form("Normal Bias"); failed.add(new com.codename1.components.SpanLabel(error));
            Button retry = new Button("Retry connection"); retry.addActionListener(event -> connect()); failed.add(retry); failed.show();
        });
    }
    private void showSession() {
        if (!session.authenticated() && !session.demo()) { new LoginForm(session, this::showSession).show(); return; }
        identity = session.identity();
        library = new LibraryForm(new LibraryApi(session), () -> {
            disposeLibrary();
            Form signingOut = new Form("Normal Bias"); signingOut.add(new Label("Signing out…")); signingOut.show();
            session.logout(this::connect, error -> {
                Form failed = new Form("Sign out pending"); failed.add(new com.codename1.components.SpanLabel(error));
                Button retry = new Button("Retry sign out"); retry.addActionListener(event -> connect()); failed.add(retry); failed.show();
            });
        });
        library.show(); library.resumeArtwork();
    }
    private void disposeLibrary() { if (library != null) { library.disposePlayer(); library = null; } }
    // Backgrounding preserves the active media and queue; destruction still releases them.
    @Override public void destroy() { if (library != null) library.disposePlayer(); }
}
