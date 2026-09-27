/* Shared helpers for all pages. window.MSG is inlined by the server from messages.properties. */
(function () {
    'use strict';
    const MSG = window.MSG || {};

    /** t('test.score', 3, 7) → "3 / 7 mutants killed"; unknown keys are returned as-is. */
    window.t = function (key, ...args) {
        let s = Object.prototype.hasOwnProperty.call(MSG, key) ? MSG[key] : key;
        args.forEach((a, i) => { s = s.split('{' + i + '}').join(String(a)); });
        return s;
    };

    /** Translate a server error key, falling back to a generic message. */
    window.tErr = function (key) {
        if (!key) return window.t('error.generic');
        return Object.prototype.hasOwnProperty.call(MSG, key) ? MSG[key] : window.t('error.generic');
    };

    window.esc = function (s) {
        return String(s == null ? '' : s)
            .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
    };

    window.fmtClock = function (ms) {
        const total = Math.max(0, Math.ceil(ms / 1000));
        const m = Math.floor(total / 60), s = total % 60;
        return m + ':' + String(s).padStart(2, '0');
    };

    /**
     * EventSource with reconnect: the browser retries on its own, but gives up after an
     * HTTP error; we then reconnect after a delay. onOpen runs on every (re)connect so the
     * page can re-fetch state it may have missed while offline.
     */
    window.liveStream = function (url, handlers, onOpen, onStatus) {
        let es = null, retryTimer = null;
        function connect() {
            if (es) es.close();
            es = new EventSource(url);
            es.addEventListener('connected', () => { onStatus && onStatus(true); onOpen && onOpen(); });
            Object.entries(handlers).forEach(([name, fn]) => es.addEventListener(name, e => {
                let data = {};
                try { data = e.data ? JSON.parse(e.data) : {}; } catch (_) { /* heartbeat etc. */ }
                fn(data);
            }));
            es.onerror = () => {
                onStatus && onStatus(false);
                if (es.readyState === EventSource.CLOSED) {
                    clearTimeout(retryTimer);
                    retryTimer = setTimeout(connect, 3000);
                }
            };
        }
        connect();
        return { reconnect: connect, close: () => es && es.close() };
    };
})();
