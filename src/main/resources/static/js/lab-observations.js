/**
 * Lab Observations Explorer
 * -------------------------
 * Searches the FHIR `Observation` resource on the configured MHD endpoint using
 * LOINC codes and renders both the raw JSON and a laboratory-oriented view
 * (value, unit, interpretation flag, reference-range gauge, specimen, notes…).
 *
 * The endpoint + OAuth2 settings are SHARED with the MHD MADO Viewer: this app
 * reads/writes the same `madoViewerConfig` localStorage key so that whatever you
 * configured in the viewer is reused here automatically (and vice-versa).
 */
const LabObservations = (function () {
    'use strict';

    // Shared with the MHD MADO Viewer so settings are reused across both apps.
    const CONFIG_KEY = 'madoViewerConfig';
    const DEFAULT_CONFIG = {
        fhirEndpoint: '',  // empty => use local ./fhir
        ohifViewerUrl: 'https://ihebelgium.ehealthhub.be/ohif/mado',
        authEnabled: false,
        tokenUrl: '',
        clientId: '',
        clientSecret: '',
        scope: '',
        audience: '',
        resource: '',
        providerId: ''
    };

    const LOINC_SYSTEM = 'http://loinc.org';

    // Server-side reverse proxy endpoints (avoid browser CORS against the
    // secured FHIR endpoint and its Keycloak token endpoint).
    const PROXY_TOKEN_URL = './api/mhd-proxy/token';
    const PROXY_FHIR_URL = './api/mhd-proxy/fhir';

    // A small palette of common lab analytes (LOINC) for quick searching.
    const COMMON_ANALYTES = [
        { code: '2951-2', label: 'Natrium (Sodium)' },
        { code: '2823-3', label: 'Kalium (Potassium)' },
        { code: '2160-0', label: 'Creatinine' },
        { code: '2345-7', label: 'Glucose' },
        { code: '718-7',  label: 'Hemoglobin' },
        { code: '1988-5', label: 'C-Reactive Protein' },
        { code: '6690-2', label: 'Leukocytes (WBC)' },
        { code: '2093-3', label: 'Cholesterol total' }
    ];

    const state = {
        config: { ...DEFAULT_CONFIG },
        bundle: null,
        observations: [],
        tokenCache: null
    };

    let el = {};

    // ==============================
    // Init
    // ==============================
    function init() {
        loadConfig();
        el = {
            searchBtn: document.getElementById('searchBtn'),
            clearBtn: document.getElementById('clearBtn'),
            configBtn: document.getElementById('configBtn'),
            inspectJsonBtn: document.getElementById('inspectJsonBtn'),
            demoBtn: document.getElementById('demoBtn'),
            endpointBadge: document.getElementById('endpointBadge'),
            loincCodes: document.getElementById('loincCodes'),
            patientId: document.getElementById('patientId'),
            category: document.getElementById('category'),
            dateFrom: document.getElementById('dateFrom'),
            dateTo: document.getElementById('dateTo'),
            quickPick: document.getElementById('quickPick'),
            resultsContent: document.getElementById('resultsContent'),
            statsGrid: document.getElementById('statsGrid'),
            errorBanner: document.getElementById('errorBanner'),
            // config modal
            fhirEndpoint: document.getElementById('fhirEndpoint'),
            authEnabled: document.getElementById('authEnabled'),
            authFields: document.getElementById('authFields'),
            tokenUrl: document.getElementById('tokenUrl'),
            clientId: document.getElementById('clientId'),
            clientSecret: document.getElementById('clientSecret'),
            authScope: document.getElementById('authScope'),
            authAudience: document.getElementById('authAudience'),
            authResource: document.getElementById('authResource'),
            providerId: document.getElementById('providerId'),
            // json modals
            jsonContent: document.getElementById('jsonContent'),
            obsJsonContent: document.getElementById('obsJsonContent')
        };

        renderQuickPick();
        wireEvents();
        updateEndpointBadge();
        renderEmpty();
    }

    function wireEvents() {
        el.searchBtn.addEventListener('click', performSearch);
        el.clearBtn.addEventListener('click', clearSearch);
        el.configBtn.addEventListener('click', openConfigModal);
        el.inspectJsonBtn.addEventListener('click', openBundleJson);
        el.demoBtn.addEventListener('click', loadDemo);
        if (el.authEnabled) el.authEnabled.addEventListener('change', toggleAuthFields);

        document.querySelectorAll('.search-field input').forEach(f => {
            f.addEventListener('keypress', e => { if (e.key === 'Enter') performSearch(); });
        });
        document.addEventListener('keydown', e => { if (e.key === 'Escape') closeAllModals(); });
    }

    function renderQuickPick() {
        el.quickPick.innerHTML = COMMON_ANALYTES.map(a =>
            `<button type="button" class="chip" data-code="${a.code}" title="LOINC ${a.code}">${escapeHtml(a.label)}</button>`
        ).join('');
        el.quickPick.querySelectorAll('.chip').forEach(btn => {
            btn.addEventListener('click', () => {
                const code = btn.dataset.code;
                const cur = el.loincCodes.value.split(',').map(s => s.trim()).filter(Boolean);
                if (!cur.includes(code)) cur.push(code);
                el.loincCodes.value = cur.join(', ');
            });
        });
    }

    // ==============================
    // Configuration (shared with MADO Viewer)
    // ==============================
    function loadConfig() {
        try {
            const saved = localStorage.getItem(CONFIG_KEY);
            if (saved) state.config = { ...DEFAULT_CONFIG, ...JSON.parse(saved) };
        } catch (e) {
            console.warn('Failed to load shared config:', e);
        }
    }

    function openConfigModal() {
        el.fhirEndpoint.value = state.config.fhirEndpoint || '';
        if (el.authEnabled) el.authEnabled.checked = !!state.config.authEnabled;
        if (el.tokenUrl) el.tokenUrl.value = state.config.tokenUrl || '';
        if (el.clientId) el.clientId.value = state.config.clientId || '';
        if (el.clientSecret) el.clientSecret.value = state.config.clientSecret || '';
        if (el.authScope) el.authScope.value = state.config.scope || '';
        if (el.authAudience) el.authAudience.value = state.config.audience || '';
        if (el.authResource) el.authResource.value = state.config.resource || '';
        if (el.providerId) el.providerId.value = state.config.providerId || '';
        toggleAuthFields();
        openModal('configModal');
    }

    function toggleAuthFields() {
        if (el.authFields) el.authFields.style.display = el.authEnabled?.checked ? 'block' : 'none';
    }

    function applyPreset(preset) {
        switch (preset) {
            case 'local':
                el.fhirEndpoint.value = '';
                setAuthEnabled(false);
                break;
            case 'ihe':
                el.fhirEndpoint.value = 'https://ihebelgium.ehealthhub.be/TheDICOMPolice/fhir';
                setAuthEnabled(false);
                break;
            case 'abrumet':
                el.fhirEndpoint.value = 'https://fhir-qa.abrumet.plus/fhirstation-rest/api/fhir/';
                el.tokenUrl.value = 'https://fhir-qa.abrumet.plus/auth/realms/fhir-station/protocol/openid-connect/token';
                el.clientId.value = 'vzn-viewer-abrumet-acc';
                el.clientSecret.value = '5MNRpkv1xWDhGagaDivuvt53ZhFGAWGt';
                el.authScope.value = 'fhir-station-application';
                if (el.providerId) el.providerId.value = '87082839962';
                setAuthEnabled(true);
                break;
        }
        showToast(`Applied ${preset} preset`, 'info');
    }

    function setAuthEnabled(enabled) {
        if (el.authEnabled) el.authEnabled.checked = enabled;
        toggleAuthFields();
    }

    function saveConfig() {
        // Merge onto the existing saved config so we don't clobber fields the
        // MADO Viewer owns (e.g. ohifViewerUrl).
        const existing = { ...DEFAULT_CONFIG };
        try {
            const saved = localStorage.getItem(CONFIG_KEY);
            if (saved) Object.assign(existing, JSON.parse(saved));
        } catch (e) { /* ignore */ }

        state.config = {
            ...existing,
            fhirEndpoint: el.fhirEndpoint?.value.trim() || '',
            authEnabled: !!el.authEnabled?.checked,
            tokenUrl: el.tokenUrl?.value.trim() || '',
            clientId: el.clientId?.value.trim() || '',
            clientSecret: el.clientSecret?.value.trim() || '',
            scope: el.authScope?.value.trim() || '',
            audience: el.authAudience?.value.trim() || '',
            resource: el.authResource?.value.trim() || '',
            providerId: el.providerId?.value.trim() || ''
        };
        state.tokenCache = null;

        try {
            localStorage.setItem(CONFIG_KEY, JSON.stringify(state.config));
            showToast('Configuration saved (shared with MADO Viewer)', 'success');
        } catch (e) {
            showToast('Failed to save configuration', 'error');
        }
        closeModal('configModal');
        updateEndpointBadge();
    }

    function updateEndpointBadge() {
        if (!el.endpointBadge) return;
        const endpoint = state.config.fhirEndpoint;
        const lock = state.config.authEnabled ? '🔐 ' : '';
        if (endpoint) {
            try {
                const url = new URL(endpoint);
                el.endpointBadge.textContent = lock + url.hostname;
                el.endpointBadge.title = (state.config.authEnabled ? 'Authenticated • ' : '') + endpoint;
            } catch {
                el.endpointBadge.textContent = lock + endpoint;
            }
        } else {
            el.endpointBadge.textContent = 'Local';
            el.endpointBadge.title = 'Using local ./fhir endpoint';
        }
    }

    // ==============================
    // Authenticated FHIR fetch (reuses the MADO Viewer proxy machinery)
    // ==============================
    async function getAccessToken() {
        const cfg = state.config;
        if (!cfg.authEnabled || !cfg.tokenUrl) return null;

        const now = Date.now();
        if (state.tokenCache && state.tokenCache.token && state.tokenCache.expiresAt - 60000 > now) {
            return state.tokenCache.token;
        }

        const resp = await fetch(PROXY_TOKEN_URL, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                tokenUrl: cfg.tokenUrl,
                clientId: cfg.clientId || '',
                clientSecret: cfg.clientSecret || '',
                scope: cfg.scope || '',
                audience: cfg.audience || '',
                resource: cfg.resource || ''
            })
        });
        if (!resp.ok) {
            const text = await resp.text().catch(() => '');
            throw new Error(`Token request failed (HTTP ${resp.status})${text ? ': ' + text : ''}`);
        }
        const data = await resp.json();
        if (!data.access_token) throw new Error('Token response did not contain an access_token');
        const expiresInMs = (data.expires_in || 300) * 1000;
        state.tokenCache = { token: data.access_token, expiresAt: now + expiresInMs };
        return state.tokenCache.token;
    }

    /**
     * fetch() wrapper. When auth is enabled the request is routed through the
     * server-side proxy with a Bearer token, the fixed X-Provider header and an
     * X-Patient header (the secured endpoint requires it for patient-scoped data).
     */
    async function fhirFetch(url, patientId) {
        const headers = { 'Accept': 'application/fhir+json' };
        if (state.config.authEnabled) {
            const token = await getAccessToken();
            if (token) headers['Authorization'] = 'Bearer ' + token;
            if (state.config.providerId) headers['X-Provider'] = state.config.providerId;
            if (patientId) headers['X-Patient'] = patientId;
            const proxiedUrl = `${PROXY_FHIR_URL}?target=${encodeURIComponent(url)}`;
            return fetch(proxiedUrl, { headers });
        }
        return fetch(url, { headers });
    }

    // ==============================
    // Search
    // ==============================
    async function performSearch() {
        hideError();
        showLoading();

        const codes = el.loincCodes.value.split(',').map(s => s.trim()).filter(Boolean);
        const patientId = el.patientId.value.trim();

        // patient.identifier is always required when searching Observations
        // (the FHIR endpoint scopes lab results per patient and rejects
        // unscoped LOINC-only queries).
        if (!patientId) {
            renderEmpty('A Patient ID is required to search Observations.');
            el.statsGrid.style.display = 'none';
            el.patientId.focus();
            showToast('Patient ID is required (patient.identifier)', 'error');
            return;
        }

        const params = new URLSearchParams();
        // patient.identifier is mandatory and always supplied.
        params.append('patient.identifier', patientId);
        if (codes.length) {
            // token search: system|code, comma-joined => OR
            params.append('code', codes.map(c => `${LOINC_SYSTEM}|${c}`).join(','));
        }

        const category = el.category.value;
        if (category) params.append('category', category);

        const dateFrom = el.dateFrom.value;
        const dateTo = el.dateTo.value;
        if (dateFrom) params.append('date', `ge${dateFrom}`);
        if (dateTo) params.append('date', `le${dateTo}`);

        params.append('_count', '200');

        const baseUrl = (state.config.fhirEndpoint || './fhir').replace(/\/+$/, '');
        const url = `${baseUrl}/Observation?${params.toString()}`;

        try {
            const resp = await fhirFetch(url, patientId);
            if (!resp.ok) {
                const text = await resp.text().catch(() => '');
                throw new Error(`HTTP ${resp.status} ${resp.statusText}${text ? ' — ' + text.slice(0, 300) : ''}`);
            }
            const bundle = await resp.json();
            handleBundle(bundle);
        } catch (err) {
            console.error('Observation search failed:', err);
            showError(`Search failed: ${err.message}`);
            renderEmpty();
            el.statsGrid.style.display = 'none';
        }
    }

    function handleBundle(bundle) {
        state.bundle = bundle;
        const entries = (bundle && bundle.entry) ? bundle.entry : [];
        state.observations = entries
            .map(e => e.resource)
            .filter(r => r && r.resourceType === 'Observation');

        if (state.observations.length === 0) {
            renderEmpty('No Observation resources matched your search.');
            el.statsGrid.style.display = 'none';
            return;
        }
        renderStats();
        renderResults();
    }

    function clearSearch() {
        el.loincCodes.value = '';
        el.patientId.value = '';
        el.category.value = '';
        el.dateFrom.value = '';
        el.dateTo.value = '';
        state.bundle = null;
        state.observations = [];
        el.statsGrid.style.display = 'none';
        hideError();
        renderEmpty();
    }

    // ==============================
    // Demo (offline sample bundle)
    // ==============================
    function loadDemo() {
        el.loincCodes.value = '2951-2, 2823-3';
        el.patientId.value = DEMO_PATIENT_ID;
        handleBundle(DEMO_BUNDLE);
        showToast('Loaded sample lab Observations', 'info');
    }

    // ==============================
    // Rendering
    // ==============================
    function renderStats() {
        const obs = state.observations;
        const total = obs.length;
        let abnormal = 0, critical = 0;
        const analytes = new Set();
        obs.forEach(o => {
            analytes.add(obsName(o));
            const c = interpretationCode(o).toUpperCase();
            if (c) abnormal++;
            if (c === 'HH' || c === 'LL') critical++;
        });

        const items = [
            { label: 'Observations', value: total, cls: '' },
            { label: 'Distinct analytes', value: analytes.size, cls: '' },
            { label: 'Out of range', value: abnormal, cls: abnormal ? 'stat-warn' : '' },
            { label: 'Critical', value: critical, cls: critical ? 'stat-crit' : '' }
        ];
        el.statsGrid.innerHTML = items.map(i =>
            `<div class="stat-card ${i.cls}"><div class="stat-value">${i.value}</div><div class="stat-label">${i.label}</div></div>`
        ).join('');
        el.statsGrid.style.display = 'grid';
    }

    function renderResults() {
        // Sort newest first.
        const obs = [...state.observations].sort((a, b) =>
            (obsDate(b) || '').localeCompare(obsDate(a) || ''));

        el.resultsContent.innerHTML = obs.map((o, i) => renderObsCard(o, i)).join('');

        el.resultsContent.querySelectorAll('[data-obs-json]').forEach(btn => {
            btn.addEventListener('click', () => {
                const idx = parseInt(btn.dataset.obsJson, 10);
                renderJson(obs[idx], 'obsJsonContent');
                openModal('obsJsonModal');
            });
        });
    }

    function renderObsCard(obs, index) {
        const name = obsName(obs);
        const loinc = obs.code?.coding?.find(c => (c.system || '').includes('loinc'))?.code || '';
        const value = formatObsValue(obs);
        const unit = formatObsUnit(obs);
        const flagCode = interpretationCode(obs);
        const flagClass = interpretationClass(flagCode);
        const flagLabel = obs.interpretation?.[0]?.coding?.[0]?.display || flagCode;
        const ref = formatRefRange(obs);
        const status = obs.status || '';
        const issued = formatLabDateTime(obs.issued || obsDate(obs));
        const notes = obsNotes(obs);
        const specimen = obs.specimen?.reference || '';

        const gauge = renderGauge(obs);

        const metaItem = (label, val) => val
            ? `<div class="obs-meta-item"><span class="obs-meta-label">${label}</span><span class="obs-meta-value">${escapeHtml(val)}</span></div>`
            : '';

        return `
        <div class="obs-card ${flagClass ? 'obs-card-flagged' : ''}">
            <div class="obs-card-head">
                <div class="obs-name">
                    <span class="obs-analyte">${escapeHtml(name)}</span>
                    ${loinc ? `<span class="obs-loinc" title="LOINC code">LOINC ${escapeHtml(loinc)}</span>` : ''}
                </div>
                <span class="obs-status obs-status-${escapeHtml((status || '').toLowerCase())}">${escapeHtml(status)}</span>
            </div>
            <div class="obs-value-row">
                <div class="obs-value ${flagClass}">
                    <span class="obs-value-num">${escapeHtml(value)}</span>
                    <span class="obs-value-unit">${escapeHtml(unit)}</span>
                    ${flagCode ? `<span class="obs-flag ${flagClass}" title="Interpretation">${escapeHtml(flagLabel || flagCode)}</span>` : ''}
                </div>
                <div class="obs-ref">${ref ? `Ref: ${escapeHtml(ref)}` : ''}</div>
            </div>
            ${gauge}
            <div class="obs-meta-grid">
                ${metaItem('Issued', issued)}
                ${metaItem('Specimen', specimen)}
            </div>
            ${notes.length ? `<div class="obs-notes">${notes.map(n => `<div class="obs-note">📝 ${escapeHtml(n)}</div>`).join('')}</div>` : ''}
            <div class="obs-card-actions">
                <button class="btn-secondary btn-small" data-obs-json="${index}">{ } View JSON</button>
            </div>
        </div>`;
    }

    /**
     * Render a horizontal reference-range gauge showing where the measured value
     * falls relative to the low/high bounds.
     */
    function renderGauge(obs) {
        const v = numericValue(obs);
        const rr = obs.referenceRange?.[0];
        const low = rr?.low?.value;
        const high = rr?.high?.value;
        if (v === null || low === undefined || high === undefined || high <= low) return '';

        // Extend the visual scale a bit beyond the reference window.
        const span = high - low;
        const min = low - span * 0.6;
        const max = high + span * 0.6;
        const pct = x => Math.max(0, Math.min(100, ((x - min) / (max - min)) * 100));

        const lowPct = pct(low);
        const highPct = pct(high);
        const valPct = pct(v);
        const inRange = v >= low && v <= high;
        const markerCls = inRange ? 'gauge-marker-ok' : (v < low ? 'gauge-marker-low' : 'gauge-marker-high');

        return `
        <div class="obs-gauge" title="${escapeHtml(String(v))} (ref ${escapeHtml(String(low))}–${escapeHtml(String(high))})">
            <div class="gauge-track">
                <div class="gauge-normal" style="left:${lowPct}%;width:${Math.max(0, highPct - lowPct)}%;"></div>
                <div class="gauge-marker ${markerCls}" style="left:${valPct}%;"></div>
            </div>
            <div class="gauge-scale">
                <span class="gauge-bound" style="left:${lowPct}%;">${escapeHtml(String(low))}</span>
                <span class="gauge-bound" style="left:${highPct}%;">${escapeHtml(String(high))}</span>
            </div>
        </div>`;
    }

    function renderEmpty(msg) {
        el.resultsContent.innerHTML = `
        <div class="empty-state">
            <div class="empty-state-icon">🧪</div>
            <div class="empty-state-title">${msg ? escapeHtml(msg) : 'No results yet'}</div>
            <div class="empty-state-text">Enter one or more LOINC codes (e.g. <code>2951-2</code> for Natrium) and click
            <strong>Search</strong>, or click <strong>Load Demo</strong> to explore offline.</div>
        </div>`;
    }

    function showLoading() {
        el.resultsContent.innerHTML = `
        <div class="empty-state">
            <div class="spinner"></div>
            <div class="empty-state-title">Searching Observations…</div>
        </div>`;
    }

    function openBundleJson() {
        if (!state.bundle) { showToast('Run a search first', 'info'); return; }
        renderJson(state.bundle, 'jsonContent');
        openModal('jsonModal');
    }

    // ==============================
    // FHIR helpers
    // ==============================
    function codeText(cc) {
        if (!cc) return '';
        return cc.text || cc.coding?.[0]?.display || cc.coding?.[0]?.code || '';
    }
    function obsName(obs) { return codeText(obs.code) || 'Observation'; }
    function obsDate(obs) { return obs.effectiveDateTime || obs.issued || obs.effectivePeriod?.start || ''; }

    function numericValue(obs) {
        const v = obs.valueQuantity?.value;
        return (typeof v === 'number') ? v : null;
    }
    function formatObsValue(obs) {
        const q = obs.valueQuantity;
        if (q) {
            const cmp = q.comparator || '';
            const val = (q.value !== undefined && q.value !== null) ? q.value : '';
            return `${cmp}${val}`.trim();
        }
        if (obs.valueString !== undefined && obs.valueString !== null) return obs.valueString;
        if (obs.valueCodeableConcept) return codeText(obs.valueCodeableConcept);
        if (obs.valueBoolean !== undefined) return String(obs.valueBoolean);
        return '';
    }
    function formatObsUnit(obs) { return obs.valueQuantity?.unit || obs.valueQuantity?.code || ''; }

    function formatRefRange(obs) {
        const rr = obs.referenceRange?.[0];
        if (!rr) return '';
        const low = rr.low?.value;
        const high = rr.high?.value;
        const unit = rr.high?.unit || rr.low?.unit || '';
        if (low !== undefined && high !== undefined) return `${low} – ${high} ${unit}`.trim();
        if (high !== undefined) return `< ${high} ${unit}`.trim();
        if (low !== undefined) return `> ${low} ${unit}`.trim();
        return rr.text || '';
    }
    function interpretationCode(obs) { return obs.interpretation?.[0]?.coding?.[0]?.code || ''; }
    function interpretationClass(code) {
        const c = (code || '').toUpperCase();
        if (c === 'HH') return 'lab-flag-critical-high';
        if (c === 'LL') return 'lab-flag-critical-low';
        if (c === 'H') return 'lab-flag-high';
        if (c === 'L') return 'lab-flag-low';
        if (c === 'A' || c === 'AA') return 'lab-flag-abnormal';
        return '';
    }
    function obsNotes(obs) { return (obs.note || []).map(n => n.text).filter(Boolean); }

    function formatLabDateTime(s) {
        if (!s) return '';
        const d = new Date(s);
        if (isNaN(d.getTime())) return s;
        return d.toLocaleString();
    }

    // ==============================
    // JSON viewer (lightweight syntax highlight)
    // ==============================
    function renderJson(obj, targetId) {
        const target = document.getElementById(targetId);
        if (!target) return;
        const json = JSON.stringify(obj, null, 2);
        target.innerHTML = syntaxHighlight(json);
    }

    function syntaxHighlight(json) {
        const esc = json.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
        return esc.replace(
            /("(\\u[a-zA-Z0-9]{4}|\\[^u]|[^\\"])*"(\s*:)?|\b(true|false|null)\b|-?\d+(?:\.\d*)?(?:[eE][+\-]?\d+)?)/g,
            match => {
                let cls = 'json-number';
                if (/^"/.test(match)) {
                    cls = /:$/.test(match) ? 'json-key' : 'json-string';
                } else if (/true|false/.test(match)) {
                    cls = 'json-boolean';
                } else if (/null/.test(match)) {
                    cls = 'json-null';
                }
                return `<span class="${cls}">${match}</span>`;
            }
        );
    }

    // ==============================
    // Modal + toast utilities
    // ==============================
    function openModal(id) {
        const m = document.getElementById(id);
        if (m) m.style.display = 'flex';
    }
    function closeModal(id) {
        const m = document.getElementById(id);
        if (m) m.style.display = 'none';
    }
    function closeAllModals() {
        document.querySelectorAll('.modal').forEach(m => m.style.display = 'none');
    }

    function showError(msg) {
        if (!el.errorBanner) return;
        el.errorBanner.textContent = msg;
        el.errorBanner.style.display = 'block';
    }
    function hideError() {
        if (el.errorBanner) el.errorBanner.style.display = 'none';
    }

    function showToast(message, type = 'info') {
        let container = document.getElementById('toastContainer');
        if (!container) {
            container = document.createElement('div');
            container.id = 'toastContainer';
            container.className = 'toast-container';
            document.body.appendChild(container);
        }
        const toast = document.createElement('div');
        toast.className = `toast toast-${type}`;
        toast.textContent = message;
        container.appendChild(toast);
        setTimeout(() => toast.classList.add('toast-show'), 10);
        setTimeout(() => {
            toast.classList.remove('toast-show');
            setTimeout(() => toast.remove(), 300);
        }, 3200);
    }

    function escapeHtml(s) {
        if (s === null || s === undefined) return '';
        return String(s)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    }

    // ==============================
    // Demo bundle (mirrors the user's eHealth lab example)
    // ==============================
    const DEMO_PATIENT_ID = '64.08.12-205.33';
    const DEMO_BUNDLE = {
        resourceType: 'Bundle',
        type: 'searchset',
        total: 3,
        entry: [
            {
                fullUrl: 'urn:uuid:obs-natrium',
                resource: {
                    resourceType: 'Observation',
                    id: 'obs-natrium',
                    status: 'final',
                    code: { coding: [{ system: LOINC_SYSTEM, code: '2951-2' }], text: 'Natrium' },
                    subject: { reference: 'urn:uuid:52a71823-6731-53ec-84d6-e7f7b032be4b' },
                    issued: '2026-04-08T13:42:23.000+02:00',
                    effectiveDateTime: '2026-04-08T13:42:23.000+02:00',
                    valueQuantity: { value: 120, unit: 'mmol/L', system: 'http://unitsofmeasure.org', code: 'mmol/L' },
                    interpretation: [{ coding: [{ system: 'http://terminology.hl7.org/CodeSystem/v3-ObservationInterpretation', code: 'L', display: 'Low' }] }],
                    note: [{ text: 'Accreditatie volgens BELAC-certificaat 087-MED' }],
                    specimen: { reference: 'urn:uuid:ed4ae7cf-1974-5db8-bac6-2a92ee4786f7' },
                    referenceRange: [{
                        low: { value: 136, unit: 'mmol/L', system: 'http://unitsofmeasure.org', code: 'mmol/L' },
                        high: { value: 145, unit: 'mmol/L', system: 'http://unitsofmeasure.org', code: 'mmol/L' }
                    }]
                }
            },
            {
                fullUrl: 'urn:uuid:obs-kalium',
                resource: {
                    resourceType: 'Observation',
                    id: 'obs-kalium',
                    status: 'final',
                    code: { coding: [{ system: LOINC_SYSTEM, code: '2823-3' }], text: 'Kalium' },
                    subject: { reference: 'urn:uuid:52a71823-6731-53ec-84d6-e7f7b032be4b' },
                    issued: '2026-04-08T13:42:23.000+02:00',
                    effectiveDateTime: '2026-04-08T13:42:23.000+02:00',
                    valueQuantity: { value: 4.2, unit: 'mmol/L', system: 'http://unitsofmeasure.org', code: 'mmol/L' },
                    specimen: { reference: 'urn:uuid:ed4ae7cf-1974-5db8-bac6-2a92ee4786f7' },
                    referenceRange: [{
                        low: { value: 3.5, unit: 'mmol/L' },
                        high: { value: 5.1, unit: 'mmol/L' }
                    }]
                }
            },
            {
                fullUrl: 'urn:uuid:obs-crp',
                resource: {
                    resourceType: 'Observation',
                    id: 'obs-crp',
                    status: 'final',
                    code: { coding: [{ system: LOINC_SYSTEM, code: '1988-5' }], text: 'C-Reactive Protein' },
                    subject: { reference: 'urn:uuid:52a71823-6731-53ec-84d6-e7f7b032be4b' },
                    issued: '2026-04-08T13:42:23.000+02:00',
                    effectiveDateTime: '2026-04-08T13:42:23.000+02:00',
                    valueQuantity: { value: 38, unit: 'mg/L', system: 'http://unitsofmeasure.org', code: 'mg/L' },
                    interpretation: [{ coding: [{ system: 'http://terminology.hl7.org/CodeSystem/v3-ObservationInterpretation', code: 'H', display: 'High' }] }],
                    referenceRange: [{ high: { value: 5, unit: 'mg/L' } }]
                }
            }
        ]
    };

    // Public API
    return {
        init,
        saveConfig,
        applyPreset,
        closeModal
    };
})();

document.addEventListener('DOMContentLoaded', LabObservations.init);

