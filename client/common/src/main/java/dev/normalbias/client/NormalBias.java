package dev.normalbias.client;

import com.codename1.system.Lifecycle;
import com.codename1.ui.CN;
import com.codename1.io.NetworkEvent;
import com.codename1.ui.Form;
import com.codename1.ui.Label;
import dev.normalbias.client.net.TrialOrigin;
import dev.normalbias.client.net.LibraryApi;
import dev.normalbias.client.ui.LibraryForm;

// The pinned iOS generator probes optional callback interfaces on this type.
// Keep it extensible so those generated instanceof checks compile.
public class NormalBias extends Lifecycle {
    private LibraryForm library;
    // This local PoC has no cloud reporting account. Each request supplies its own retry state.
    @Override protected void bindCrashProtection() { }
    @Override protected void handleNetworkError(NetworkEvent event) { event.consume(); }
    @Override public void runApp() {
        if (library == null) {
            CN.setProperty("platformHint.javascript.beforeUnloadMessage", null);
            String page = CN.isSimulator()
                    ? System.getProperty("normalbias.apiOrigin", "http://127.0.0.1:8080/")
                    : CN.getProperty("browser.window.location.href", "http://127.0.0.1:8080/");
            if (!CN.isSimulator() && "ios".equals(CN.getPlatformName())) {
                page = TrialOrigin.bundled();
                if (page == null) {
                    Form setup = new Form("Local device trial");
                    setup.add(new Label("Build with client/build-ios-trial.sh and a private API origin."));
                    setup.show();
                    return;
                }
            }
            library = new LibraryForm(new LibraryApi(page));
        }
        library.show();
    }
    // Backgrounding preserves the active media and queue; destruction still releases them.
    @Override public void destroy() { if (library != null) library.disposePlayer(); }
}
