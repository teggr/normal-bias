/* Browser-only audio glue. UI, catalogue access and queue policy live in Java.
 * CN1's native interface runs this object on the browser's main thread, so
 * HTMLAudioElement methods are never invoked on a worker-side host proxy.
 */
(function (exports) {
    'use strict';
    var audio = null;
    var error = '';
    var waiting = false;
    var loadingSince = 0;
    var playVersion = 0;

    function close() {
        playVersion++;
        if (audio) {
            var old = audio;
            audio = null;
            old.pause();
            old.removeAttribute('src');
            old.load();
            old.remove();
        }
        error = '';
        waiting = false;
        loadingSince = 0;
    }

    exports.dev_normalbias_client_player_BrowserAudio = {
        isSupported_: function (callback) {
            callback.complete(typeof Audio === 'function');
        },
        open__java_lang_String: function (url, callback) {
            close();
            try {
                var parsed = new URL(url, window.location.href);
                if (parsed.origin !== window.location.origin || !/^\/api\/tracks\/[a-f0-9]{64}\/audio$/.test(parsed.pathname)) {
                    throw new Error('Invalid audio URL');
                }
                var current = new Audio();
                audio = current;
                loadingSince = Date.now();
                current.preload = 'auto';
                current.hidden = true;
                // Keep a real media element attached for normal browser lifecycle handling.
                document.body.appendChild(current);
                current.addEventListener('error', function () {
                    if (audio === current) {
                        error = 'Cannot play this track. Check the connection and press Retry track.';
                        waiting = false;
                    }
                });
                current.addEventListener('waiting', function () { if (audio === current) waiting = true; });
                current.addEventListener('stalled', function () { if (audio === current) waiting = true; });
                current.addEventListener('playing', function () { if (audio === current) waiting = false; });
                current.addEventListener('canplay', function () { if (audio === current) waiting = false; });
                current.src = parsed.href;
                callback.complete();
            } catch (ex) {
                error = 'Cannot open this track. Press Retry track.';
                callback.complete();
            }
        },
        play_: function (callback) {
            var current = audio;
            var version = ++playVersion;
            error = '';
            if (current) {
                try {
                    var attempt = current.play();
                    if (attempt && attempt.catch) attempt.catch(function (ex) {
                        if (audio !== current || version !== playVersion) return;
                        error = ex.name === 'NotAllowedError'
                            ? 'The browser blocked audio. Press Play to allow playback.'
                            : 'Playback interrupted. Press Retry track to reconnect.';
                        waiting = false;
                    });
                } catch (ex) {
                    error = 'Playback interrupted. Press Retry track to reconnect.';
                }
            }
            // Never return the play Promise: the worker must stay responsive while buffering.
            callback.complete();
        },
        pause_: function (callback) {
            playVersion++;
            if (audio) audio.pause();
            waiting = false;
            callback.complete();
        },
        seek__int: function (milliseconds, callback) {
            if (audio && Number.isFinite(audio.duration)) {
                try { audio.currentTime = Math.max(0, Math.min(audio.duration, milliseconds / 1000)); }
                catch (ex) { error = 'Cannot seek yet. Wait for audio to load and try again.'; }
            }
            callback.complete();
        },
        snapshot_: function (callback) {
            if (audio && audio.readyState === 0 && Date.now() - loadingSince > 15000 && !error) {
                error = 'Audio is taking too long to load. Check the connection and press Retry track.';
            }
            callback.complete(JSON.stringify({
                time: audio && Number.isFinite(audio.currentTime) ? Math.round(audio.currentTime * 1000) : 0,
                duration: audio && Number.isFinite(audio.duration) ? Math.round(audio.duration * 1000) : 0,
                playing: !!audio && !audio.paused && !audio.ended,
                loading: !!audio && audio.readyState === 0 && !error,
                ended: !!audio && audio.ended,
                waiting: waiting,
                error: error
            }));
        },
        close_: function (callback) { close(); callback.complete(); }
    };
})(cn1_get_native_interfaces());
