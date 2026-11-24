(function (exports) {
    'use strict';
    exports.dev_normalbias_client_net_BrowserAccountLink = {
        isSupported_: function (callback) { callback.complete(true); },
        openService__java_lang_String: function (url, callback) {
            window.location.assign(url);
            callback.complete();
        },
        takeLink_: function (callback) {
            var link = window.normalBiasAccountLink || '';
            delete window.normalBiasAccountLink;
            callback.complete(link);
        }
    };
})(cn1_get_native_interfaces());
