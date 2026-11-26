package dev.normalbias.client.net;

import com.codename1.io.ConnectionRequest;

/** Routes global transport errors back to the request's own recovery UI. */
public abstract class ApiRequest extends ConnectionRequest {
    public final void networkFailed(Exception error) { handleException(error == null ? new java.io.IOException("Service connection failed") : error); }
}
