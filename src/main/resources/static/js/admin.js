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

    let sessions = [];
    let archived = [];
    let rounds = [];
    let participants = [];
    let selectedSessionId = null;
    let currentTab = 'sessions';
    let roundTimer = null;

    /* ── API ───────────────────────────────────────── */

    async function api(url, method = 'GET', body = null) {
        const opts = { method, headers: { 'Content-Type': 'application/json' }, credentials: 'same-origin', cache: 'no-store' };
        if (csrf.token && csrf.header) opts.headers[csrf.header] = csrf.token;
        if (body) opts.body = JSON.stringify(body);
        const res = await fetch(url, opts);
        if (res.status === 401 || res.status === 403 || res.redirected) {
            alert(t('admin.sessionExpired'));
            window.location.href = '/admin/login';
            throw new Error('unauthorized');
        }
        let data = null;
        try { data = await res.json(); } catch (_) { /* empty */ }
        if (!res.ok) {
            toast(tErr(data && data.error), true);
            throw new Error((data && data.error) || res.status);
        }
        return data;
    }

    let toastTimer = null;
    function toast(msg, isError) {
        const el = $('#toast');
        el.textContent = msg;
        el.className = 'toast' + (isError ? ' toast-error' : '');
        show(el);
        clearTimeout(toastTimer);
        toastTimer = setTimeout(() => hide(el), isError ? 5000 : 2500);
    }

    /** Collapse bursts of events (e.g. 80 submissions) into one reload. */
    function throttle(fn, ms) {
        let timer = null, pending = false;
        return function () {
            if (timer) { pending = true; return; }
            fn();
            timer = setTimeout(() => { timer = null; if (pending) { pending = false; fn(); } }, ms);
        };
    }

    const fmtTime = ms => new Date(ms).toLocaleTimeString();
    const fmtDate = ms => new Date(ms).toLocaleString();
    const selected = () => sessions.concat(archived).find(s => s.id === selectedSessionId);

    /* ── Tabs ──────────────────────────────────────── */

    $$('.tab-btn').forEach(btn => btn.addEventListener('click', () => switchTab(btn.dataset.tab)));

    function switchTab(tab) {
        currentTab = tab;
        $$('.tab-btn').forEach(b => b.classList.toggle('active', b.dataset.tab === tab));
        $$('.tab-panel').forEach(p => hide(p));
        show($('#tab-' + tab));
        try { history.replaceState(null, '', '#' + tab); } catch (_) { /* ignore */ }
        refreshTab();
    }

    function refreshTab() {
        const tab = currentTab;
        if (tab === 'sessions') loadSessions();
        if (tab === 'rounds') loadRounds();
        if (tab === 'settings') loadSettings();
        if (tab === 'participants') loadParticipants();
        if (tab === 'submissions') loadSubmissionFilters().then(loadSubmissions);
        if (tab === 'analytics') loadRounds().then(loadAnalytics);
        if (tab === 'board') loadRounds().then(updateExportLinks);
    }

    /* ── Session selection ─────────────────────────── */

    function selectSession(id) {
        selectedSessionId = id;
        try { localStorage.setItem('mh.selectedSession', String(id)); } catch (_) { /* ignore */ }
        rounds = [];
        renderSelectedLabel();
    }

    function renderSelectedLabel() {
        const s = selected();
        const el = $('#selected-session');
        if (!s) { el.textContent = t('admin.noSession'); return; }
        el.innerHTML = esc(t('admin.selectedSession', s.name)) +
            (s.active ? ` <span class="badge-active">${esc(t('admin.live'))}</span>`
                      : ` <span class="muted">${esc(t('admin.selectedSession.notLive'))}</span>`);
    }

    function requireSession() {
        if (!selectedSessionId) { toast(t('admin.noSession'), true); return false; }
        return true;
    }

    /* ── Sessions ──────────────────────────────────── */

    async function loadSessions() {
        [sessions, archived] = await Promise.all([
            api('/admin/api/sessions'), api('/admin/api/sessions/archived')]);

        const all = sessions.concat(archived);
        if (!all.some(s => s.id === selectedSessionId)) {
            let stored = null;
            try { stored = parseInt(localStorage.getItem('mh.selectedSession'), 10); } catch (_) { /* ignore */ }
            const pick = all.find(s => s.id === stored) || sessions.find(s => s.active) || sessions[0];
            selectedSessionId = pick ? pick.id : null;
        }
        renderSelectedLabel();

        $('#sessions-body').innerHTML = sessions.length === 0
            ? `<tr><td colspan="4" class="muted">${esc(t('admin.sessions.empty'))}</td></tr>`
            : sessions.map(s => `
                <tr class="${s.id === selectedSessionId ? 'row-selected' : ''}">
                    <td><strong>${esc(s.name)}</strong></td>
                    <td>${s.active ? `<span class="badge-active">${esc(t('admin.sessions.live'))}</span>` : ''}</td>
                    <td>${fmtDate(s.createdAt)}</td>
                    <td class="actions-cell">
                        ${s.id === selectedSessionId
                            ? `<span class="badge">${esc(t('admin.sessions.managing'))}</span>`
                            : `<button class="btn btn-sm btn-outline" data-act="manage" data-id="${s.id}">${esc(t('admin.sessions.manage'))}</button>`}
                        ${!s.active ? `<button class="btn btn-sm btn-primary" data-act="activate" data-id="${s.id}">${esc(t('admin.sessions.activate'))}</button>` : ''}
                        <button class="btn btn-sm btn-outline" data-act="archive" data-id="${s.id}">${esc(t('admin.sessions.archive'))}</button>
                        <button class="btn btn-sm btn-danger" data-act="delete" data-id="${s.id}">${esc(t('admin.sessions.delete'))}</button>
                    </td>
                </tr>`).join('');

        $('#archived-body').innerHTML = archived.length === 0
            ? `<tr><td class="muted">${esc(t('common.none'))}</td></tr>`
            : archived.map(s => `
                <tr class="${s.id === selectedSessionId ? 'row-selected' : ''}">
                    <td>${esc(s.name)}</td>
                    <td>${fmtDate(s.createdAt)}</td>
                    <td class="actions-cell">
                        <button class="btn btn-sm btn-outline" data-act="manage" data-id="${s.id}">${esc(t('admin.sessions.manage'))}</button>
                        <button class="btn btn-sm btn-outline" data-act="unarchive" data-id="${s.id}">${esc(t('admin.sessions.unarchive'))}</button>
                        <button class="btn btn-sm btn-danger" data-act="delete" data-id="${s.id}">${esc(t('admin.sessions.delete'))}</button>
                    </td>
                </tr>`).join('');
    }

    $('#tab-sessions').addEventListener('click', async e => {
        const b = e.target.closest('button[data-act]');
        if (!b) return;
        const id = parseInt(b.dataset.id, 10);
        const s = sessions.concat(archived).find(x => x.id === id);
        const name = s ? s.name : '';
        switch (b.dataset.act) {
            case 'manage':
                selectSession(id);
                break;
            case 'activate':
                if (!confirm(t('admin.sessions.activate.confirm', name))) return;
                await api(`/admin/api/sessions/${id}/activate`, 'POST');
                selectSession(id);
                break;
            case 'archive':
                if (!confirm(t('admin.sessions.archive.confirm', name))) return;
                await api(`/admin/api/sessions/${id}/archive`, 'POST');
                break;
            case 'unarchive':
                await api(`/admin/api/sessions/${id}/unarchive`, 'POST');
                break;
            case 'delete':
                if (!confirm(t('admin.sessions.delete.confirm', name))) return;
                await api(`/admin/api/sessions/${id}`, 'DELETE');
                if (selectedSessionId === id) selectedSessionId = null;
                break;
        }
        loadSessions();
    });

    $('#create-session-form').addEventListener('submit', async e => {
        e.preventDefault();
        const s = await api('/admin/api/sessions', 'POST', { name: $('#new-session-name').value.trim() });
        $('#new-session-name').value = '';
        if (!selectedSessionId) selectSession(s.id);
        loadSessions();
    });

    /* ── Rounds ────────────────────────────────────── */

    async function loadProblems() {
        const problems = await api('/admin/api/problems');
        $('#new-round-problem').innerHTML = problems
            .map(p => `<option value="${esc(p.id)}">${esc(p.title)}</option>`).join('');
    }

    async function loadRounds() {
        if (!selectedSessionId) {
            rounds = [];
            $('#rounds-body').innerHTML = `<tr><td colspan="6" class="muted">${esc(t('admin.noSession'))}</td></tr>`;
            populateRoundSelects();
            return;
        }
        rounds = await api(`/admin/api/sessions/${selectedSessionId}/rounds`);
        renderRounds();
        populateRoundSelects();
    }

    function renderRounds() {
        $('#rounds-body').innerHTML = rounds.length === 0
            ? `<tr><td colspan="6" class="muted">${esc(t('admin.rounds.empty'))}</td></tr>`
            : rounds.map(r => `
                <tr>
                    <td><strong>${esc(r.name)}</strong></td>
                    <td>${esc(r.problemId)}</td>
                    <td>${r.testLimit || t('common.unlimited')}</td>
                    <td>${r.timeLimitSeconds ? fmtClock(r.timeLimitSeconds * 1000) : t('common.none')}
                        ${r.deadline ? `<span class="badge badge-timer" data-deadline="${r.deadline}"></span>` : ''}</td>
                    <td><span class="badge-status badge-${esc(r.status)}">${esc(r.status)}</span></td>
                    <td class="actions-cell">${roundActions(r)}</td>
                </tr>`).join('');
        tickRoundTimers();
        clearInterval(roundTimer);
        roundTimer = setInterval(tickRoundTimers, 1000);
    }

    function tickRoundTimers() {
        $$('[data-deadline]').forEach(el => {
            el.textContent = t('admin.rounds.remaining', fmtClock(parseInt(el.dataset.deadline, 10) - Date.now()));
        });
    }

    function roundActions(r) {
        const b = (act, cls, key) =>
            `<button class="btn btn-sm ${cls}" data-act="${act}" data-id="${r.id}">${esc(t(key))}</button>`;
        let html = '';
        if (r.status === 'DRAFT') html += b('open', 'btn-primary', 'admin.rounds.open');
        if (r.status === 'OPEN') html += b('close', 'btn-warn', 'admin.rounds.close');
        if (r.status === 'CLOSED') html += b('reveal', 'btn-primary', 'admin.rounds.reveal');
        html += b('edit', 'btn-outline', 'admin.rounds.edit');
        if (r.status === 'DRAFT') html += b('delete', 'btn-danger', 'admin.rounds.delete');
        return html;
    }

    $('#tab-rounds').addEventListener('click', async e => {
        const b = e.target.closest('button[data-act]');
        if (!b) return;
        const id = parseInt(b.dataset.id, 10);
        const r = rounds.find(x => x.id === id);
        if (!r) return;
        switch (b.dataset.act) {
            case 'open': {
                const other = rounds.some(x => x.status === 'OPEN');
                if (!confirm(t(other ? 'admin.rounds.open.confirmOther' : 'admin.rounds.open.confirm', r.name))) return;
                await api(`/admin/api/rounds/${id}/open`, 'POST');
                break;
            }
            case 'close':
                if (!confirm(t('admin.rounds.close.confirm', r.name))) return;
                await api(`/admin/api/rounds/${id}/close`, 'POST');
                break;
            case 'reveal':
                if (!confirm(t('admin.rounds.reveal.confirm', r.name))) return;
                await api(`/admin/api/rounds/${id}/reveal`, 'POST');
                break;
            case 'delete':
                if (!confirm(t('admin.rounds.delete.confirm', r.name))) return;
                await api(`/admin/api/rounds/${id}`, 'DELETE');
                break;
            case 'edit': {
                const name = prompt(t('admin.rounds.edit.name'), r.name);
                if (name === null) return;
                const limit = prompt(t('admin.rounds.edit.testLimit'), String(r.testLimit || 0));
                if (limit === null) return;
                const minutes = prompt(t('admin.rounds.edit.timeLimit'), String((r.timeLimitSeconds || 0) / 60));
                if (minutes === null) return;
                await api(`/admin/api/rounds/${id}`, 'PUT', {
                    name,
                    testLimit: limit.trim() || '0',
                    timeLimitSeconds: String(Math.round((parseFloat(minutes) || 0) * 60))
                });
                toast(t('admin.saved'));
                break;
            }
        }
        loadRounds();
    });

    $('#create-round-form').addEventListener('submit', async e => {
        e.preventDefault();
        if (!requireSession()) return;
        const minutes = parseFloat($('#new-round-time').value) || 0;
        await api(`/admin/api/sessions/${selectedSessionId}/rounds`, 'POST', {
            name: $('#new-round-name').value.trim() || ('Round ' + (rounds.length + 1)),
            problemId: $('#new-round-problem').value,
            testLimit: $('#new-round-limit').value || '0',
            timeLimitSeconds: String(Math.round(minutes * 60))
        });
        $('#new-round-name').value = '';
        loadRounds();
    });

    function populateRoundSelects() {
        const opts = rounds.map(r => `<option value="${r.id}">${esc(r.name)} (${esc(r.status)})</option>`).join('');
        const fill = (sel, first) => {
            const cur = sel.value;
            sel.innerHTML = (first ? `<option value="">${esc(first)}</option>` : '') + opts;
            if (cur && rounds.some(r => String(r.id) === cur)) sel.value = cur;
            else if (!first && rounds.length) sel.value = String(preferredRound().id);
        };
        fill($('#sub-filter-round'), t('admin.submissions.filter.allRounds'));
        fill($('#analytics-round'), null);
        fill($('#export-round'), null);
    }

    /** Open round, else the most recently opened one, else the newest. */
    function preferredRound() {
        return rounds.find(r => r.status === 'OPEN')
            || rounds.filter(r => r.openedAt).sort((a, b) => b.openedAt - a.openedAt)[0]
            || rounds[0];
    }

    /* ── Settings ──────────────────────────────────── */

    async function loadSettings() {
        if (!selectedSessionId) return;
        const s = await api(`/admin/api/sessions/${selectedSessionId}/settings`);
        $('#set-name').value = s.name;
        $('#set-anonymity').value = s.anonymityMode;
        $('#set-joinOpen').value = String(s.joinOpen);
        $('#set-studentIdLength').value = s.studentIdLength;
        $('#set-mutantPanel').value = s.mutantPanelMode;
        $('#set-rateLimit').value = s.submissionRateLimitMs;
    }

    $('#settings-form').addEventListener('submit', async e => {
        e.preventDefault();
        if (!requireSession()) return;
        await api(`/admin/api/sessions/${selectedSessionId}/settings`, 'POST', {
            name: $('#set-name').value,
            anonymityMode: $('#set-anonymity').value,
            joinOpen: $('#set-joinOpen').value,
            studentIdLength: $('#set-studentIdLength').value || '0',
            mutantPanelMode: $('#set-mutantPanel').value,
            submissionRateLimitMs: $('#set-rateLimit').value || '0'
        });
        toast(t('admin.saved'));
        loadSessions();
    });

    /* ── Participants ──────────────────────────────── */

    async function loadParticipants() {
        if (!selectedSessionId) return;
        const data = await api(`/admin/api/sessions/${selectedSessionId}/participants`);
        participants = data.participants;
        $('#participant-count').textContent = participants.length;

        $('#participants-head').innerHTML = `<tr>
            <th>${esc(t('admin.participants.name'))}</th>
            <th>${esc(t('admin.participants.studentId'))}</th>
            <th>${esc(t('admin.participants.displayName'))}</th>
            <th>${esc(t('admin.participants.joinedAt'))}</th>
            <th title="${esc(t('admin.participants.rejoins.hint'))}">${esc(t('admin.participants.rejoins'))}</th>
            ${data.rounds.map(r => `<th class="num">${esc(r.name)}</th>`).join('')}
            <th>${esc(t('admin.participants.actions'))}</th></tr>`;

        const totals = Object.fromEntries(data.rounds.map(r => [r.id, r.totalMutants]));
        $('#participants-body').innerHTML = participants.length === 0
            ? `<tr><td colspan="${6 + data.rounds.length}" class="muted">${esc(t('admin.participants.empty'))}</td></tr>`
            : participants.map(p => `
                <tr>
                    <td>${esc(p.fullName)}</td>
                    <td>${esc(p.studentNumber)}</td>
                    <td>${esc(p.displayName)}</td>
                    <td>${fmtTime(p.joinedAt)}</td>
                    <td class="num ${p.rejoinCount > 0 ? 'warn-text' : ''}">${p.rejoinCount}</td>
                    ${p.scores.map(sc => `<td class="num" title="${sc.testsUsed} tests">${sc.testsUsed > 0
                        ? `${sc.score}/${totals[sc.roundId]} <span class="muted small">(${sc.testsUsed})</span>` : '–'}</td>`).join('')}
                    <td class="actions-cell">
                        <button class="btn btn-sm btn-outline" data-act="nick" data-id="${p.id}">${esc(t('admin.participants.editNickname'))}</button>
                        <button class="btn btn-sm btn-danger" data-act="kick" data-id="${p.id}">${esc(t('admin.participants.kick'))}</button>
                    </td>
                </tr>`).join('');
    }

    $('#tab-participants').addEventListener('click', async e => {
        const b = e.target.closest('button[data-act]');
        if (!b) return;
        const id = parseInt(b.dataset.id, 10);
        const p = participants.find(x => x.id === id);
        if (!p) return;
        if (b.dataset.act === 'kick') {
            if (!confirm(t('admin.participants.kick.confirm', p.fullName))) return;
            await api(`/admin/api/participants/${id}/kick`, 'POST');
        } else {
            const nick = prompt(t('admin.participants.editNickname.prompt', p.fullName), p.nickname || '');
            if (nick === null) return;
            await api(`/admin/api/participants/${id}/nickname`, 'POST', { nickname: nick });
        }
        loadParticipants();
    });

    /* ── Submissions ───────────────────────────────── */

    async function loadSubmissionFilters() {
        if (!selectedSessionId) return;
        await loadRounds();
        const data = await api(`/admin/api/sessions/${selectedSessionId}/participants`);
        const sel = $('#sub-filter-student');
        const cur = sel.value;
        sel.innerHTML = `<option value="">${esc(t('admin.submissions.filter.allStudents'))}</option>` +
            data.participants
                .slice().sort((a, b) => a.fullName.localeCompare(b.fullName))
                .map(p => `<option value="${p.id}">${esc(p.fullName)} (${esc(p.studentNumber)})</option>`).join('');
        if (cur) sel.value = cur;
    }

    async function loadSubmissions() {
        if (!selectedSessionId) return;
        const q = new URLSearchParams();
        if ($('#sub-filter-round').value) q.set('roundId', $('#sub-filter-round').value);
        if ($('#sub-filter-student').value) q.set('participantId', $('#sub-filter-student').value);
        if ($('#sub-filter-validity').value) q.set('validity', $('#sub-filter-validity').value);
        const subs = await api(`/admin/api/sessions/${selectedSessionId}/submissions?${q}`);

        $('#submission-count').textContent = t('admin.submissions.count', subs.length);
        $('#submissions-body').innerHTML = subs.length === 0
            ? `<tr><td colspan="10" class="muted">${esc(t('admin.submissions.empty'))}</td></tr>`
            : subs.map(s => `
                <tr>
                    <td>${fmtTime(s.time)}</td>
                    <td>${esc(s.roundName)}</td>
                    <td>${esc(s.participantName)}</td>
                    <td>${esc(s.studentNumber)}</td>
                    <td><code>[${esc(s.list)}]</code></td>
                    <td><code>${esc(s.target)}</code></td>
                    <td><code>${esc(s.expected)}</code></td>
                    <td><code>${esc(s.referenceResult)}</code></td>
                    <td><span class="badge-${esc(s.validity)}">${esc(s.validity)}</span></td>
                    <td>${esc((s.killedMutants || []).join(', '))}</td>
                </tr>`).join('');
    }

    ['#sub-filter-round', '#sub-filter-student', '#sub-filter-validity']
        .forEach(sel => $(sel).addEventListener('change', loadSubmissions));

    const liveSubmissions = throttle(() => { if (currentTab === 'submissions') loadSubmissions(); }, 1500);

    /* ── Analytics ─────────────────────────────────── */

    async function loadAnalytics() {
        const roundId = $('#analytics-round').value;
        if (!roundId) { hide($('#analytics-content')); return; }
        const data = await api(`/admin/api/rounds/${roundId}/analytics`);
        show($('#analytics-content'));
        $('#an-total').textContent = data.totalSubmissions;
        $('#an-invalid').textContent = Math.round(data.invalidRate * 100) + '% (' + data.invalidSubmissions + ')';
        $('#an-avg').textContent = data.averageScore.toFixed(2);
        $('#an-participants').textContent = data.activeParticipantCount + ' / ' + data.participantCount;

        const base = data.activeParticipantCount;
        $('#analytics-mutants').innerHTML = Object.entries(data.mutantKillCounts).map(([mid, count]) => {
            const pct = base > 0 ? Math.round(count / base * 100) : 0;
            return `
                <tr>
                    <td><strong>${esc(mid)}</strong></td>
                    <td>${esc(data.mutantNames[mid] || '')}</td>
                    <td class="num">${count}</td>
                    <td class="num">${pct}%</td>
                    <td class="bar-cell"><div class="kill-bar" style="width:${pct}%"></div></td>
                </tr>`;
        }).join('');
    }

    $('#analytics-round').addEventListener('change', loadAnalytics);
    const liveAnalytics = throttle(() => { if (currentTab === 'analytics') loadAnalytics(); }, 2000);

    /* ── Board control & export ────────────────────── */

    $$('[data-board-cmd]').forEach(b => b.addEventListener('click', async () => {
        await api('/admin/api/board-command', 'POST', { command: b.dataset.boardCmd });
        toast(t('admin.board.sent'));
    }));

    function updateExportLinks() {
        const sid = selectedSessionId;
        const rid = $('#export-round').value;
        const set = (sel, href) => {
            const a = $(sel);
            if (href) { a.href = href; a.classList.remove('disabled'); }
            else { a.removeAttribute('href'); a.classList.add('disabled'); }
        };
        set('#export-session-participants', sid && `/admin/api/sessions/${sid}/export/participants`);
        set('#export-session-submissions', sid && `/admin/api/sessions/${sid}/export/submissions`);
        set('#export-round-participants', rid && `/admin/api/rounds/${rid}/export/participants`);
        set('#export-round-submissions', rid && `/admin/api/rounds/${rid}/export/submissions`);
    }
    $('#export-round').addEventListener('change', updateExportLinks);

    /* ── SSE ───────────────────────────────────────── */

    const liveParticipants = throttle(() => { if (currentTab === 'participants') loadParticipants(); }, 1500);

    liveStream('/sse/admin', {
        'submission': () => { liveSubmissions(); liveParticipants(); },
        'scoreboard': () => { liveAnalytics(); },
        'participants': () => liveParticipants(),
        'participant-rejoined': d => {
            toast(t('admin.participants.rejoined', d.fullName, d.studentNumber));
            liveParticipants();
        },
        'round-state': () => { if (['rounds', 'analytics', 'board', 'submissions'].includes(currentTab)) loadRounds(); }
    }, () => { if (selectedSessionId) refreshTab(); },
       ok => $('#conn-dot').classList.toggle('ok', ok));

    /* ── Init ──────────────────────────────────────── */

    (async () => {
        await loadProblems();
        await loadSessions();
        const tab = (location.hash || '#sessions').slice(1);
        switchTab($('#tab-' + tab) ? tab : 'sessions');
    })();
})();
