/* ═══════════════════════════════════════════════════════
   Student Page – /   (mobile first)
   ═══════════════════════════════════════════════════════ */
(function () {
    'use strict';

    const $ = (s, p) => (p || document).querySelector(s);
    const show = el => { if (el) el.style.display = ''; };
    const hide = el => { if (el) el.style.display = 'none'; };

    const screens = ['join', 'waiting', 'game', 'closed', 'revealed', 'kicked'];
    let currentScreen = null;
    let stream = null;
    let status = null;
    let fieldsKey = null;       // which problem's fields are rendered
    let timerInterval = null;
    let refreshing = false, refreshAgain = false;

    function showScreen(name) {
        screens.forEach(k => (k === name ? show : hide)($('#' + k + '-screen')));
        if (currentScreen !== name) window.scrollTo(0, 0);
        currentScreen = name;
    }

    /* ── API helpers ────────────────────────────────── */

    async function api(url, opts = {}) {
        const res = await fetch(url, {
            headers: { 'Content-Type': 'application/json' },
            credentials: 'same-origin',
            cache: 'no-store',
            ...opts
        });
        let body = {};
        try { body = await res.json(); } catch (_) { /* empty body */ }
        body._status = res.status;
        return body;
    }

    function setAlert(el, kind, text) {
        el.className = 'alert alert-' + kind;
        el.textContent = text;
        show(el);
    }

    /* ── Join ───────────────────────────────────────── */

    const sidInput = $('#join-sid');
    const requiredLen = parseInt(sidInput.dataset.length || '0', 10);
    if (requiredLen > 0) sidInput.placeholder = t('join.studentId.placeholder.length', requiredLen);
    sidInput.addEventListener('input', () => { sidInput.value = sidInput.value.replace(/\D/g, ''); });

    $('#join-form').addEventListener('submit', async e => {
        e.preventDefault();
        const err = $('#join-error');
        hide(err);
        const fullName = $('#join-name').value.trim();
        const studentNumber = sidInput.value.trim();
        if (!fullName) return setAlert(err, 'error', t('join.error.name.required'));
        if (!studentNumber) return setAlert(err, 'error', t('join.error.studentId.required'));

        const btn = $('#join-btn');
        btn.disabled = true;
        try {
            const data = await api('/api/join', {
                method: 'POST',
                body: JSON.stringify({ fullName, studentNumber, nickname: $('#join-nick').value.trim() })
            });
            if (data.success) {
                enterGame();
            } else if (data.error === 'join.error.studentId.length' && requiredLen > 0) {
                setAlert(err, 'error', t('join.error.studentId.lengthN', requiredLen));
            } else {
                setAlert(err, 'error', tErr(data.error));
            }
        } catch (_) {
            setAlert(err, 'error', t('error.network'));
        } finally {
            btn.disabled = false;
        }
    });

    /* ── Status ────────────────────────────────────── */

    function enterGame() {
        showScreen('waiting');
        if (!stream) connectStream();
        refreshStatus();
    }

    /** Re-fetch the full state; calls that arrive while one is running are coalesced. */
    async function refreshStatus() {
        if (refreshing) { refreshAgain = true; return; }
        refreshing = true;
        try {
            const s = await api('/api/status');
            if (s._status === 401) { stopStream(); showScreen('join'); return; }
            if (s._status === 403) { stopStream(); showScreen('kicked'); return; }
            if (s._status !== 200) return;
            status = s;
            render(s);
        } catch (_) {
            // offline – the stream reconnect will trigger another refresh
        } finally {
            refreshing = false;
            if (refreshAgain) { refreshAgain = false; refreshStatus(); }
        }
    }

    function render(s) {
        $('#player-name').textContent = s.displayName || s.playerName || '';
        startTimer(s);

        switch (s.roundStatus) {
            case 'OPEN':
                showScreen('game');
                renderGame(s);
                break;
            case 'CLOSED':
                showScreen('closed');
                $('#closed-score').innerHTML = scoreHtml(s);
                renderSubmissions($('#closed-submissions'), s, false);
                break;
            case 'REVEALED':
                showScreen('revealed');
                renderRevealed(s);
                break;
            default:
                showScreen('waiting');
                $('#waiting-count').textContent = participantsText(s.participantCount);
        }
    }

    function scoreHtml(s) {
        return `<span class="score-num">${s.killed} / ${s.totalMutants}</span>` +
               `<span class="score-label">${esc(t('test.score.label'))}</span>`;
    }

    function participantsText(n) {
        return n === 1 ? t('waiting.participants.one') : t('waiting.participants', n);
    }

    function remainingText(n) {
        if (n < 0) return t('test.remaining.unlimited');
        if (n === 0) return t('test.remaining.none');
        if (n === 1) return t('test.remaining.one');
        return t('test.remaining', n);
    }

    function renderGame(s) {
        $('#round-name').textContent = s.roundName || '';
        $('#score-display').textContent = t('test.score', s.killed, s.totalMutants);
        $('#tests-remaining').textContent = remainingText(s.testsRemaining);
        $('#tests-remaining').classList.toggle('badge-warn', s.testsRemaining >= 0 && s.testsRemaining <= 2);
        $('#spec-content').innerHTML = s.specification || '';   // trusted, from the server's Problem class

        renderInputFields(s.functionInputFields, s.expectedField);
        renderSubmissions($('#submissions'), s, false);

        const btn = $('#submit-btn');
        const noneLeft = s.testsRemaining === 0;
        btn.disabled = noneLeft;
        btn.textContent = noneLeft ? t('test.remaining.none') : t('test.submit');
    }

    /* ── Test form ─────────────────────────────────── */

    function renderInputFields(fields, expectedField) {
        if (!fields || !expectedField) return;
        const all = [...fields, expectedField];
        const key = all.map(f => f.id).join('|');
        if (key === fieldsKey) return;   // keep what the student is typing
        fieldsKey = key;

        const container = $('#input-fields');
        container.innerHTML = '';
        all.forEach((f, i) => {
            const isList = f.id === 'list';
            const isLast = i === all.length - 1;
            const div = document.createElement('div');
            div.className = 'field';
            div.innerHTML = `
                <label for="field-${esc(f.id)}">${esc(t(f.label))}</label>
                <div class="input-with-keys">
                    <input id="field-${esc(f.id)}" name="${esc(f.id)}" type="text"
                           inputmode="${esc(f.inputMode || 'text')}" autocomplete="off" autocorrect="off"
                           autocapitalize="off" spellcheck="false" maxlength="${isList ? 300 : 12}"
                           enterkeyhint="${isLast ? 'send' : 'next'}"
                           placeholder="${esc(t(f.placeholder))}"/>
                    <button type="button" class="key-btn" data-insert="-" data-for="field-${esc(f.id)}"
                            aria-label="${esc(t('test.key.minus'))}">−</button>
                    ${isList ? `<button type="button" class="key-btn" data-insert="," data-for="field-${esc(f.id)}"
                            aria-label="${esc(t('test.key.comma'))}">,</button>` : ''}
                </div>`;
            container.appendChild(div);
        });

        // iOS number pads have no minus sign / comma: insert them at the cursor
        container.querySelectorAll('.key-btn').forEach(b => {
            b.addEventListener('pointerdown', e => e.preventDefault());   // keep keyboard open
            b.addEventListener('click', () => insertAtCursor($('#' + b.dataset.for), b.dataset.insert));
        });
        // Enter jumps to the next field, submits on the last one
        const inputs = [...container.querySelectorAll('input')];
        inputs.forEach((inp, i) => inp.addEventListener('keydown', e => {
            if (e.key === 'Enter' && i < inputs.length - 1) { e.preventDefault(); inputs[i + 1].focus(); }
        }));
    }

    function insertAtCursor(input, text) {
        const start = input.selectionStart ?? input.value.length;
        const end = input.selectionEnd ?? input.value.length;
        input.value = input.value.slice(0, start) + text + input.value.slice(end);
        input.focus();
        input.setSelectionRange(start + text.length, start + text.length);
    }

    $('#test-form').addEventListener('submit', async e => {
        e.preventDefault();
        const fb = $('#submit-feedback');
        hide(fb);
        const btn = $('#submit-btn');
        if (btn.disabled) return;
        btn.disabled = true;
        btn.textContent = t('test.submitting');

        const body = {};
        const inputs = $('#input-fields').querySelectorAll('input');
        inputs.forEach(inp => { body[inp.name] = inp.value.trim(); });

        try {
            const res = await api('/api/submit', { method: 'POST', body: JSON.stringify(body) });
            if (res._status === 401 || res._status === 403) { refreshStatus(); return; }
            if (res.success) {
                if (res.validity === 'INVALID') {
                    setAlert(fb, 'warn', '✗ ' + t('test.result.invalid') + ' – ' + tErr(res.message));
                } else {
                    setAlert(fb, 'success', '✓ ' + t('test.result.valid.message', res.killed, res.totalMutants));
                }
                inputs.forEach(inp => { inp.value = ''; });
                if (navigator.vibrate) navigator.vibrate(30);
            } else {
                setAlert(fb, 'error', tErr(res.message || res.error));
            }
        } catch (_) {
            setAlert(fb, 'error', t('error.network'));
        } finally {
            btn.disabled = false;
            btn.textContent = t('test.submit');
            await refreshStatus();
        }
    });

    /* ── Submissions ───────────────────────────────── */

    function renderSubmissions(container, s, revealed) {
        const subs = s.submissions || [];
        if (subs.length === 0) {
            container.innerHTML = `<p class="muted small">${esc(t('test.history.empty'))}</p>`;
            return;
        }
        container.innerHTML = subs.slice().reverse().map((sub, idx) => {
            const n = subs.length - idx;
            const valid = sub.validity === 'VALID';
            let detail = '';
            if (revealed) {
                if (!valid) {
                    detail = `<div class="sub-detail">${esc(t('reveal.test.invalid', sub.referenceResult))}</div>`;
                } else if (sub.mutantResults) {
                    detail = '<div class="sub-mutants">' + sub.mutantResults.map(mr =>
                        `<span class="mutant-chip ${mr.killed ? 'killed' : 'survived'}"
                               title="${esc(t(mr.killed ? 'reveal.chip.killed' : 'reveal.chip.survived'))}">
                            ${esc(mr.mutantId)} ${mr.killed ? '✓' : '✗'}</span>`).join('') + '</div>';
                }
            }
            return `
                <div class="sub-card ${valid ? 'valid' : 'invalid'}">
                    <div class="sub-header">
                        <span class="sub-num">#${n}</span>
                        <code class="sub-inputs">indexOf([${esc(sub.list)}], ${esc(sub.target)}) → ${esc(sub.expected)}</code>
                        <span class="sub-validity ${valid ? 'valid-text' : 'invalid-text'}">${esc(t(valid ? 'test.result.valid' : 'test.result.invalid'))}</span>
                    </div>${detail}
                </div>`;
        }).join('');
    }

    function renderRevealed(s) {
        $('#revealed-score').innerHTML = scoreHtml(s);
        renderSubmissions($('#revealed-submissions'), s, true);

        const killedByMe = new Set();
        (s.submissions || []).forEach(sub => (sub.mutantResults || [])
            .forEach(mr => { if (mr.killed) killedByMe.add(mr.mutantId); }));

        $('#mutant-legend').innerHTML = (s.mutantInfos || []).map(m => {
            const mine = killedByMe.has(m.id);
            return `
                <div class="mutant-legend-item ${mine ? 'killed' : 'survived'}">
                    <div class="mutant-legend-head">
                        <strong>${esc(m.id)}: ${esc(m.shortName)}</strong>
                        <span class="mutant-chip ${mine ? 'killed' : 'survived'}">
                            ${esc(t(mine ? 'reveal.mutants.killedByYou' : 'reveal.mutants.survived'))}</span>
                    </div>
                    <p>${esc(m.description)}</p>
                    ${m.code ? `<pre class="code">${esc(m.code)}</pre>` : ''}
                </div>`;
        }).join('');
        $('#reference-code').textContent = s.referenceCode || '';
    }

    /* ── Timer ─────────────────────────────────────── */

    function startTimer(s) {
        clearInterval(timerInterval);
        const el = $('#timer-display');
        if (s.roundStatus !== 'OPEN' || s.remainingMs == null) { hide(el); return; }
        const deadline = Date.now() + s.remainingMs;   // server-relative, immune to phone clock skew
        show(el);
        const tick = () => {
            const left = deadline - Date.now();
            el.textContent = '⏱ ' + fmtClock(left);
            el.classList.toggle('badge-warn', left < 30000);
            if (left <= 0) clearInterval(timerInterval);
        };
        tick();
        timerInterval = setInterval(tick, 500);
    }

    /* ── Live updates (SSE) ────────────────────────── */

    function connectStream() {
        stream = liveStream('/sse/student', {
            'round-state': () => refreshStatus(),
            'settings-changed': () => refreshStatus(),
            'session-changed': () => refreshStatus(),
            'participants': d => {
                if (currentScreen === 'waiting') $('#waiting-count').textContent = participantsText(d.count);
            },
            'kicked': () => { stopStream(); showScreen('kicked'); }
        }, refreshStatus, ok => (ok ? hide : show)($('#conn-banner')));
    }

    function stopStream() {
        if (stream) { stream.close(); stream = null; }
        hide($('#conn-banner'));
    }

    // Safety net for networks that silently drop the event stream: re-sync now and then.
    setInterval(() => { if (stream && currentScreen !== 'join') refreshStatus(); }, 30000);
    document.addEventListener('visibilitychange', () => {
        if (document.visibilityState === 'visible' && stream) refreshStatus();
    });

    /* ── Init: already joined (cookie)? ────────────── */

    (async () => {
        try {
            const s = await api('/api/status');
            if (s._status === 200) { connectStream(); status = s; render(s); }
            else if (s._status === 403) showScreen('kicked');
            else showScreen('join');
        } catch (_) {
            showScreen('join');
        }
    })();
})();
