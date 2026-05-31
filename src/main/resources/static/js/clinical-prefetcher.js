/**
 * Smart Clinical Context Prefetcher & Sorter
 * ------------------------------------------
 * Orchestrates the four-phase pipeline:
 *   1. Query the MHD DocumentReference registry for a patient's imaging priors.
 *   2. Normalize bulky FHIR resources into a slim metadata schema.
 *   3. Ask a local Qwen model (via the server-side Ollama proxy) to rank each
 *      study into a clinical relevance Tier (1 = high, 2 = moderate, 3 = low).
 *   4. Sort + render the dashboard and wire up the MADO manifest handoff.
 */
const ClinicalPrefetcher = (function () {
    'use strict';

    const CONFIG_KEY = 'clinicalPrefetcherConfig';
    const DEFAULT_CONFIG = {
        fhirEndpoint: '',  // empty => use local ./fhir
        ohifViewerUrl: 'https://ihebelgium.ehealthhub.be/ohif/mado'
    };

    const MODALITY_LABEL = {
        CR: 'CR (Computed Radiography)', DX: 'DX (Digital Radiography)', CT: 'CT (Computed Tomography)',
        MR: 'MR (Magnetic Resonance)', US: 'US (Ultrasound)', MG: 'MG (Mammography)',
        NM: 'NM (Nuclear Medicine)', PT: 'PT (PET)', XA: 'XA (X-Ray Angiography)',
        SR: 'SR (Structured Report)', KO: 'KO (Key Object Selection)', OT: 'OT (Other)'
    };

    const TIER_META = {
        1: { label: 'Tier 1 · Highly Relevant', cls: 'tier-1', badge: '🟢' },
        2: { label: 'Tier 2 · Moderately Relevant', cls: 'tier-2', badge: '🟡' },
        3: { label: 'Tier 3 · Low / No Relevance', cls: 'tier-3', badge: '⚪' }
    };

    const state = {
        config: { ...DEFAULT_CONFIG },
        models: [],
        selectedModel: '',
        studies: [],     // normalized + ranked study objects
        reason: '',
        loading: false
    };

    let el = {};

    // ==============================
    // Demo data (works fully offline; the LLM call still hits local Ollama)
    // ==============================
    const DEMO = {
        patientId: '64.08.12-205.33',
        reason: 'Right hip pain after a fall, rule out fracture',
        studies: [
            { id: 'doc-ref-001', date: '2024-11-12T14:30:00Z', modality: 'CR', bodySite: 'Pelvis/Hip',
              title: 'X-RAY PELVIS 1-2 VIEWS', facility: 'Community Hospital A',
              summary_or_conclusion: 'No acute fracture. Mild osteoarthritis of the right hip joint.' },
            { id: 'doc-ref-002', date: '2025-03-01T09:15:00Z', modality: 'CT', bodySite: 'Chest',
              title: 'CT CHEST W IV CONTRAST', facility: 'Regional Medical Center',
              summary_or_conclusion: 'Subcentimeter pulmonary nodule in the right upper lobe, stable.' },
            { id: 'doc-ref-003', date: '2023-06-20T11:00:00Z', modality: 'MR', bodySite: 'Right Hip',
              title: 'MRI RIGHT HIP WO CONTRAST', facility: 'University Hospital',
              summary_or_conclusion: 'Labral tear, early degenerative changes of the right hip.' },
            { id: 'doc-ref-004', date: '2022-01-05T08:45:00Z', modality: 'DX', bodySite: 'Lumbar Spine',
              title: 'X-RAY LUMBAR SPINE', facility: 'Community Hospital A',
              summary_or_conclusion: 'Mild multilevel degenerative disc disease. No acute findings.' },
            { id: 'doc-ref-005', date: '2025-02-18T16:20:00Z', modality: 'US', bodySite: 'Abdomen',
              title: 'US ABDOMEN COMPLETE', facility: 'Regional Medical Center',
              summary_or_conclusion: 'Hepatic steatosis. No gallstones. Otherwise unremarkable.' },
            { id: 'doc-ref-006', date: '2021-09-30T13:10:00Z', modality: 'CR', bodySite: 'Right Femur',
              title: 'X-RAY RIGHT FEMUR', facility: 'University Hospital',
              summary_or_conclusion: 'Healed femoral shaft fracture with intramedullary nail in situ.' }
        ]
    };

    // ==============================
    // Init
    // ==============================
    function init() {
        loadConfig();
        el = {
            settingsBtn: document.getElementById('settingsBtn'),
            recheckBtn: document.getElementById('recheckBtn'),
            ollamaDot: document.getElementById('ollamaDot'),
            ollamaStatusText: document.getElementById('ollamaStatusText'),
            modelSelect: document.getElementById('modelSelect'),
            patientId: document.getElementById('patientId'),
            reasonForVisit: document.getElementById('reasonForVisit'),
            analyzeBtn: document.getElementById('analyzeBtn'),
            loadDemoBtn: document.getElementById('loadDemoBtn'),
            hideTier3: document.getElementById('hideTier3'),
            resultsContent: document.getElementById('resultsContent'),
            errorBanner: document.getElementById('errorBanner'),
            reasonChip: document.getElementById('reasonChip'),
            statsPanel: document.getElementById('statsPanel'),
            tier1Count: document.getElementById('tier1Count'),
            tier2Count: document.getElementById('tier2Count'),
            tier3Count: document.getElementById('tier3Count'),
            fhirEndpoint: document.getElementById('fhirEndpoint'),
            ohifViewerUrl: document.getElementById('ohifViewerUrl'),
            docJsonContent: document.getElementById('docJsonContent')
        };

        el.settingsBtn.addEventListener('click', openSettings);
        el.recheckBtn.addEventListener('click', checkOllamaStatus);
        el.analyzeBtn.addEventListener('click', runAnalysis);
        el.loadDemoBtn.addEventListener('click', loadDemo);
        el.modelSelect.addEventListener('change', () => { state.selectedModel = el.modelSelect.value; });
        el.hideTier3.addEventListener('change', renderResults);
        el.reasonForVisit.addEventListener('keydown', (e) => {
            if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) runAnalysis();
        });
        document.addEventListener('keydown', (e) => { if (e.key === 'Escape') closeAllModals(); });

        checkOllamaStatus();
    }

    // ==============================
    // Ollama status & models
    // ==============================
    async function checkOllamaStatus() {
        setStatus('unknown', 'Checking Ollama…');
        try {
            const res = await fetch('./api/ollama/status', { headers: { 'Accept': 'application/json' } });
            const data = await res.json();
            if (data.available) {
                const count = (data.models || []).length;
                setStatus('online', `Ollama online · ${count} model${count === 1 ? '' : 's'}`);
                populateModels(data.models || [], data.defaultModel);
            } else {
                setStatus('offline', 'Ollama unreachable');
                populateModels([], data.defaultModel);
                showError(data.error || 'Cannot reach the local Ollama instance. Start it with "ollama serve".');
            }
        } catch (e) {
            setStatus('offline', 'Ollama unreachable');
            populateModels([], 'qwen3.5:9b');
            showError('Failed to query Ollama status: ' + e.message);
        }
    }

    function setStatus(kind, text) {
        el.ollamaDot.className = 'status-dot status-' + kind;
        el.ollamaStatusText.textContent = text;
    }

    function populateModels(models, defaultModel) {
        // Collect all available models, merging detected ones with requested ones
        const desiredModels = ['qwen', 'qwen3.5:9b', 'qwen3.5:0.8b'];
        const mergedModels = [...models];
        desiredModels.forEach(dm => {
            if (!mergedModels.includes(dm)) {
                mergedModels.push(dm);
            }
        });

        state.models = mergedModels;
        el.modelSelect.innerHTML = '';

        // Determine the preferred default selection
        // First choice: defaultModel if it's one of the merged models,
        // otherwise prefer 'qwen3.5:9b', then stable 'qwen', then others.
        let preferred = defaultModel;
        if (!preferred || !mergedModels.includes(preferred)) {
            if (mergedModels.includes('qwen3.5:9b')) {
                preferred = 'qwen3.5:9b';
            } else if (mergedModels.includes('qwen')) {
                preferred = 'qwen';
            } else {
                preferred = mergedModels.find(m => /qwen/i.test(m)) || mergedModels[0] || '';
            }
        }

        mergedModels.forEach(m => {
            const opt = document.createElement('option');
            opt.value = m;
            const isInstalled = models.includes(m);
            opt.textContent = m + (isInstalled ? '' : ' (not pulled)');
            if (m === preferred) opt.selected = true;
            el.modelSelect.appendChild(opt);
        });

        state.selectedModel = el.modelSelect.value;
    }

    // ==============================
    // Phase 1 & 2: fetch + normalize, then rank
    // ==============================
    async function loadDemo() {
        el.patientId.value = DEMO.patientId;
        el.reasonForVisit.value = DEMO.reason;
        state.reason = DEMO.reason;

        hideError();
        showLoading('Querying MHD registry for Marie Janssens (Demo Case)…');
        try {
            const bundle = await fetchDocumentReferences(DEMO.patientId);
            const normalized = normalizeBundle(bundle);
            if (normalized && normalized.length > 0) {
                await rankStudies(normalized, DEMO.reason);
                showToast('Successfully queried Marie Janssens from live MHD / MADO backend!', 'success');
                return;
            }
        } catch (e) {
            console.warn('Live MHD query failed, using offline backup demo studies:', e);
        }

        await rankStudies(DEMO.studies.map(s => ({ ...s, _raw: null })), DEMO.reason);
        showToast('Loaded Marie Janssens (Offline Backup Demo Data)', 'info');
    }

    async function runAnalysis() {
        const patientId = el.patientId.value.trim();
        const reason = el.reasonForVisit.value.trim();

        if (!reason) { showError('Please enter a Reason for Visit.'); return; }
        if (!patientId) { showError('Please enter a Patient ID (or click "Load Demo").'); return; }

        state.reason = reason;
        hideError();
        showLoading('Querying MHD registry for imaging priors…');

        let normalized;
        try {
            const bundle = await fetchDocumentReferences(patientId);
            normalized = normalizeBundle(bundle);
        } catch (e) {
            showError(`Failed to fetch documents from the MHD registry: ${e.message}`);
            renderEmpty();
            return;
        }

        if (!normalized || normalized.length === 0) {
            renderEmpty('No imaging DocumentReferences found for this patient.');
            return;
        }

        await rankStudies(normalized, reason);
    }

    async function fetchDocumentReferences(patientId) {
        const params = new URLSearchParams();
        params.append('patient.identifier', patientId);
        const baseUrl = state.config.fhirEndpoint || './fhir';
        const url = `${baseUrl}/DocumentReference?${params.toString()}`;
        const response = await fetch(url, { headers: { 'Accept': 'application/fhir+json' } });
        if (!response.ok) throw new Error(`HTTP ${response.status}: ${response.statusText}`);
        return response.json();
    }

    function normalizeBundle(bundle) {
        const entries = (bundle && bundle.entry) ? bundle.entry : [];
        const studyMap = new Map();
        
        for (const entry of entries) {
            const doc = entry.resource;
            if (!doc || doc.resourceType !== 'DocumentReference') continue;
            
            const normalized = normalizeDoc(doc);
            let uid = normalized.studyUid || normalized.id;
            if (uid && uid.startsWith('urn:oid:')) uid = uid.substring(8);
            
            // Format check
            const formatCode = doc.content?.[0]?.format?.code || '';
            const contentType = doc.content?.[0]?.attachment?.contentType || '';
            const profiles = doc.meta?.profile || [];
            const isKos = (formatCode === '1.2.840.10008.5.1.4.1.1.88.59' ||
                           contentType === 'application/dicom' ||
                           profiles.some(p => p.includes('MadoDicomKosDocumentReference')));
            
            normalized.isKos = isKos;
            normalized.studyUid = uid; // unified

            if (!studyMap.has(uid)) {
                studyMap.set(uid, []);
            }
            studyMap.get(uid).push(normalized);
        }

        const out = [];
        for (const [uid, docs] of studyMap.entries()) {
            if (docs.length === 1) {
                out.push(docs[0]);
            } else {
                // We have multiple versions. Prefer the FHIR version (not 'isKos'), but merge information.
                const fhirDoc = docs.find(d => !d.isKos);
                const kosDoc = docs.find(d => d.isKos);
                
                const primary = fhirDoc || kosDoc || docs[0];
                if (fhirDoc && kosDoc) {
                    primary.hasKosVersion = true;
                    if (!primary.bodySite && kosDoc.bodySite) {
                        primary.bodySite = kosDoc.bodySite;
                    }
                }
                out.push(primary);
            }
        }
        return out;
    }

    function normalizeDoc(doc) {
        // Modality (R5 extension on DocumentReference)
        let modality = '';
        const modalityExt = (doc.extension || []).find(e =>
            e.url === 'http://hl7.org/fhir/5.0/StructureDefinition/extension-DocumentReference.modality');
        if (modalityExt?.valueCodeableConcept?.coding?.length) {
            modality = modalityExt.valueCodeableConcept.coding[0].code || '';
        }
        if (!modality) modality = 'OT';

        // Body site (R5 extension)
        let bodySite = '';
        const bodySiteExt = (doc.extension || []).find(e =>
            e.url === 'http://hl7.org/fhir/5.0/StructureDefinition/extension-DocumentReference.bodySite');
        if (bodySiteExt) {
            const conceptExt = (bodySiteExt.extension || []).find(e => e.url === 'concept');
            const cc = conceptExt?.valueCodeableConcept;
            if (cc) bodySite = cc.coding?.[0]?.display || cc.coding?.[0]?.code || cc.text || '';
        }

        const title = doc.description || doc.content?.[0]?.attachment?.title || 'Imaging study';
        const facility = doc.custodian?.display || doc.author?.[0]?.display || '';

        // Best-effort report text / conclusion
        const summary = doc.text?.div ? stripHtml(doc.text.div)
            : (doc.description || title);

        // Manifest retrieval URL for the MADO handoff
        const manifestUrl = doc.content?.[0]?.attachment?.url || '';
        const contentType = doc.content?.[0]?.attachment?.contentType || '';

        let studyUid = doc.masterIdentifier?.value || doc.id;
        const studyUidRelated = (doc.context?.related || []).find(r =>
            r.identifier?.type?.coding?.some(c => c.code === '110180'));
        if (studyUidRelated?.identifier?.value) studyUid = studyUidRelated.identifier.value;

        return {
            id: doc.id || studyUid || ('doc-' + Math.random().toString(36).slice(2, 8)),
            date: doc.date || '',
            modality,
            bodySite: bodySite || '',
            title,
            facility: facility || '',
            summary_or_conclusion: summary || '',
            // extra fields kept locally (not sent to LLM)
            studyUid,
            manifestUrl,
            contentType,
            _raw: doc
        };
    }

    function stripHtml(html) {
        const tmp = document.createElement('div');
        tmp.innerHTML = html;
        return (tmp.textContent || tmp.innerText || '').trim();
    }

    /** Phase 2/3: send slim metadata to Ollama and merge the ranking back. */
    async function rankStudies(normalized, reason) {
        hideError();
        showLoading('Asking Qwen to triage clinical relevance…');

        // Build the slim payload (strip local-only fields and truncate long clinical summaries to stay well within context limit & maximize speed)
        const slim = normalized.map(s => {
            let summary = s.summary_or_conclusion || '';
            if (summary.length > 250) {
                summary = summary.substring(0, 250) + '...';
            }
            return {
                id: s.id,
                date: s.date,
                modality: MODALITY_LABEL[s.modality] || s.modality,
                bodySite: s.bodySite,
                title: s.title,
                facility: s.facility,
                summary_or_conclusion: summary
            };
        });

        let ranking;
        try {
            const res = await fetch('./api/ollama/rank', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' },
                body: JSON.stringify({
                    reasonForVisit: reason,
                    model: state.selectedModel || el.modelSelect.value || '',
                    studies: slim
                })
            });
            const data = await res.json();
            if (!res.ok) throw new Error(data.error || `HTTP ${res.status}`);
            ranking = Array.isArray(data.ranked_studies) ? data.ranked_studies : [];
        } catch (e) {
            showError(`LLM ranking failed: ${e.message}`);
            renderEmpty();
            return;
        }

        // Merge ranking onto the normalized studies (by id)
        const rankById = {};
        ranking.forEach(r => { if (r && r.id != null) rankById[String(r.id)] = r; });

        state.studies = normalized.map(s => {
            const r = rankById[String(s.id)] || {};
            let tier = parseInt(r.tier, 10);
            if (!(tier >= 1 && tier <= 3)) tier = 3;
            return { ...s, tier, clinical_reasoning: r.clinical_reasoning || 'No reasoning returned by the model.' };
        });

        renderResults();
        showToast(`Ranked ${state.studies.length} prior${state.studies.length === 1 ? '' : 's'}`, 'success');
    }

    // ==============================
    // Phase 3: sort & render
    // ==============================
    function sortStudies(list) {
        // Primary: tier ascending (Tier 1 first). Secondary: date descending (newest first).
        return [...list].sort((a, b) => {
            if (a.tier !== b.tier) return a.tier - b.tier;
            const da = a.date ? new Date(a.date).getTime() : 0;
            const db = b.date ? new Date(b.date).getTime() : 0;
            return db - da;
        });
    }

    function renderResults() {
        if (!state.studies.length) { renderEmpty(); return; }

        const hideT3 = el.hideTier3.checked;
        const sorted = sortStudies(state.studies).filter(s => !(hideT3 && s.tier === 3));

        // Stats
        const counts = { 1: 0, 2: 0, 3: 0 };
        state.studies.forEach(s => { counts[s.tier] = (counts[s.tier] || 0) + 1; });
        el.tier1Count.textContent = counts[1] || 0;
        el.tier2Count.textContent = counts[2] || 0;
        el.tier3Count.textContent = counts[3] || 0;
        el.statsPanel.style.display = 'block';

        el.reasonChip.textContent = '🩺 ' + state.reason;
        el.reasonChip.style.display = 'inline-flex';

        if (!sorted.length) {
            renderEmpty('All results are Tier 3 (hidden by filter).');
            return;
        }

        const cards = sorted.map((s, idx) => renderCard(s, idx)).join('');
        el.resultsContent.innerHTML = `<div class="cards-grid">${cards}</div>`;
    }

    function renderCard(s, idx) {
        const tier = TIER_META[s.tier] || TIER_META[3];
        const dateStr = s.date ? new Date(s.date).toLocaleDateString(undefined,
            { year: 'numeric', month: 'short', day: 'numeric' }) : '—';
        const hasManifest = !!s.manifestUrl;

        return `
        <article class="study-card ${tier.cls}">
            <div class="card-tier-rail"></div>
            <div class="card-main">
                <div class="card-top">
                    <span class="tier-badge ${tier.cls}">${tier.badge} ${tier.label}</span>
                    <span class="card-date">${escapeHtml(dateStr)}</span>
                </div>
                <h3 class="card-title">${escapeHtml(s.title || 'Imaging study')}</h3>
                <div class="card-meta">
                    <span class="meta-pill modality">${escapeHtml(s.modality)}</span>
                    ${s.bodySite ? `<span class="meta-pill">📍 ${escapeHtml(s.bodySite)}</span>` : ''}
                    ${s.facility ? `<span class="meta-pill">🏥 ${escapeHtml(s.facility)}</span>` : ''}
                </div>
                ${s.summary_or_conclusion ? `<p class="card-summary">${escapeHtml(s.summary_or_conclusion)}</p>` : ''}
                <div class="card-reasoning">
                    <span class="reasoning-label">🤖 AI relevance</span>
                    <p>${escapeHtml(s.clinical_reasoning)}</p>
                </div>
                <div class="card-actions">
                    <button class="btn btn-primary btn-small" ${hasManifest ? '' : 'disabled title="No manifest URL"'}
                        onclick="ClinicalPrefetcher.openViewer(${idx})">🖼️ Open in Viewer</button>
                    <button class="btn-secondary btn-small" ${hasManifest ? '' : 'disabled'}
                        onclick="ClinicalPrefetcher.prefetch(${idx})">⬇️ Prefetch DICOM</button>
                    <button class="btn-secondary btn-small" onclick="ClinicalPrefetcher.inspect(${idx})">📄 JSON</button>
                </div>
            </div>
        </article>`;
    }

    function visibleStudyByIndex(idx) {
        const hideT3 = el.hideTier3.checked;
        const sorted = sortStudies(state.studies).filter(s => !(hideT3 && s.tier === 3));
        return sorted[idx];
    }

    // ==============================
    // Phase 4: MADO manifest handoff
    // ==============================
    function openViewer(idx) {
        const s = visibleStudyByIndex(idx);
        if (!s || !s.manifestUrl) { showToast('No MADO manifest URL for this study', 'error'); return; }
        const fullUrl = makeFullUrl(s.manifestUrl);
        const ohif = state.config.ohifViewerUrl || DEFAULT_CONFIG.ohifViewerUrl;
        window.open(`${ohif}?manifestUrl=${encodeURIComponent(fullUrl)}`, '_blank');
        showToast('Opening MADO manifest in viewer…', 'info');
    }

    function prefetch(idx) {
        const s = visibleStudyByIndex(idx);
        if (!s || !s.manifestUrl) { showToast('No MADO manifest URL for this study', 'error'); return; }
        const fullUrl = makeFullUrl(s.manifestUrl);
        try {
            sessionStorage.setItem('madoDownloaderData', JSON.stringify({
                url: fullUrl, description: s.title, patientId: el.patientId.value.trim(), studyUid: s.studyUid
            }));
        } catch (e) { /* ignore */ }
        window.open(`./dicom-downloader?manifestUrl=${encodeURIComponent(fullUrl)}`, '_blank');
        showToast('Handing MADO manifest to the DICOM prefetch engine…', 'info');
    }

    function inspect(idx) {
        const s = visibleStudyByIndex(idx);
        if (!s) return;
        const data = s._raw || {
            id: s.id, date: s.date, modality: s.modality, bodySite: s.bodySite,
            title: s.title, facility: s.facility, summary_or_conclusion: s.summary_or_conclusion,
            tier: s.tier, clinical_reasoning: s.clinical_reasoning
        };
        el.docJsonContent.textContent = JSON.stringify(data, null, 2);
        openModal('docJsonModal');
    }

    function makeFullUrl(url) {
        return url.startsWith('http') ? url : window.location.origin + '/' + url.replace(/^\.\//, '');
    }

    // ==============================
    // Settings
    // ==============================
    function loadConfig() {
        try {
            const saved = localStorage.getItem(CONFIG_KEY);
            if (saved) state.config = { ...DEFAULT_CONFIG, ...JSON.parse(saved) };
        } catch (e) { /* ignore */ }
    }

    function openSettings() {
        el.fhirEndpoint.value = state.config.fhirEndpoint || '';
        el.ohifViewerUrl.value = state.config.ohifViewerUrl || DEFAULT_CONFIG.ohifViewerUrl;
        openModal('settingsModal');
    }

    function saveSettings() {
        state.config = {
            fhirEndpoint: el.fhirEndpoint.value.trim(),
            ohifViewerUrl: el.ohifViewerUrl.value.trim() || DEFAULT_CONFIG.ohifViewerUrl
        };
        try { localStorage.setItem(CONFIG_KEY, JSON.stringify(state.config)); } catch (e) { /* ignore */ }
        closeModal('settingsModal');
        showToast('Settings saved', 'success');
    }

    // ==============================
    // UI helpers
    // ==============================
    function showLoading(msg) {
        el.resultsContent.innerHTML = `
            <div class="loading-block">
                <div class="spinner"></div>
                <p>${escapeHtml(msg || 'Working…')}</p>
            </div>`;
        el.statsPanel.style.display = 'none';
    }

    function renderEmpty(msg) {
        el.resultsContent.innerHTML = `
            <div class="empty-state">
                <div class="empty-state-icon">🩻</div>
                <div class="empty-state-title">${escapeHtml(msg || 'No results')}</div>
                <div class="empty-state-text">Enter a Patient ID and Reason for Visit, then click <strong>Fetch &amp; Rank</strong>, or click <strong>Load Demo</strong>.</div>
            </div>`;
    }

    function showError(msg) {
        el.errorBanner.textContent = '⚠️ ' + msg;
        el.errorBanner.style.display = 'block';
    }
    function hideError() { el.errorBanner.style.display = 'none'; }

    function openModal(id) {
        const m = document.getElementById(id);
        if (m) { m.style.display = 'flex'; document.body.style.overflow = 'hidden'; }
    }
    function closeModal(id) {
        const m = document.getElementById(id);
        if (m) { m.style.display = 'none'; document.body.style.overflow = ''; }
    }
    function closeAllModals() { ['settingsModal', 'docJsonModal'].forEach(closeModal); }

    function showToast(message, type = 'info') {
        const container = document.getElementById('toastContainer');
        if (!container) return;
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

    function escapeHtml(str) {
        if (str == null) return '';
        return String(str)
            .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
    }

    // Public API
    return {
        init,
        saveSettings,
        closeModal,
        openViewer,
        prefetch,
        inspect
    };
})();

document.addEventListener('DOMContentLoaded', ClinicalPrefetcher.init);

