package dev.normalbias.client.net;
import com.codename1.system.NativeInterface;
/** Browser location/history integration only; link policy and UI stay in Java. */
public interface BrowserAccountLink extends NativeInterface {
    String takeLink();
}
