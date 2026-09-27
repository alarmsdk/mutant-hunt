/* ═══════════════════════════════════════════════════════
   Board Page – /board   (projector; public, no login)
   Keyboard: Q = large QR, S = normal view, F = fullscreen scoreboard
   ═══════════════════════════════════════════════════════ */
(function () {
    'use strict';

    const $ = s => document.querySelector(s);
    const show = el => { if (el) el.style.display = ''; };
    const hide = el => { if (el) el.style.display = 'none'; };

    let timerInterval = null;
    let totalMutants = 0;
    let mode = 'SHOW_SCOREBOARD';
    let loading = false, loadAgain = false;
    let roundStatus = null;

    /* ── Full reload of the board state ─────────────── */

    async function loadBoardData() {
        if (loading) { loadAgain = true; return; }
        loading = true;
        try {
            const data = await (await fetch('/api/board', { cache: 'no-store' })).json();
            render(data);
        } catch (e) {
            console.error('Failed to load board data', e);
        } finally {
            loading = false;
            if (loadAgain) { loadAgain = false; loadBoardData(); }
        }
    }

    function render(data) {
        hide($('#no-session')); hide($('#no-round')); hide($('#round-content'));
        if (data.joinUrl) $('#join-url').textContent = data.joinUrl;
        document.body.dataset.panel = data.mutantPanelMode || '';
        roundStatus = data.round ? data.round.status : null;

        if (!data.hasSession) {
            show($('#no-session'));
            setParticipants(0);
            stopTimer();
            return;
        }
        setParticipants(data.participantCount);

        if (!data.round) {
            $('#session-name').textContent = data.sessionName || '';
            show($('#no-round'));
            stopTimer();
            document.body.classList.add('no-round');
            return;
        }
        document.body.classList.remove('no-round');
        show($('#round-content'));

        const r = data.round;
        totalMutants = data.totalMutants || 0;
        $('#round-name').textContent = r.name;
        const st = $('#round-status');
        st.textContent = t('board.status.' + r.status);
        st.className = 'board-status status-' + r.status;
        $('#board-test-limit').textContent = r.testLimit > 0
            ? t('board.testLimit', r.testLimit) : t('board.testLimit.unlimited');
        $('#board-spec').innerHTML = data.specification || '';   // trusted, from the Problem class

        renderScoreboard(data.scoreboard || []);
        renderMutantPanel(data.mutantPanel, r.status);
        startTimer(r);
    }

    function setParticipants(n) {
        const txt = n === 1 ? t('board.participants.one') : t('board.participants', n);
        $('#board-participants').textContent = txt;
        $('#board-participant-count').textContent = txt;
    }

    /* ── Scoreboard ────────────────────────────────── */

    function renderScoreboard(entries) {
        const body = $('#scoreboard-body');
        const top = entries.slice(0, 10);
        body.innerHTML = top.map((e, i) => {
            const pct = e.totalMutants > 0 ? Math.round(e.score / e.totalMutants * 100) : 0;
            return `
                <tr class="${e.score === e.totalMutants && e.totalMutants > 0 ? 'perfect' : ''}">
                    <td class="rank">${i + 1}</td>
                    <td class="name">${esc(e.displayName)}</td>
                    <td class="score">
                        <span class="score-num">${e.score}<small>/${e.totalMutants}</small></span>
                        <span class="score-bar"><span style="width:${pct}%"></span></span>
                    </td>
                    <td class="tests">${e.testsUsed}</td>
                </tr>`;
        }).join('');
        (entries.length === 0 ? show : hide)($('#scoreboard-empty'));

        const rest = entries.length - top.length;
        const moreEl = $('#scoreboard-more');
        if (rest > 0) { moreEl.textContent = t('board.more', rest); show(moreEl); } else hide(moreEl);
    }

    /* ── Mutant panel ──────────────────────────────── */

    function renderMutantPanel(rows, roundStatus) {
        const panel = $('#mutant-panel');
        // after reveal the discussion is about the mutants: give them the spec's space
        document.body.classList.toggle('show-mutants', !!rows && roundStatus === 'REVEALED');
        if (!rows) { hide(panel); return; }
        show(panel);
        const anyDetails = rows.some(m => m.shortName);
        (!anyDetails && document.body.dataset.panel === 'REVEALED' && roundStatus !== 'REVEALED' ? show : hide)($('#mutant-note'));
        $('#mutant-list').innerHTML = rows.map(m => `
            <div class="board-mutant-item ${m.killCount > 0 ? 'hit' : ''}">
                <div class="mutant-head">
                    <strong>${esc(m.id)}${m.shortName ? ': ' + esc(m.shortName) : ''}</strong>
                    <span class="kill-count">${esc(m.killCount === 1 ? t('board.mutants.killedBy.one') : t('board.mutants.killedBy', m.killCount))}</span>
                </div>
                ${m.description ? `<p class="mutant-desc">${esc(m.description)}</p>` : ''}
            </div>`).join('');
    }

    /* ── Timer (server-relative) ───────────────────── */

    function stopTimer() {
        clearInterval(timerInterval);
        hide($('#board-timer'));
    }

    function startTimer(round) {
        stopTimer();
        const el = $('#board-timer');
        if (round.status !== 'OPEN' || round.remainingMs == null) return;
        const deadline = Date.now() + round.remainingMs;
        show(el);
        const tick = () => {
            const left = deadline - Date.now();
            if (left <= 0) {
                el.textContent = t('board.timer.expired');
                el.classList.add('expired');
                clearInterval(timerInterval);
            } else {
                el.textContent = '⏱ ' + fmtClock(left);
                el.classList.remove('expired');
                el.classList.toggle('urgent', left < 30000);
            }
        };
        tick();
        timerInterval = setInterval(tick, 250);
    }

    /* ── Layout commands (from admin or keyboard) ──── */

    function applyMode(cmd) {
        mode = cmd;
        document.body.classList.toggle('mode-qr', cmd === 'SHOW_QR');
        document.body.classList.toggle('mode-fullscreen', cmd === 'FULLSCREEN_SCORE');
    }

    document.addEventListener('keydown', e => {
        if (e.ctrlKey || e.metaKey || e.altKey) return;
        const k = e.key.toLowerCase();
        if (k === 'q') applyMode(mode === 'SHOW_QR' ? 'SHOW_SCOREBOARD' : 'SHOW_QR');
        if (k === 's') applyMode('SHOW_SCOREBOARD');
        if (k === 'f') applyMode(mode === 'FULLSCREEN_SCORE' ? 'SHOW_SCOREBOARD' : 'FULLSCREEN_SCORE');
    });

    /* ── SSE ───────────────────────────────────────── */

    liveStream('/sse/board', {
        'scoreboard': d => {
            if (d.entries) renderScoreboard(d.entries);
            if (d.participantCount != null) setParticipants(d.participantCount);
            if (roundStatus) renderMutantPanel(d.mutantPanel, roundStatus);
        },
        'participants': d => setParticipants(d.count),
        'round-state': () => loadBoardData(),
        'settings-changed': () => loadBoardData(),
        'session-changed': () => loadBoardData(),
        'board-command': d => applyMode(d.command)
    }, loadBoardData, ok => (ok ? hide : show)($('#conn-banner')));

    // periodic safety re-sync (cheap: one board)
    setInterval(loadBoardData, 60000);
})();
