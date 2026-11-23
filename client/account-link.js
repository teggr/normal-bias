/* Location/history glue only. Java validates links and owns account workflows. */
(function () {
    'use strict';
    function capture() {
        if (!location.hash) return;
        window.normalBiasAccountLink = location.href;
        history.replaceState(null, '', location.pathname + location.search);
    }
    capture();
    addEventListener('hashchange', capture);
})();
