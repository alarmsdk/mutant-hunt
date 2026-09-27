/* ═══════════════════════════════════════════════════════
   Student Page – /
   ═══════════════════════════════════════════════════════ */
(function () {
    'use strict';

    const $ = (s, p) => (p || document).querySelector(s);
    const show = el => el.style.display = '';
    const hide = el => el.style.display = 'none';

    const screens = {
        join: $('#join-screen'),
        waiting: $('#waiting-screen'),
        game: $('#game-screen'),
        closed: $('#closed-screen'),
        revealed: $('#revealed-screen')
    };

    let currentScreen = 'join';
    let sse = null;
    let pollTimer = null;

    function showScreen(name) {
        Object.entries(screens).forEach(([k, el]) => {
            if (k === name) show(el); else hide(el);
        });
        currentScreen = name;
    }

    /* ── API helpers ────────────────────────────────── */

    async function api(url, opts = {}) {
        const res = await fetch(url, {
            headers: { 'Content-Type': 'application/json' },
            credentials: 'same-origin',
            ...opts
        });
        return res.json();
    }

    /* ── Join ───────────────────────────────────────── */

    $('#join-form').addEventListener('submit', async e => {
        e.preventDefault();
        hide($('#join-error'));
        const btn = $('#join-btn');
        btn.disabled = true;

        try {
            const data = await api('/api/join', {
                method: 'POST',
                body: JSON.stringify({
                    fullName: $('#join-name').value.trim(),
                    studentNumber: $('#join-sid').value.trim(),
                    nickname: $('#join-nick')?.value?.trim() || ''
                })
            });

            if (data.success) {
                enterGame();
            } else {
                showError($('#join-error'), data.error || 'Failed to join');
            }
        } catch (err) {
            showError($('#join-error'), 'Network error. Please try again.');
        } finally {
            btn.disabled = false;
        }
    });

    function showError(el, msg) {
        el.textContent = msg;
        show(el);
    }

    /* ── Enter game (after join or on reload with cookie) */

    async function enterGame() {
        showScreen('waiting');
        connectSSE();
        await refreshStatus();
    }

    /* ── Status polling & rendering ────────────────── */

    async function refreshStatus() {
        try {
            const status = await api('/api/status');
            if (status.error === 'not_joined') {
                showScreen('join');
                return;
            }
            renderStatus(status);
        } catch (e) {
            // will retry via SSE reconnect
        }
    }

    function renderStatus(s) {
        if (s.roundStatus === 'WAITING' || !s.roundStatus) {
            showScreen('waiting');
            $('#waiting-count').textContent = s.participantCount + ' participants connected';
            return;
        }

        if (s.roundStatus === 'OPEN') {
            showScreen('game');
            renderGameScreen(s);
            return;
        }

        if (s.roundStatus === 'CLOSED') {
            showScreen('closed');
            renderClosedScreen(s);
            return;
        }

        if (s.roundStatus === 'REVEALED') {
            showScreen('revealed');
            renderRevealedScreen(s);
            return;
        }
    }

    function renderGameScreen(s) {
        $('#score-display').textContent = s.killed + ' / ' + s.totalMutants + ' mutants killed';
        $('#tests-remaining').textContent = s.testsRemaining < 0
            ? 'Unlimited tests'
            : s.testsRemaining + ' tests left';

        if (s.specification) {
            $('#spec-content').innerHTML = s.specification;
        }

        renderInputFields(s.functionInputFields, s.expectedField);
        renderSubmissions(s.submissions, false);

        if (s.testsRemaining === 0) {
            $('#submit-btn').disabled = true;
            $('#submit-btn').textContent = 'No tests remaining';
        } else {
            $('#submit-btn').disabled = false;
            $('#submit-btn').textContent = 'Submit Test';
        }
    }

    let fieldsRendered = false;
    function renderInputFields(fields, expectedField) {
        if (fieldsRendered) return;
        fieldsRendered = true;
        const container = $('#input-fields');
        container.innerHTML = '';

        if (!fields) return;
        const allFields = [...fields, expectedField];
        allFields.forEach(f => {
            const div = document.createElement('div');
            div.className = 'field';
            const labelKey = f.label || f.id;
            const placeholderKey = f.placeholder || '';
            div.innerHTML = `
                <label for="field-${f.id}">${labelKey}</label>
                <input id="field-${f.id}" name="${f.id}" type="text"
                       inputmode="${f.inputMode || 'text'}"
                       placeholder="${placeholderKey}" autocomplete="off"/>
            `;
            container.appendChild(div);
        });
    }

    function renderSubmissions(subs, showMutants) {
        if (!subs) return;
        const container = currentScreen === 'game' ? $('#submissions')
            : currentScreen === 'closed' ? $('#closed-submissions')
            : $('#revealed-submissions');
        container.innerHTML = '';

        subs.slice().reverse().forEach((sub, idx) => {
            const card = document.createElement('div');
            card.className = 'sub-card ' + (sub.validity === 'VALID' ? 'valid' : 'invalid');
            let html = `
                <div class="sub-header">
                    <span class="sub-inputs">list=[${sub.list}] target=${sub.target} expected=${sub.expected}</span>
                    <span class="sub-validity ${sub.validity === 'VALID' ? 'valid-text' : 'invalid-text'}">${sub.validity}</span>
                </div>`;

            if (showMutants && sub.mutantResults) {
                html += '<div class="sub-mutants">';
                sub.mutantResults.forEach(mr => {
                    html += `<span class="mutant-chip ${mr.killed ? 'killed' : 'survived'}">${mr.mutantId} ${mr.killed ? '✓' : '✗'}</span>`;
                });
                html += '</div>';
            }
            card.innerHTML = html;
            container.appendChild(card);
        });
    }

    function renderClosedScreen(s) {
        $('#closed-score').textContent = s.killed + ' / ' + s.totalMutants;
        renderSubmissions(s.submissions, false);
    }

    function renderRevealedScreen(s) {
        $('#revealed-score').textContent = s.killed + ' / ' + s.totalMutants;

        if (s.mutantInfos) {
            const legend = $('#mutant-legend');
            legend.innerHTML = '<h3>Mutants</h3>';
            s.mutantInfos.forEach(m => {
                legend.innerHTML += `
                    <div class="mutant-legend-item">
                        <strong>${m.id}: ${m.shortName}</strong>
                        <p>${m.description}</p>
                    </div>`;
            });
        }

        renderSubmissions(s.submissions, true);
    }

    /* ── Submit test ───────────────────────────────── */

    $('#test-form').addEventListener('submit', async e => {
        e.preventDefault();
        hide($('#submit-error'));
        const btn = $('#submit-btn');
        btn.disabled = true;

        const body = {};
        const inputs = $('#input-fields').querySelectorAll('input');
        inputs.forEach(inp => { body[inp.name] = inp.value.trim(); });

        try {
            const res = await api('/api/submit', {
                method: 'POST',
                body: JSON.stringify(body)
            });

            if (res.success === false && res.message) {
                showError($('#submit-error'), res.message);
            } else if (res.validity) {
                if (res.message) {
                    showError($('#submit-error'), res.message);
                    $('#submit-error').className = 'alert alert-warn';
                }
                $('#score-display').textContent = res.killed + ' / ' + res.totalMutants + ' mutants killed';
                $('#tests-remaining').textContent = res.testsRemaining < 0
                    ? 'Unlimited tests' : res.testsRemaining + ' tests left';

                if (res.testsRemaining === 0) {
                    btn.disabled = true;
                    btn.textContent = 'No tests remaining';
                }

                inputs.forEach(inp => inp.value = '');
                await refreshStatus();
            }
        } catch (err) {
            showError($('#submit-error'), 'Network error');
        } finally {
            if ($('#submit-btn').textContent !== 'No tests remaining') {
                btn.disabled = false;
            }
        }
    });

    /* ── SSE ───────────────────────────────────────── */

    function connectSSE() {
        if (sse) { sse.close(); }
        sse = new EventSource('/sse/student');

        sse.addEventListener('round-state', () => {
            fieldsRendered = false;
            refreshStatus();
        });
        sse.addEventListener('score-update', () => refreshStatus());
        sse.addEventListener('heartbeat', () => {});

        sse.onerror = () => {
            setTimeout(() => {
                if (currentScreen !== 'join') connectSSE();
            }, 3000);
        };
    }

    /* ── Init: check if already joined ──────────────── */

    (async () => {
        try {
            const status = await api('/api/status');
            if (status.error === 'not_joined') {
                showScreen('join');
            } else {
                enterGame();
            }
        } catch (e) {
            showScreen('join');
        }
    })();

})();
