/* ═══════════════════════════════════════════════════════
   Admin Panel – /admin
   ═══════════════════════════════════════════════════════ */
(function () {
    'use strict';

    const $ = s => document.querySelector(s);
    const $$ = s => document.querySelectorAll(s);
    const show = el => { if (el) el.style.display = ''; };
    const hide = el => { if (el) el.style.display = 'none'; };

    const csrf = {
        token: $('meta[name="_csrf"]')?.content,
        header: $('meta[name="_csrf_header"]')?.content
    };

    let activeSessionId = null;
    let sessions = [];
    let rounds = [];
    let sse = null;

    /* ── API ───────────────────────────────────────── */

    async function api(url, method = 'GET', body = null) {
        const opts = { method, headers: { 'Content-Type': 'application/json' }, credentials: 'same-origin' };
        if (csrf.token && csrf.header) opts.headers[csrf.header] = csrf.token;
        if (body) opts.body = JSON.stringify(body);
        const res = await fetch(url, opts);
        if (res.status === 204) return null;
        return res.json();
    }

    /* ── Tabs ──────────────────────────────────────── */

    $$('.tab-btn').forEach(btn => {
        btn.addEventListener('click', () => {
            $$('.tab-btn').forEach(b => b.classList.remove('active'));
            btn.classList.add('active');
            $$('.tab-panel').forEach(p => hide(p));
            show($('#tab-' + btn.dataset.tab));
            onTabSwitch(btn.dataset.tab);
        });
    });

    function onTabSwitch(tab) {
        if (tab === 'sessions') loadSessions();
        if (tab === 'rounds') loadRounds();
        if (tab === 'settings') loadSettings();
        if (tab === 'participants') loadParticipants();
        if (tab === 'submissions') loadSubmissions();
        if (tab === 'analytics') loadAnalyticsRounds();
    }

    /* ── Sessions ──────────────────────────────────── */

    async function loadSessions() {
        sessions = await api('/admin/api/sessions');
        const body = $('#sessions-body');
        body.innerHTML = '';
        sessions.forEach(s => {
            const tr = document.createElement('tr');
            tr.innerHTML = `
                <td>${esc(s.name)}</td>
                <td>${s.active ? '<span class="badge-active">ACTIVE</span>' : ''}</td>
                <td>${new Date(s.createdAt).toLocaleString()}</td>
                <td>
                    ${!s.active ? `<button class="btn btn-sm btn-outline" onclick="activateSession(${s.id})">Activate</button>` : ''}
                    <button class="btn btn-sm btn-outline" onclick="archiveSession(${s.id})">Archive</button>
                    <button class="btn btn-sm btn-danger" onclick="deleteSession(${s.id})">Delete</button>
                </td>`;
            body.appendChild(tr);
        });

        const active = sessions.find(s => s.active);
        if (active) activeSessionId = active.id;
    }

    window.activateSession = async id => {
        await api(`/admin/api/sessions/${id}/activate`, 'POST');
        activeSessionId = id;
        loadSessions();
    };
    window.archiveSession = async id => {
        await api(`/admin/api/sessions/${id}/archive`, 'POST');
        loadSessions();
    };
    window.deleteSession = async id => {
        if (!confirm('Delete this session and ALL its data?')) return;
        await api(`/admin/api/sessions/${id}`, 'DELETE');
        if (activeSessionId === id) activeSessionId = null;
        loadSessions();
    };

    $('#create-session-btn').addEventListener('click', async () => {
        const name = $('#new-session-name').value.trim() || 'New Session';
        await api('/admin/api/sessions', 'POST', { name });
        $('#new-session-name').value = '';
        loadSessions();
    });

    /* ── Rounds ────────────────────────────────────── */

    async function loadRounds() {
        if (!activeSessionId) { $('#rounds-body').innerHTML = '<tr><td colspan="6">No active session</td></tr>'; return; }
        const s = sessions.find(s => s.id === activeSessionId);
        $('#rounds-session-name').textContent = s ? '– ' + s.name : '';
        rounds = await api(`/admin/api/sessions/${activeSessionId}/rounds`);
        renderRounds();
    }

    function renderRounds() {
        const body = $('#rounds-body');
        body.innerHTML = '';
        rounds.forEach(r => {
            const tr = document.createElement('tr');
            const actions = buildRoundActions(r);
            tr.innerHTML = `
                <td>${esc(r.name)}</td>
                <td>${r.problemId}</td>
                <td>${r.testLimit || '∞'}</td>
                <td>${r.timeLimitSeconds ? r.timeLimitSeconds + 's' : '—'}</td>
                <td><span class="badge-status badge-${r.status}">${r.status}</span></td>
                <td>${actions}</td>`;
            body.appendChild(tr);
        });

        // populate round filter dropdowns
        populateRoundSelects();
    }

    function buildRoundActions(r) {
        let html = '';
        if (r.status === 'DRAFT') {
            html += `<button class="btn btn-sm btn-primary" onclick="openRound(${r.id})">Open</button> `;
        }
        if (r.status === 'OPEN') {
            html += `<button class="btn btn-sm btn-outline" onclick="closeRound(${r.id})">Close</button> `;
        }
        if (r.status === 'CLOSED') {
            html += `<button class="btn btn-sm btn-outline" onclick="revealRound(${r.id})">Reveal</button> `;
        }
        return html;
    }

    window.openRound = async id => { await api(`/admin/api/rounds/${id}/open`, 'POST'); loadRounds(); };
    window.closeRound = async id => { await api(`/admin/api/rounds/${id}/close`, 'POST'); loadRounds(); };
    window.revealRound = async id => { await api(`/admin/api/rounds/${id}/reveal`, 'POST'); loadRounds(); };

    $('#create-round-btn').addEventListener('click', async () => {
        if (!activeSessionId) return alert('No active session');
        const name = $('#new-round-name').value.trim() || 'Round';
        const testLimit = $('#new-round-limit').value || '0';
        const timeLimitSeconds = $('#new-round-time').value || '0';
        await api(`/admin/api/sessions/${activeSessionId}/rounds`, 'POST', {
            name, problemId: 'indexOf', testLimit, timeLimitSeconds
        });
        $('#new-round-name').value = '';
        $('#new-round-limit').value = '';
        $('#new-round-time').value = '';
        loadRounds();
    });

    /* ── Settings ──────────────────────────────────── */

    async function loadSettings() {
        if (!activeSessionId) return;
        const s = await api(`/admin/api/sessions/${activeSessionId}/settings`);
        $('#set-anonymity').value = s.anonymityMode;
        $('#set-joinOpen').value = String(s.joinOpen);
        $('#set-studentIdLength').value = s.studentIdLength;
        $('#set-mutantPanel').value = s.mutantPanelMode;
        $('#set-rateLimit').value = s.submissionRateLimitMs;
    }

    $('#settings-form').addEventListener('submit', async e => {
        e.preventDefault();
        if (!activeSessionId) return;
        await api(`/admin/api/sessions/${activeSessionId}/settings`, 'POST', {
            anonymityMode: $('#set-anonymity').value,
            joinOpen: $('#set-joinOpen').value,
            studentIdLength: $('#set-studentIdLength').value,
            mutantPanelMode: $('#set-mutantPanel').value,
            submissionRateLimitMs: $('#set-rateLimit').value
        });
        const msg = $('#settings-msg');
        show(msg);
        setTimeout(() => hide(msg), 2000);
    });

    /* ── Participants ──────────────────────────────── */

    async function loadParticipants() {
        if (!activeSessionId) return;
        const participants = await api(`/admin/api/sessions/${activeSessionId}/participants`);
        $('#participant-count').textContent = participants.length;
        const body = $('#participants-body');
        body.innerHTML = '';
        participants.forEach(p => {
            const tr = document.createElement('tr');
            tr.innerHTML = `
                <td>${esc(p.fullName)}</td>
                <td>${esc(p.studentNumber)}</td>
                <td>${esc(p.displayName)}</td>
                <td>${new Date(p.joinedAt).toLocaleTimeString()}</td>
                <td>
                    <button class="btn btn-sm btn-outline" onclick="editNick(${p.id},'${esc(p.nickname || '')}')">Nickname</button>
                    <button class="btn btn-sm btn-danger" onclick="kickParticipant(${p.id})">Remove</button>
                </td>`;
            body.appendChild(tr);
        });
    }

    window.kickParticipant = async id => {
        if (!confirm('Remove this participant?')) return;
        await api(`/admin/api/participants/${id}/kick`, 'POST');
        loadParticipants();
    };

    window.editNick = async (id, current) => {
        const nick = prompt('Enter new nickname:', current);
        if (nick === null) return;
        await api(`/admin/api/participants/${id}/nickname`, 'POST', { nickname: nick });
        loadParticipants();
    };

    /* ── Submissions ───────────────────────────────── */

    async function loadSubmissions() {
        const roundId = $('#sub-filter-round').value;
        if (!roundId) return;
        const validityFilter = $('#sub-filter-validity').value;

        let subs = await api(`/admin/api/rounds/${roundId}/submissions`);
        if (validityFilter) subs = subs.filter(s => s.validity === validityFilter);

        const body = $('#submissions-body');
        body.innerHTML = '';
        subs.forEach(s => {
            const tr = document.createElement('tr');
            tr.innerHTML = `
                <td>${new Date(s.time).toLocaleTimeString()}</td>
                <td>${esc(s.participantName)}</td>
                <td>${esc(s.studentNumber)}</td>
                <td>${esc(s.list)}</td>
                <td>${s.target}</td>
                <td>${s.expected}</td>
                <td>${s.referenceResult}</td>
                <td><span class="badge-${s.validity}">${s.validity}</span></td>
                <td>${s.killedMutants ? s.killedMutants.join(', ') : ''}</td>`;
            body.appendChild(tr);
        });
    }

    function populateRoundSelects() {
        [
            $('#sub-filter-round'),
            $('#analytics-round')
        ].forEach(sel => {
            const current = sel.value;
            sel.innerHTML = '<option value="">Select round</option>';
            rounds.forEach(r => {
                sel.innerHTML += `<option value="${r.id}">${esc(r.name)} (${r.status})</option>`;
            });
            if (current) sel.value = current;
        });
    }

    $('#sub-filter-round').addEventListener('change', loadSubmissions);
    $('#sub-filter-validity').addEventListener('change', loadSubmissions);

    /* ── Analytics ─────────────────────────────────── */

    async function loadAnalyticsRounds() {
        if (!activeSessionId) return;
        if (rounds.length === 0) {
            rounds = await api(`/admin/api/sessions/${activeSessionId}/rounds`);
        }
        populateRoundSelects();
    }

    $('#analytics-round').addEventListener('change', async () => {
        const roundId = $('#analytics-round').value;
        if (!roundId) { hide($('#analytics-content')); return; }

        const data = await api(`/admin/api/rounds/${roundId}/analytics`);
        show($('#analytics-content'));
        $('#an-total').textContent = data.totalSubmissions;
        $('#an-invalid').textContent = data.invalidSubmissions;
        $('#an-avg').textContent = data.averageScore.toFixed(1);
        $('#an-participants').textContent = data.participantCount;

        const body = $('#analytics-mutants');
        body.innerHTML = '';
        Object.entries(data.mutantKillCounts).forEach(([mid, count]) => {
            const pct = data.participantCount > 0
                ? Math.round(count / data.participantCount * 100) : 0;
            const tr = document.createElement('tr');
            tr.innerHTML = `
                <td><strong>${mid}</strong></td>
                <td>${esc(data.mutantNames[mid] || '')}</td>
                <td>${count}</td>
                <td>${pct}%</td>
                <td><div class="kill-bar" style="width:${pct}%">&nbsp;</div></td>`;
            body.appendChild(tr);
        });
    });

    /* ── Board control ─────────────────────────────── */

    window.sendBoardCmd = async cmd => {
        await api('/admin/api/board-command', 'POST', { command: cmd });
    };

    /* ── Export ─────────────────────────────────────── */

    $('#export-participants-btn')?.addEventListener('click', () => {
        if (!activeSessionId) return alert('No active session');
        window.location.href = `/admin/api/sessions/${activeSessionId}/export/participants`;
    });

    $('#export-submissions-btn')?.addEventListener('click', () => {
        const roundId = $('#sub-filter-round').value || (rounds.length > 0 ? rounds[0].id : '');
        if (!roundId) return alert('Select a round first');
        window.location.href = `/admin/api/rounds/${roundId}/export/submissions`;
    });

    /* ── SSE ───────────────────────────────────────── */

    function connectSSE() {
        if (sse) sse.close();
        sse = new EventSource('/sse/admin');

        sse.addEventListener('submission', e => {
            // live submission feed - refresh if on submissions tab
            if ($('#tab-submissions').style.display !== 'none') loadSubmissions();
        });

        sse.addEventListener('participants', () => {
            if ($('#tab-participants').style.display !== 'none') loadParticipants();
        });

        sse.addEventListener('round-state', () => {
            loadRounds();
        });

        sse.addEventListener('heartbeat', () => {});
        sse.onerror = () => setTimeout(connectSSE, 3000);
    }

    /* ── Helpers ────────────────────────────────────── */

    function esc(s) {
        if (!s) return '';
        const div = document.createElement('div');
        div.textContent = s;
        return div.innerHTML;
    }

    /* ── Init ──────────────────────────────────────── */

    loadSessions();
    connectSSE();
    // after sessions load, also load rounds
    setTimeout(() => { if (activeSessionId) loadRounds(); }, 500);

})();
