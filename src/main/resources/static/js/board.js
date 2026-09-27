/* ═══════════════════════════════════════════════════════
   Board Page – /board
   ═══════════════════════════════════════════════════════ */
(function () {
    'use strict';

    const $ = (s) => document.querySelector(s);
    const show = el => el.style.display = '';
    const hide = el => el.style.display = 'none';

    let sse = null;
    let timerInterval = null;
    let currentRound = null;

    /* ── Initial load ──────────────────────────────── */

    async function loadBoardData() {
        try {
            const data = await (await fetch('/admin/api/board-data')).json();
            if (!data.hasSession) {
                hide($('#qr-section'));
                hide($('#round-section'));
                show($('#no-session'));
                return;
            }
            hide($('#no-session'));
            show($('#qr-section'));

            $('#board-participants').textContent = data.participantCount + ' participants';

            if (data.round) {
                currentRound = data.round;
                showRound(data);
            }
        } catch (e) {
            console.error('Failed to load board data', e);
        }
    }

    function showRound(data) {
        show($('#round-section'));
        $('#round-name').textContent = data.round.name;
        $('#board-participant-count').textContent = data.participantCount + ' participants';

        const limit = data.round.testLimit;
        $('#board-test-limit').textContent = 'Limit: ' + (limit && limit > 0 ? limit + ' tests' : '∞');

        if (data.specification) {
            $('#board-spec').innerHTML = data.specification;
        }

        if (data.scoreboard) renderScoreboard(data.scoreboard);
        renderMutantPanel(data);
        startTimer(data.round);
    }

    /* ── Scoreboard ────────────────────────────────── */

    function renderScoreboard(entries) {
        const body = $('#scoreboard-body');
        body.innerHTML = '';
        const top10 = entries.slice(0, 10);
        const rest = entries.length - 10;

        top10.forEach((e, i) => {
            const tr = document.createElement('tr');
            tr.innerHTML = `
                <td>${i + 1}</td>
                <td>${escHtml(e.displayName)}</td>
                <td>${e.score} / ${e.totalMutants}</td>
                <td>${e.validTestCount}</td>`;
            body.appendChild(tr);
        });

        const moreEl = $('#scoreboard-more');
        if (rest > 0) {
            moreEl.textContent = '+' + rest + ' more';
            show(moreEl);
        } else {
            hide(moreEl);
        }
    }

    /* ── Mutant panel ──────────────────────────────── */

    function renderMutantPanel(data) {
        const panel = $('#mutant-panel');
        const list = $('#mutant-list');

        if (!data.mutantKillCounts) {
            hide(panel);
            return;
        }

        show(panel);
        list.innerHTML = '';

        Object.entries(data.mutantKillCounts).forEach(([mutantId, count]) => {
            const div = document.createElement('div');
            div.className = 'board-mutant-item';

            let label = mutantId;
            if (data.mutantInfos) {
                const info = data.mutantInfos.find(m => m.id === mutantId);
                if (info) label = mutantId + ': ' + info.shortName;
            }

            div.innerHTML = `<span>${escHtml(label)}</span><span>killed by <strong>${count}</strong> students</span>`;
            list.appendChild(div);
        });
    }

    /* ── Timer ─────────────────────────────────────── */

    function startTimer(round) {
        if (timerInterval) clearInterval(timerInterval);
        const timerEl = $('#board-timer');

        if (!round.timeLimitSeconds || round.timeLimitSeconds <= 0 || !round.openedAt) {
            hide(timerEl);
            return;
        }
        if (round.status !== 'OPEN') {
            hide(timerEl);
            return;
        }

        show(timerEl);
        const deadline = round.openedAt + round.timeLimitSeconds * 1000;

        function tick() {
            const remaining = Math.max(0, Math.floor((deadline - Date.now()) / 1000));
            const min = Math.floor(remaining / 60);
            const sec = remaining % 60;
            timerEl.textContent = min + ':' + String(sec).padStart(2, '0');

            if (remaining <= 0) {
                timerEl.textContent = 'Time is up!';
                timerEl.classList.add('expired');
                clearInterval(timerInterval);
            } else {
                timerEl.classList.remove('expired');
            }
        }

        tick();
        timerInterval = setInterval(tick, 1000);
    }

    /* ── SSE ───────────────────────────────────────── */

    function connectSSE() {
        if (sse) sse.close();
        sse = new EventSource('/sse/board');

        sse.addEventListener('scoreboard', e => {
            const data = JSON.parse(e.data);
            if (data.entries) renderScoreboard(data.entries);
        });

        sse.addEventListener('participants', e => {
            const data = JSON.parse(e.data);
            $('#board-participants').textContent = data.count + ' participants';
            $('#board-participant-count').textContent = data.count + ' participants';
        });

        sse.addEventListener('round-state', e => {
            const data = JSON.parse(e.data);
            currentRound = data;
            if (data.status === 'OPEN' || data.status === 'CLOSED' || data.status === 'REVEALED') {
                loadBoardData();
            }
        });

        sse.addEventListener('board-command', e => {
            const data = JSON.parse(e.data);
            handleBoardCommand(data.command);
        });

        sse.addEventListener('settings-changed', () => {
            loadBoardData();
        });

        sse.addEventListener('heartbeat', () => {});

        sse.onerror = () => {
            setTimeout(connectSSE, 3000);
        };
    }

    function handleBoardCommand(cmd) {
        const body = document.body;
        body.classList.remove('fullscreen');

        if (cmd === 'SHOW_QR') {
            show($('#qr-section'));
            hide($('#round-section'));
        } else if (cmd === 'SHOW_SCOREBOARD') {
            show($('#qr-section'));
            show($('#round-section'));
        } else if (cmd === 'FULLSCREEN_SCORE') {
            body.classList.add('fullscreen');
            hide($('#qr-section'));
            show($('#round-section'));
        }
    }

    /* ── Helpers ────────────────────────────────────── */

    function escHtml(s) {
        const div = document.createElement('div');
        div.textContent = s || '';
        return div.innerHTML;
    }

    /* ── Init ──────────────────────────────────────── */

    loadBoardData();
    connectSSE();

})();
