package dev.normalbias.client;

import com.codename1.system.Lifecycle;
import com.codename1.ui.CN;
import dev.normalbias.client.net.LibraryApi;
import dev.normalbias.client.ui.LibraryForm;

public final class NormalBias extends Lifecycle {
    private LibraryForm library;
    @Override public void runApp() {
        if (library == null) {
            CN.setProperty("platformHint.javascript.beforeUnloadMessage", null);
            String page = CN.getProperty("browser.window.location.href", "http://127.0.0.1:8080/");
            library = new LibraryForm(new LibraryApi(page));
        }
        library.show();
    }
    @Override public void destroy() { if (library != null) library.disposePlayer(); }
}
