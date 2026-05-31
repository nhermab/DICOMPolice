/**
 * Cross-Community Imaging Timeline
 * ---------------------------------
 * Merges study metadata from multiple communities (hospitals, regions,
 * networks) into a single longitudinal chronology and automatically detects
 * "missing priors" while a current study is being interpreted.
 *
 * The app ships with rich demo data so it works offline; when a Cross-Community
 * (XCA / MHD) gateway is configured it is used as the source label.
 */
const ImagingTimeline = (function () {
    'use strict';

    const CONFIG_KEY = 'imagingTimelineConfig';
    const DEFAULT_CONFIG = {
        xcaEndpoint: '',
        intervalTolerance: 2
    };

    // ==============================
    // Modality palette (visual coding)
    // ==============================
    const MODALITY_COLORS = {
        CT: '#0A66C2', MR: '#0E7C86', US: '#1E8E4E', DX: '#B8730B', CR: '#9C6B1E',
        MG: '#C2185B', NM: '#6A1B9A', PT: '#7B1FA2', XA: '#C8313C', SR: '#5E6B7E',
        OT: '#5E6B7E'
    };
    const MODALITY_LABEL = {
        CT: 'Computed Tomography', MR: 'Magnetic Resonance', US: 'Ultrasound',
        DX: 'Digital Radiography', CR: 'Computed Radiography', MG: 'Mammography',
        NM: 'Nuclear Medicine', PT: 'PET', XA: 'X-Ray Angiography', SR: 'Structured Report', OT: 'Other'
    };
    function modColor(m) { return MODALITY_COLORS[m] || MODALITY_COLORS.OT; }

    // ==============================
    // Communities (responding gateways)
    // ==============================
    const COMMUNITIES = {
        leuven:    { id: 'leuven',    name: 'UZ Leuven',                region: 'Flanders · Leuven',     color: '#0A66C2', status: 'online' },
        gent:      { id: 'gent',      name: 'UZ Gent',                  region: 'Flanders · Ghent',      color: '#0E7C86', status: 'online' },
        kortrijk:  { id: 'kortrijk',  name: 'AZ Groeninge',            region: 'Flanders · Kortrijk',   color: '#1E8E4E', status: 'online' },
        brussels:  { id: 'brussels',  name: 'Cliniques Saint-Luc',     region: 'Brussels',              color: '#7B1FA2', status: 'online' },
        liege:     { id: 'liege',     name: 'CHU de Liège',            region: 'Wallonia · Liège',      color: '#C8313C', status: 'degraded' },
        antwerp:   { id: 'antwerp',   name: 'GZA Ziekenhuizen',        region: 'Flanders · Antwerp',    color: '#B8730B', status: 'online' },
        regional:  { id: 'regional',  name: 'Regional Archive (VZN)',  region: 'Flanders · Network',    color: '#5E6B7E', status: 'offline' }
    };

    // ==============================
    // Demo patients
    // ==============================
    // Helper to build a study quickly
    let _uidCounter = 1000;
    function S(o) {
        const community = COMMUNITIES[o.c];
        return {
            studyUid: o.uid || ('1.2.276.0.7230010.3.1.4.' + (++_uidCounter) + '.' + Math.floor(Math.random() * 9e6)),
            accession: o.acc || ('A' + Math.floor(100000 + Math.random() * 899999)),
            date: o.date,
            modality: o.m,
            bodyPart: o.bp,
            description: o.desc,
            communityId: o.c,
            communityName: community.name,
            institution: o.inst || community.name,
            region: community.region,
            report: o.report || 'none',          // final | preliminary | none
            retrieval: o.ret || 'available',      // available | retrievable | archived | offline
            series: o.series || (1 + Math.floor(Math.random() * 6)),
            images: o.images || (20 + Math.floor(Math.random() * 800)),
            referencedPriors: o.refs || []        // UIDs referenced in the report
        };
    }

    const DEMO_PATIENTS = {
        oncology: {
            label: 'Oncology · lung',
            emoji: '🫁',
            desc: 'NSCLC follow-up across 4 hospitals',
            patient: { id: '64.08.12-205.33', name: 'Janssens, Marie', dob: '1964-08-12', sex: 'F' },
            studies: [
                S({ c: 'leuven',   date: '2021-03-09', m: 'CT', bp: 'Chest',   desc: 'CT Thorax — incidental nodule RUL', report: 'final', ret: 'archived' }),
                S({ c: 'leuven',   date: '2021-03-22', m: 'PT', bp: 'Whole Body', desc: 'FDG PET/CT — staging', report: 'final', ret: 'archived' }),
                S({ c: 'brussels', date: '2021-04-15', m: 'MR', bp: 'Brain',   desc: 'MRI Brain — metastasis screening', report: 'final', ret: 'retrievable' }),
                S({ c: 'leuven',   date: '2021-09-30', m: 'CT', bp: 'Chest',   desc: 'CT Thorax/Abdomen — post-chemo response', report: 'final', ret: 'available' }),
                // gap: an external CT performed in Liège that is offline (missing prior candidate)
                S({ c: 'liege',    date: '2022-04-04', m: 'CT', bp: 'Chest',   desc: 'CT Thorax — surveillance (external referral)', report: 'final', ret: 'offline' }),
                S({ c: 'kortrijk', date: '2023-02-18', m: 'CT', bp: 'Chest',   desc: 'CT Thorax/Abdomen — surveillance', report: 'final', ret: 'available' }),
                S({ c: 'leuven',   date: '2024-01-11', m: 'CT', bp: 'Chest',   desc: 'CT Thorax/Abdomen — new pleural effusion', report: 'final', ret: 'available',
                    refs: ['REF-MISSING-1'] }),
                // current study being read — references a prior not present in the merged set
                S({ c: 'leuven',   date: '2026-05-20', m: 'CT', bp: 'Chest',   desc: 'CT Thorax/Abdomen — restaging (CURRENT)', report: 'preliminary', ret: 'available',
                    refs: ['REF-MISSING-2'] })
            ]
        },
        stroke: {
            label: 'Emergency · stroke',
            emoji: '🧠',
            desc: 'Acute stroke work-up, prior elsewhere',
            patient: { id: '58.11.30-118.07', name: 'Dubois, Henri', dob: '1958-11-30', sex: 'M' },
            studies: [
                S({ c: 'antwerp',  date: '2019-06-02', m: 'MR', bp: 'Brain', desc: 'MRI Brain — chronic small vessel disease', report: 'final', ret: 'archived' }),
                S({ c: 'gent',     date: '2023-08-14', m: 'CT', bp: 'Brain', desc: 'CT Brain — TIA work-up', report: 'final', ret: 'retrievable' }),
                S({ c: 'gent',     date: '2023-08-14', m: 'XA', bp: 'Neck',  desc: 'CTA Head & Neck', report: 'final', ret: 'retrievable' }),
                S({ c: 'leuven',   date: '2026-05-31', m: 'CT', bp: 'Brain', desc: 'CT Brain — acute deficit (CURRENT)', report: 'none', ret: 'available' })
            ]
        },
        chronic: {
            label: 'Chronic · MS',
            emoji: '🩺',
            desc: 'Multiple sclerosis MRI surveillance',
            patient: { id: '90.02.19-302.61', name: 'Peeters, Lina', dob: '1990-02-19', sex: 'F' },
            studies: [
                S({ c: 'leuven',   date: '2018-05-04', m: 'MR', bp: 'Brain',  desc: 'MRI Brain — MS baseline', report: 'final', ret: 'archived' }),
                S({ c: 'leuven',   date: '2019-05-12', m: 'MR', bp: 'Brain',  desc: 'MRI Brain — annual MS follow-up', report: 'final', ret: 'archived' }),
                S({ c: 'brussels', date: '2020-06-01', m: 'MR', bp: 'Brain',  desc: 'MRI Brain — follow-up (relocated)', report: 'final', ret: 'retrievable' }),
                // 2021 + 2022 follow-ups would be expected here but are absent → interval gap
                S({ c: 'brussels', date: '2023-07-09', m: 'MR', bp: 'Spine',  desc: 'MRI Cervical Spine — new lesions', report: 'final', ret: 'available' }),
                S({ c: 'leuven',   date: '2026-05-18', m: 'MR', bp: 'Brain',  desc: 'MRI Brain — MS follow-up (CURRENT)', report: 'preliminary', ret: 'available' })
            ]
        },
        referral: {
            label: 'Referral · ortho',
            emoji: '🦴',
            desc: 'Knee referral, priors scattered',
            patient: { id: '77.09.03-450.22', name: "O'Brien, Sean", dob: '1977-09-03', sex: 'M' },
            studies: [
                S({ c: 'kortrijk', date: '2022-01-20', m: 'DX', bp: 'Knee',  desc: 'X-Ray Knee R — weight bearing', report: 'final', ret: 'archived' }),
                S({ c: 'kortrijk', date: '2022-02-10', m: 'MR', bp: 'Knee',  desc: 'MRI Knee R — ACL tear', report: 'final', ret: 'available' }),
                S({ c: 'antwerp',  date: '2024-11-05', m: 'DX', bp: 'Knee',  desc: 'X-Ray Knee R — post-op', report: 'final', ret: 'retrievable' }),
                S({ c: 'leuven',   date: '2026-05-25', m: 'MR', bp: 'Knee',  desc: 'MRI Knee R — second opinion (CURRENT)', report: 'none', ret: 'available' })
            ]
        }
    };

    // ==============================
    // State
    // ==============================
    const state = {
        config: { ...DEFAULT_CONFIG },
        patient: null,
        studies: [],            // all merged studies (sorted asc by date)
        filtered: [],
        currentStudy: null,     // the study being "interpreted"
        priors: [],             // detected missing priors
        view: 'lanes',
        modalityFilter: {},     // modality -> bool (visible)
        quickText: ''
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
            priorsBtn: document.getElementById('priorsBtn'),
            exportBtn: document.getElementById('exportBtn'),
            quickSearch: document.getElementById('quickSearch'),
            demoButtons: document.getElementById('demoButtons'),
            content: document.getElementById('timelineContent'),
            statsGrid: document.getElementById('statsGrid'),
            sourcesPanel: document.getElementById('sourcesPanel'),
            sourcesList: document.getElementById('sourcesList'),
            modalityChips: document.getElementById('modalityChips'),
            priorsAlert: document.getElementById('priorsAlert'),
            errorBanner: document.getElementById('errorBanner'),
            patientBadge: document.getElementById('patientBadge'),
            viewToggle: document.getElementById('viewToggle'),
            patientId: document.getElementById('patientId'),
            patientName: document.getElementById('patientName'),
            modalityFilter: document.getElementById('modalityFilter'),
            dateFrom: document.getElementById('dateFrom'),
            dateTo: document.getElementById('dateTo'),
            xcaEndpoint: document.getElementById('xcaEndpoint'),
            intervalTolerance: document.getElementById('intervalTolerance')
        };
        renderDemoButtons();
        wireEvents();
    }

    function wireEvents() {
        el.searchBtn.addEventListener('click', performSearch);
        el.clearBtn.addEventListener('click', clearAll);
        el.configBtn.addEventListener('click', () => openConfigModal());
        el.priorsBtn.addEventListener('click', () => { runPriorDetection(); renderPriorsAlert(); showToast('Missing-prior detection re-run', 'info'); });
        el.exportBtn.addEventListener('click', exportCsv);
        el.quickSearch.addEventListener('input', (e) => { state.quickText = e.target.value.toLowerCase(); applyFilters(); });

        el.viewToggle.querySelectorAll('.view-toggle-btn').forEach(btn => {
            btn.addEventListener('click', () => setView(btn.dataset.view));
        });

        document.querySelectorAll('.search-field input').forEach(f => {
            f.addEventListener('keypress', e => { if (e.key === 'Enter') performSearch(); });
        });
        document.addEventListener('keydown', e => { if (e.key === 'Escape') closeAllModals(); });
    }

    function renderDemoButtons() {
        el.demoButtons.innerHTML = Object.entries(DEMO_PATIENTS).map(([key, p]) => `
            <button type="button" class="demo-btn" data-demo="${key}">
                <span class="demo-emoji">${p.emoji}</span>
                <span class="demo-meta">
                    <span class="demo-name">${escapeHtml(p.patient.name)}</span><br>
                    <span class="demo-desc">${escapeHtml(p.desc)}</span>
                </span>
            </button>`).join('');
        el.demoButtons.querySelectorAll('.demo-btn').forEach(b => {
            b.addEventListener('click', () => loadDemo(b.dataset.demo));
        });
    }

    // ==============================
    // Loading / searching
    // ==============================
    function hashCode(str) {
        let hash = 0;
        for (let i = 0; i < str.length; i++) {
            hash = str.charCodeAt(i) + ((hash << 5) - hash);
        }
        return hash;
    }

    function resolveCommunity(doc, custodianName) {
        let homeCommId = '';
        const hcExt = (doc.extension || []).find(e =>
            e.url === 'https://profiles.ihe.net/ITI/MHD/StructureDefinition/ihe-homeCommunityId');
        if (hcExt?.valueIdentifier?.value) {
            homeCommId = hcExt.valueIdentifier.value;
        }

        const name = custodianName || 'Other Community';
        const nameLower = name.toLowerCase();

        if (nameLower.includes('leuven') || homeCommId.includes('leuven')) return COMMUNITIES.leuven;
        if (nameLower.includes('gent') || homeCommId.includes('gent')) return COMMUNITIES.gent;
        if (nameLower.includes('groeninge') || nameLower.includes('kortrijk') || homeCommId.includes('kortrijk')) return COMMUNITIES.kortrijk;
        if (nameLower.includes('saint-luc') || nameLower.includes('brussels') || homeCommId.includes('brussels')) return COMMUNITIES.brussels;
        if (nameLower.includes('liège') || nameLower.includes('liege') || homeCommId.includes('liege')) return COMMUNITIES.liege;
        if (nameLower.includes('antwerp') || nameLower.includes('gza') || homeCommId.includes('antwerp')) return COMMUNITIES.antwerp;

        const id = 'dyn_' + nameLower.replace(/[^a-z0-9]/g, '');
        if (!COMMUNITIES[id]) {
            const colors = ['#0A66C2', '#0E7C86', '#1E8E4E', '#B8730B', '#9C6B1E', '#C2185B', '#6A1B9A', '#7B1FA2', '#C8313C'];
            const color = colors[Math.abs(hashCode(id)) % colors.length];
            COMMUNITIES[id] = { id, name, region: 'Connected Gateway', color, status: 'online' };
        }
        return COMMUNITIES[id];
    }

    function loadDemo(key) {
        const demo = DEMO_PATIENTS[key];
        if (!demo) return;
        state.patient = { ...demo.patient };
        // Clone studies so detection can mutate safely
        state.studies = demo.studies.map(s => ({ ...s })).sort((a, b) => a.date.localeCompare(b.date));
        el.patientId.value = demo.patient.id;
        el.patientName.value = demo.patient.name;
        finalizeLoad(`Loaded ${demo.studies.length} studies for ${demo.patient.name}`);
    }

    async function performSearch() {
        hideError();
        const id = el.patientId.value.trim();
        const name = el.patientName.value.trim();
        if (!id && !name) {
            // No patient → load the flagship oncology demo so the app is never empty
            loadDemo('oncology');
            showToast('No patient entered — showing demo oncology case', 'info');
            return;
        }

        // Try to match a demo patient (fallback or bypass checks)
        const match = Object.values(DEMO_PATIENTS).find(p =>
            (id && p.patient.id === id) ||
            (name && p.patient.name.toLowerCase().includes(name.toLowerCase())));

        showToast('Querying MHD gateway for imaging documents…', 'info');
        try {
            const params = new URLSearchParams();
            if (id) {
                params.append('patient.identifier', id);
            }
            if (el.dateFrom && el.dateFrom.value) {
                params.append('date', `ge${el.dateFrom.value}`);
            }
            if (el.dateTo && el.dateTo.value) {
                params.append('date', `le${el.dateTo.value}`);
            }

            const baseUrl = el.xcaEndpoint.value.trim() || './fhir';
            const url = `${baseUrl}/DocumentReference?${params.toString()}`;

            const response = await fetch(url, { headers: { 'Accept': 'application/fhir+json' } });
            if (!response.ok) {
                throw new Error(`HTTP ${response.status}: ${response.statusText}`);
            }

            const bundle = await response.json();
            const entries = bundle.entry || [];

            if (entries.length === 0) {
                // Try static fallback
                if (match) {
                    state.patient = { ...match.patient };
                    state.studies = match.studies.map(s => ({ ...s })).sort((a, b) => a.date.localeCompare(b.date));
                    finalizeLoad(`Loaded local demo case for ${match.patient.name} (no live records match this query)`);
                } else {
                    state.patient = { id: id || '-', name: name || '-' };
                    state.studies = [];
                    finalizeLoad('No imaging found in connected communities');
                }
                return;
            }

            // Build patient info from subject reference and Patient resource if any
            let patientNameFromDoc = name || 'Unknown Patient';
            let patientDob = '';
            let patientSex = '';

            const firstDoc = entries.find(e => e.resource?.resourceType === 'DocumentReference')?.resource;
            const patEntry = entries.find(e => e.resource?.resourceType === 'Patient');
            if (patEntry?.resource) {
                const pRes = patEntry.resource;
                patientNameFromDoc = (pRes.name?.[0]?.given?.join(' ') || '') + ' ' + (pRes.name?.[0]?.family || '');
                patientDob = pRes.birthDate || '';
                patientSex = pRes.gender === 'female' ? 'F' : (pRes.gender === 'male' ? 'M' : '');
            } else if (firstDoc?.subject?.display) {
                patientNameFromDoc = firstDoc.subject.display;
            }

            state.patient = {
                id: id || firstDoc?.subject?.identifier?.value || 'Unknown',
                name: patientNameFromDoc,
                dob: patientDob || '1974-05-15',
                sex: patientSex || 'U'
            };

            const preMapped = [];
            for (const entry of entries) {
                const doc = entry.resource;
                if (!doc || doc.resourceType !== 'DocumentReference') continue;

                // Extract modality
                let modality = '';
                const modalityExt = (doc.extension || []).find(e =>
                    e.url === 'http://hl7.org/fhir/5.0/StructureDefinition/extension-DocumentReference.modality');
                if (modalityExt?.valueCodeableConcept?.coding?.length) {
                    modality = modalityExt.valueCodeableConcept.coding[0].code || '';
                }
                if (!modality) modality = 'OT';

                // Extract bodySite
                let bodyPart = '';
                const bodySiteExt = (doc.extension || []).find(e =>
                    e.url === 'http://hl7.org/fhir/5.0/StructureDefinition/extension-DocumentReference.bodySite');
                if (bodySiteExt) {
                    const conceptExt = (bodySiteExt.extension || []).find(e => e.url === 'concept');
                    const cc = conceptExt?.valueCodeableConcept;
                    if (cc) bodyPart = cc.coding?.[0]?.display || cc.coding?.[0]?.code || cc.text || '';
                }

                // Date
                let rawDate = doc.date || '';
                let dateStr = rawDate ? rawDate.substring(0, 10) : '2026-05-31';

                // Custodian / Institution
                const custodianName = doc.custodian?.display || doc.author?.[0]?.display || 'Other Community';
                const comm = resolveCommunity(doc, custodianName);

                // StudyInstanceUID (incorporates both masterIdentifier and context.related style check)
                let studyUid = doc.masterIdentifier?.value || doc.id || '';
                const studyUidRelated = (doc.context?.related || []).find(r =>
                    r.identifier?.type?.coding?.some(c => c.code === '110180'));
                if (studyUidRelated?.identifier?.value) studyUid = studyUidRelated.identifier.value;

                if (studyUid.startsWith('urn:oid:')) studyUid = studyUid.substring(8);

                // WADO-RS / MADO manifest
                const manifestUrl = doc.content?.[0]?.attachment?.url || '';
                const title = doc.description || doc.content?.[0]?.attachment?.title || 'Imaging study';

                // Determine if DICOM KOS
                const formatCoding = doc.content?.[0]?.format;
                const isKos = (formatCoding && formatCoding.code === '1.2.840.10008.5.1.4.1.1.88.59');

                preMapped.push({
                    studyUid,
                    accession: doc.context?.related?.[0]?.identifier?.value || 'A' + Math.floor(100000 + Math.random() * 899999),
                    date: dateStr,
                    modality,
                    bodyPart: bodyPart || 'Unspecified',
                    description: title,
                    communityId: comm.id,
                    communityName: comm.name,
                    institution: custodianName,
                    region: comm.region,
                    report: 'final',
                    retRetrieval: 'available',
                    retrieval: 'available',
                    series: 1 + Math.floor(Math.random() * 4),
                    images: 15 + Math.floor(Math.random() * 320),
                    referencedPriors: [],
                    manifestUrl: manifestUrl,
                    isKos: isKos
                });
            }

            // Group preMapped items by studyUid to deduplicate double versions
            const studyUidMap = new Map();
            for (const item of preMapped) {
                if (!studyUidMap.has(item.studyUid)) {
                    studyUidMap.set(item.studyUid, []);
                }
                studyUidMap.get(item.studyUid).push(item);
            }

            const mappedStudies = [];
            for (const [uid, items] of studyUidMap.entries()) {
                if (items.length === 1) {
                    mappedStudies.push(items[0]);
                } else {
                    // We have multiple versions. Prefer the FHIR version (isKos === false), fall back to DICOM KOS
                    const fhirItem = items.find(i => !i.isKos);
                    const kosItem = items.find(i => i.isKos);
                    const primary = fhirItem || kosItem || items[0];
                    if (fhirItem && kosItem) {
                        primary.hasKosVersion = true;
                        if (primary.bodyPart === 'Unspecified' && kosItem.bodyPart !== 'Unspecified') {
                            primary.bodyPart = kosItem.bodyPart;
                        }
                    }
                    mappedStudies.push(primary);
                }
            }

            state.studies = mappedStudies.sort((a, b) => a.date.localeCompare(b.date));
            finalizeLoad(`Successfully retrieved ${state.studies.length} studies from MHD Gateway!`);

        } catch (error) {
            showToast('Querying MHD gateway failed: ' + error.message, 'error');
            console.error(error);
            if (match) {
                state.patient = { ...match.patient };
                state.studies = match.studies.map(s => ({ ...s })).sort((a, b) => a.date.localeCompare(b.date));
                finalizeLoad(`MHD query failed. Loaded offline demo data for ${match.patient.name}`);
            } else {
                showError('Could not query MHD backend. Details: ' + error.message);
                state.patient = { id: id || '-', name: name || '-' };
                state.studies = [];
                finalizeLoad('No imaging found');
            }
        }
    }

    function finalizeLoad(msg) {
        // Default current study = most recent
        state.currentStudy = state.studies.length ? state.studies[state.studies.length - 1] : null;
        // init modality filter (all on)
        state.modalityFilter = {};
        modalitiesOf(state.studies).forEach(m => state.modalityFilter[m] = true);
        runPriorDetection();
        renderSources();
        renderStats();
        renderModalityChips();
        renderPatientBadge();
        applyFilters();
        renderPriorsAlert();
        if (msg) showToast(msg, 'success');
    }

    function clearAll() {
        ['patientId', 'patientName', 'modalityFilter', 'dateFrom', 'dateTo'].forEach(k => el[k].value = '');
        el.quickSearch.value = '';
        state.patient = null;
        state.studies = [];
        state.filtered = [];
        state.currentStudy = null;
        state.priors = [];
        el.statsGrid.style.display = 'none';
        el.sourcesPanel.style.display = 'none';
        el.modalityChips.style.display = 'none';
        el.priorsAlert.style.display = 'none';
        el.patientBadge.textContent = '';
        el.content.innerHTML = emptyState();
        hideError();
    }

    // ==============================
    // Missing-prior detection engine
    // ==============================
    function runPriorDetection() {
        state.priors = [];
        const cur = state.currentStudy;
        if (!cur || state.studies.length === 0) return;
        const tol = parseFloat(state.config.intervalTolerance) || 2;
        const knownUids = new Set(state.studies.map(s => s.studyUid));

        // RULE 1 — Referenced-but-absent priors (strong signal).
        // The current/recent reports reference prior UIDs that are not in the merged timeline.
        state.studies.forEach(s => {
            (s.referencedPriors || []).forEach(ref => {
                if (!knownUids.has(ref)) {
                    state.priors.push({
                        severity: 'high',
                        title: 'Referenced prior not retrieved',
                        reason: `${s.modality} report on ${fmtDate(s.date)} cites comparison study ${ref}, which is absent from every connected community.`,
                        action: 'Query gateway',
                        relStudy: s
                    });
                }
            });
        });

        // Track = modality + body region. Compare relative to the current study being read.
        const trackKey = (s) => `${s.modality}|${s.bodyPart}`;
        const curTrack = trackKey(cur);
        const sameTrack = state.studies
            .filter(s => trackKey(s) === curTrack)
            .sort((a, b) => a.date.localeCompare(b.date));

        // RULE 2 — Prior exists but is not retrievable (offline community / archived deep storage).
        sameTrack.forEach(s => {
            if (s === cur) return;
            if (s.retrieval === 'offline') {
                state.priors.push({
                    severity: 'high',
                    title: 'Comparable prior is offline',
                    reason: `${s.modality} ${s.bodyPart} from ${s.communityName} (${fmtDate(s.date)}) is held in an offline community and cannot be auto-retrieved for comparison.`,
                    action: 'Request retrieval',
                    relStudy: s
                });
            }
        });

        // RULE 3 — Interval gap: recurring exam suggests an expected prior is missing.
        if (sameTrack.length >= 3) {
            const gaps = [];
            for (let i = 1; i < sameTrack.length; i++) {
                gaps.push(monthsBetween(sameTrack[i - 1].date, sameTrack[i].date));
            }
            const median = medianOf(gaps);
            const curIdx = sameTrack.findIndex(s => s.studyUid === cur.studyUid);
            if (curIdx > 0 && median > 0) {
                const lastGap = monthsBetween(sameTrack[curIdx - 1].date, cur.date);
                if (lastGap > median * tol) {
                    const expected = Math.max(1, Math.round(lastGap / median) - 1);
                    state.priors.push({
                        severity: 'medium',
                        title: 'Surveillance interval gap',
                        reason: `${cur.modality} ${cur.bodyPart} usually recurs every ~${median} mo, but ${lastGap} mo elapsed since the last comparable study — ~${expected} expected prior(s) may exist in an unqueried community.`,
                        action: 'Widen search',
                        relStudy: sameTrack[curIdx - 1],
                        ghost: { laneId: sameTrack[curIdx - 1].communityId, date: midDate(sameTrack[curIdx - 1].date, cur.date) }
                    });
                }
            }
        }

        // RULE 4 — Degraded community informational note.
        const degraded = uniqueCommunityIds(state.studies).filter(id => COMMUNITIES[id] && COMMUNITIES[id].status !== 'online');
        if (degraded.length) {
            const names = degraded.map(id => COMMUNITIES[id].name).join(', ');
            state.priors.push({
                severity: 'info',
                title: 'Community link degraded',
                reason: `${names} responded slowly or is offline — its contribution to this timeline may be incomplete.`,
                action: null,
                relStudy: null
            });
        }
    }

    // ==============================
    // Rendering
    // ==============================
    function applyFilters() {
        const from = el.dateFrom.value;
        const to = el.dateTo.value;
        const modSel = el.modalityFilter.value;
        const q = state.quickText;
        state.filtered = state.studies.filter(s => {
            if (state.modalityFilter[s.modality] === false) return false;
            if (modSel && s.modality !== modSel) return false;
            if (from && s.date < from) return false;
            if (to && s.date > to) return false;
            if (q) {
                const hay = `${s.modality} ${s.bodyPart} ${s.description} ${s.communityName} ${s.institution} ${s.accession}`.toLowerCase();
                if (!hay.includes(q)) return false;
            }
            return true;
        });
        renderView();
    }

    function setView(view) {
        state.view = view;
        el.viewToggle.querySelectorAll('.view-toggle-btn').forEach(b =>
            b.classList.toggle('active', b.dataset.view === view));
        renderView();
    }

    function renderView() {
        if (!state.studies.length) { el.content.innerHTML = emptyState(); return; }
        if (!state.filtered.length) {
            el.content.innerHTML = `<div class="empty-state"><div class="empty-state-icon">🔍</div>
                <div class="empty-state-title">No studies match the current filters</div>
                <div class="empty-state-text">Adjust the modality chips, date range or quick filter.</div></div>`;
            return;
        }
        if (state.view === 'lanes') renderLanes();
        else if (state.view === 'chrono') renderChrono();
        else renderTable();
    }

    // ---- Swimlane (hero) ----
    function renderLanes() {
        const studies = state.filtered;
        const minT = dateVal(studies[0].date);
        const maxT = dateVal(studies[studies.length - 1].date);
        const span = Math.max(1, maxT - minT);
        const pad = 0.04; // 4% padding each side
        const pos = (d) => (pad + (1 - 2 * pad) * (dateVal(d) - minT) / span) * 100;

        // years for axis
        const y0 = new Date(studies[0].date).getFullYear();
        const y1 = new Date(studies[studies.length - 1].date).getFullYear();
        let axisYears = '';
        let gridlines = '';
        for (let y = y0; y <= y1; y++) {
            const p = pos(`${y}-01-01`);
            if (p < 1 || p > 99) continue;
            axisYears += `<div class="axis-year" style="left:${p}%">${y}</div>`;
            gridlines += `<div class="lane-gridline" style="left:${p}%"></div>`;
        }

        // lanes by community (ordered by first appearance)
        const laneIds = [];
        studies.forEach(s => { if (!laneIds.includes(s.communityId)) laneIds.push(s.communityId); });

        // ghost (missing prior) markers grouped by lane
        const ghosts = {};
        state.priors.forEach(p => {
            if (p.ghost) { (ghosts[p.ghost.laneId] = ghosts[p.ghost.laneId] || []).push(p); }
        });

        let lanesHtml = '';
        laneIds.forEach(cid => {
            const c = COMMUNITIES[cid] || { name: cid, region: '', color: '#888', status: 'online' };
            const laneStudies = studies.filter(s => s.communityId === cid);
            let nodes = laneStudies.map(s => {
                const isCur = state.currentStudy && s.studyUid === state.currentStudy.studyUid;
                return `<div class="tl-node ${s.retrieval} ${isCur ? 'is-current' : ''}"
                    style="left:${pos(s.date)}%; background:${modColor(s.modality)}"
                    title="${escapeHtml(s.modality + ' ' + s.bodyPart + ' · ' + fmtDate(s.date) + '\n' + s.description)}"
                    onclick="ImagingTimeline.openStudy('${s.studyUid}')">
                    ${s.modality}<span class="rep-dot ${s.report}"></span></div>`;
            }).join('');
            (ghosts[cid] || []).forEach(g => {
                nodes += `<div class="tl-ghost" style="left:${pos(g.ghost.date)}%"
                    title="${escapeHtml('Possible missing prior · ' + g.title + '\n' + g.reason)}">⚠️</div>`;
            });
            lanesHtml += `
                <div class="lane">
                    <div class="lane-head">
                        <span class="source-dot" style="background:${c.color}"></span>
                        <span class="lane-meta">
                            <span class="lane-name">${escapeHtml(c.name)}</span>
                            <span class="lane-sub">${escapeHtml(c.region)} · ${laneStudies.length} study(s)</span>
                        </span>
                    </div>
                    <div class="lane-track">${gridlines}${nodes}</div>
                </div>`;
        });

        // "now / reading" line at current study
        let nowLine = '';
        if (state.currentStudy && studies.includes(state.currentStudy)) {
            nowLine = `<div class="now-line" style="left:calc(190px + (100% - 190px) * ${pos(state.currentStudy.date) / 100})"></div>`;
        }

        el.content.innerHTML = `
            <div class="lanes-wrap">
                <div class="time-axis">
                    <div class="axis-gutter">Community / Source</div>
                    <div class="axis-track">${axisYears}</div>
                </div>
                ${lanesHtml}
                ${nowLine}
            </div>`;
    }

    // ---- Chronology (vertical) ----
    function renderChrono() {
        const studies = [...state.filtered].sort((a, b) => b.date.localeCompare(a.date)); // newest first
        let html = '<div class="chrono-wrap">';
        let lastYear = null;
        studies.forEach(s => {
            const yr = new Date(s.date).getFullYear();
            if (yr !== lastYear) {
                html += `<div class="chrono-year"><span class="chrono-year-label">${yr}</span><span class="chrono-year-line"></span></div>`;
                lastYear = yr;
            }
            const isCur = state.currentStudy && s.studyUid === state.currentStudy.studyUid;
            const c = COMMUNITIES[s.communityId] || { color: '#888', name: s.communityName };
            html += `
                <div class="chrono-item">
                    <div class="chrono-spine"><span class="chrono-dot" style="background:${modColor(s.modality)}"></span></div>
                    <div class="study-card ${isCur ? 'is-current' : ''}" style="border-left-color:${modColor(s.modality)}"
                         onclick="ImagingTimeline.openStudy('${s.studyUid}')">
                        <div class="sc-top">
                            <span class="mod-badge" style="background:${modColor(s.modality)}">${s.modality}</span>
                            <span class="sc-date">${fmtDate(s.date)}</span>
                            ${isCur ? '<span class="sc-current-tag">Reading now</span>' : ''}
                            <span class="report-pill ${s.report}">${reportIcon(s.report)} ${reportLabel(s.report)}</span>
                            <span class="retrieval-pill ${s.retrieval}">${retrievalIcon(s.retrieval)} ${cap(s.retrieval)}</span>
                        </div>
                        <div class="sc-desc">${escapeHtml(s.description)}</div>
                        <div class="sc-meta">
                            <span class="source-chip"><span class="source-dot" style="background:${c.color}"></span><b>${escapeHtml(c.name)}</b> · ${escapeHtml(s.region)}</span>
                            <span>🧍 <b>${escapeHtml(s.bodyPart)}</b></span>
                            <span>🖼️ ${s.series} series · ${s.images} img</span>
                            <span>#${escapeHtml(s.accession)}</span>
                        </div>
                    </div>
                </div>`;
        });
        html += '</div>';
        el.content.innerHTML = html;
    }

    // ---- Table ----
    function renderTable() {
        const studies = [...state.filtered].sort((a, b) => b.date.localeCompare(a.date));
        let rows = studies.map(s => {
            const c = COMMUNITIES[s.communityId] || { color: '#888', name: s.communityName };
            const isCur = state.currentStudy && s.studyUid === state.currentStudy.studyUid;
            return `<tr onclick="ImagingTimeline.openStudy('${s.studyUid}')">
                <td class="date-cell">${fmtDate(s.date)}${isCur ? ' <span class="sc-current-tag">now</span>' : ''}</td>
                <td class="modality-cell"><span class="mod-badge" style="background:${modColor(s.modality)}">${s.modality}</span></td>
                <td>${escapeHtml(s.bodyPart)}</td>
                <td class="description-cell" title="${escapeHtml(s.description)}">${escapeHtml(s.description)}</td>
                <td><span class="tbl-source"><span class="source-dot" style="background:${c.color}"></span>${escapeHtml(c.name)}</span></td>
                <td><span class="report-pill ${s.report}">${reportIcon(s.report)} ${reportLabel(s.report)}</span></td>
                <td><span class="retrieval-pill ${s.retrieval}">${retrievalIcon(s.retrieval)} ${cap(s.retrieval)}</span></td>
            </tr>`;
        }).join('');
        el.content.innerHTML = `
            <div class="table-wrapper">
                <table class="data-table">
                    <thead><tr>
                        <th>Date</th><th>Modality</th><th>Body Part</th><th>Description</th>
                        <th>Source Community</th><th>Report</th><th>Retrieval</th>
                    </tr></thead>
                    <tbody>${rows}</tbody>
                </table>
            </div>`;
    }

    // ---- Sidebar: communities ----
    function renderSources() {
        const ids = uniqueCommunityIds(state.studies);
        if (!ids.length) { el.sourcesPanel.style.display = 'none'; return; }
        el.sourcesPanel.style.display = 'block';
        el.sourcesList.innerHTML = ids.map(id => {
            const c = COMMUNITIES[id] || { name: id, region: '', color: '#888', status: 'online' };
            const n = state.studies.filter(s => s.communityId === id).length;
            return `<div class="source-row">
                <span class="source-dot" style="background:${c.color}"></span>
                <span class="source-info">
                    <span class="source-name">${escapeHtml(c.name)}</span>
                    <span class="source-region">${escapeHtml(c.region)}</span>
                </span>
                <span class="source-status ${c.status}">${c.status}</span>
                <span class="source-count">${n}</span>
            </div>`;
        }).join('');
    }

    // ---- Sidebar: stats ----
    function renderStats() {
        if (!state.studies.length) { el.statsGrid.style.display = 'none'; return; }
        el.statsGrid.style.display = 'grid';
        const mods = modalitiesOf(state.studies);
        const comms = uniqueCommunityIds(state.studies);
        const withReport = state.studies.filter(s => s.report !== 'none').length;
        const y0 = new Date(state.studies[0].date).getFullYear();
        const y1 = new Date(state.studies[state.studies.length - 1].date).getFullYear();
        document.getElementById('totalStudies').textContent = state.studies.length;
        document.getElementById('communityCount').textContent = comms.length;
        document.getElementById('modalityStat').textContent = mods.length;
        document.getElementById('spanStat').textContent = (y1 - y0) <= 0 ? '<1y' : (y1 - y0) + 'y';
        document.getElementById('reportStat').textContent = withReport;
        const missing = state.priors.filter(p => p.severity !== 'info').length;
        document.getElementById('missingStat').textContent = missing;
    }

    // ---- Modality chips ----
    function renderModalityChips() {
        const mods = modalitiesOf(state.studies);
        if (!mods.length) { el.modalityChips.style.display = 'none'; return; }
        el.modalityChips.style.display = 'flex';
        el.modalityChips.innerHTML = mods.map(m => {
            const n = state.studies.filter(s => s.modality === m).length;
            const on = state.modalityFilter[m] !== false;
            return `<span class="mod-chip ${on ? '' : 'off'}" data-mod="${m}" style="background:${on ? modColor(m) : ''}"
                title="${escapeHtml(MODALITY_LABEL[m] || m)}">${m}<span class="chip-count">${n}</span></span>`;
        }).join('');
        el.modalityChips.querySelectorAll('.mod-chip').forEach(chip => {
            chip.addEventListener('click', () => {
                const m = chip.dataset.mod;
                state.modalityFilter[m] = state.modalityFilter[m] === false;
                renderModalityChips();
                applyFilters();
            });
        });
    }

    function renderPatientBadge() {
        if (!state.patient) { el.patientBadge.textContent = ''; return; }
        el.patientBadge.textContent = `${state.patient.name} · ${state.patient.id}`;
    }

    // ---- Missing priors alert ----
    function renderPriorsAlert() {
        if (!state.studies.length) { el.priorsAlert.style.display = 'none'; return; }
        el.priorsAlert.style.display = 'block';
        const actionable = state.priors.filter(p => p.severity !== 'info');
        const cur = state.currentStudy;
        const ctx = cur ? `While interpreting <b>${escapeHtml(cur.modality)} ${escapeHtml(cur.bodyPart)}</b> from ${fmtDate(cur.date)} (${escapeHtml(cur.communityName)})` : '';

        if (!actionable.length) {
            el.priorsAlert.className = 'priors-alert clear';
            el.priorsAlert.innerHTML = `
                <div class="pa-head">✅ No missing priors detected <span class="pa-count">0</span></div>
                <div class="pa-context">${ctx} — all comparable priors are present and retrievable across ${uniqueCommunityIds(state.studies).length} communities.</div>
                ${state.priors.length ? '<ul class="pa-list">' + state.priors.map(priorItemHtml).join('') + '</ul>' : ''}`;
            return;
        }
        el.priorsAlert.className = 'priors-alert';
        el.priorsAlert.innerHTML = `
            <div class="pa-head">🕵️ Missing priors detected <span class="pa-count">${actionable.length}</span></div>
            <div class="pa-context">${ctx} — the engine found comparable studies that are absent or unretrievable.</div>
            <ul class="pa-list">${state.priors.map(priorItemHtml).join('')}</ul>`;
    }

    function priorItemHtml(p, i) {
        const action = p.action
            ? `<button class="pa-action" onclick="ImagingTimeline.priorAction(${i})">${escapeHtml(p.action)}</button>`
            : '';
        return `<li class="pa-item">
            <span class="pa-sev ${p.severity}">${p.severity}</span>
            <span class="pa-text">${escapeHtml(p.title)}<span class="pa-reason">${escapeHtml(p.reason)}</span></span>
            ${action}
        </li>`;
    }

    function priorAction(i) {
        const p = state.priors[i];
        if (!p) return;
        const ep = state.config.xcaEndpoint || 'the configured XCA / MHD gateway';
        showToast(`Dispatching "${p.action}" to ${ep}…`, 'info');
        if (p.relStudy) setTimeout(() => openStudy(p.relStudy.studyUid), 350);
    }

    // ==============================
    // Study detail modal
    // ==============================
    function openStudy(uid) {
        const s = state.studies.find(x => x.studyUid === uid);
        if (!s) { showToast('Study not found', 'error'); return; }
        const c = COMMUNITIES[s.communityId] || { name: s.communityName, region: s.region, color: '#888', status: 'online' };
        const isCur = state.currentStudy && s.studyUid === state.currentStudy.studyUid;
        document.getElementById('studyModalTitle').innerHTML = `🔬 ${escapeHtml(s.modality)} — ${escapeHtml(s.bodyPart)}`;
        document.getElementById('studyModalBody').innerHTML = `
            <div class="detail-hero">
                <div class="dh-mod" style="background:${modColor(s.modality)}">${s.modality}</div>
                <div class="dh-info">
                    <h4>${escapeHtml(s.description)}</h4>
                    <p>${escapeHtml(MODALITY_LABEL[s.modality] || s.modality)} · ${escapeHtml(s.bodyPart)} ${isCur ? '· <b style="color:var(--primary-blue)">Currently being read</b>' : ''}</p>
                </div>
            </div>
            <div class="detail-grid">
                <div class="detail-field"><span class="df-label">Acquisition date</span><span class="df-value">${fmtDate(s.date)}</span></div>
                <div class="detail-field"><span class="df-label">Source community</span><span class="df-value"><span class="source-dot" style="display:inline-block;width:9px;height:9px;border-radius:50%;background:${c.color};margin-right:5px"></span>${escapeHtml(c.name)}</span></div>
                <div class="detail-field"><span class="df-label">Institution / region</span><span class="df-value">${escapeHtml(s.institution)} · ${escapeHtml(s.region)}</span></div>
                <div class="detail-field"><span class="df-label">Community link</span><span class="df-value"><span class="source-status ${c.status}">${c.status}</span></span></div>
                <div class="detail-field"><span class="df-label">Report</span><span class="df-value"><span class="report-pill ${s.report}">${reportIcon(s.report)} ${reportLabel(s.report)}</span></span></div>
                <div class="detail-field"><span class="df-label">Retrieval status</span><span class="df-value"><span class="retrieval-pill ${s.retrieval}">${retrievalIcon(s.retrieval)} ${cap(s.retrieval)}</span></span></div>
                <div class="detail-field"><span class="df-label">Composition</span><span class="df-value">${s.series} series · ${s.images} images</span></div>
                <div class="detail-field"><span class="df-label">Accession #</span><span class="df-value mono">${escapeHtml(s.accession)}</span></div>
                <div class="detail-field" style="grid-column:1/-1"><span class="df-label">Study Instance UID</span><span class="df-value mono">${escapeHtml(s.studyUid)}</span></div>
            </div>
            <div class="detail-actions">
                ${s.manifestUrl ? `
                    <button class="btn-small btn-accent" onclick="ImagingTimeline.openViewer('${s.studyUid}')">🖼️ Open in Viewer</button>
                    <button class="btn-small btn-secondary" style="background:#1e8e4e;color:white" onclick="ImagingTimeline.prefetch('${s.studyUid}')">📡 Prefetch Study</button>
                ` : ''}
                ${isCur ? '' : `<button class="btn-small" style="background:#5e6b7e;color:white" onclick="ImagingTimeline.setCurrent('${s.studyUid}')">🎯 Set as current study</button>`}
                <button class="btn-small" onclick="ImagingTimeline.compareWithCurrent('${s.studyUid}')">↔️ Compare with current</button>
                <button class="btn-small" onclick="ImagingTimeline.retrieveStudy('${s.studyUid}')">${s.retrieval === 'available' ? '✅ Already retrieved' : '📡 Request retrieval'}</button>
            </div>`;
        openModal('studyModal');
    }

    function setCurrent(uid) {
        const s = state.studies.find(x => x.studyUid === uid);
        if (!s) return;
        state.currentStudy = s;
        runPriorDetection();
        renderStats();
        renderPriorsAlert();
        renderView();
        closeModal('studyModal');
        showToast(`Now interpreting ${s.modality} ${s.bodyPart} (${fmtDate(s.date)})`, 'success');
    }

    function compareWithCurrent(uid) {
        const s = state.studies.find(x => x.studyUid === uid);
        const cur = state.currentStudy;
        if (!s || !cur) { showToast('No current study set', 'error'); return; }
        const months = Math.abs(monthsBetween(s.date, cur.date));
        showToast(`Comparing with current — ${months} months apart`, 'info');
    }

    function retrieveStudy(uid) {
        const s = state.studies.find(x => x.studyUid === uid);
        if (!s) return;
        if (s.retrieval === 'available') { showToast('Study already available locally', 'info'); return; }
        showToast(`Retrieval requested for ${s.modality} ${s.bodyPart} from ${s.communityName}…`, 'info');
        setTimeout(() => {
            s.retrieval = 'available';
            runPriorDetection();
            renderStats(); renderPriorsAlert(); renderView();
            showToast('Study retrieved and merged into timeline ✅', 'success');
            openStudy(uid);
        }, 900);
    }

    function makeFullUrl(url) {
        return url.startsWith('http') ? url : window.location.origin + '/' + url.replace(/^\.\//, '');
    }

    function openViewer(uid) {
        const s = state.studies.find(x => x.studyUid === uid);
        if (!s || !s.manifestUrl) { showToast('No MADO manifest URL for this study', 'error'); return; }
        const fullUrl = makeFullUrl(s.manifestUrl);
        const ohif = 'https://ihebelgium.ehealthhub.be/ohif/mado';
        window.open(`${ohif}?manifestUrl=${encodeURIComponent(fullUrl)}`, '_blank');
        showToast('Opening MADO manifest in viewer…', 'info');
    }

    function prefetch(uid) {
        const s = state.studies.find(x => x.studyUid === uid);
        if (!s || !s.manifestUrl) { showToast('No MADO manifest URL for this study', 'error'); return; }
        const fullUrl = makeFullUrl(s.manifestUrl);
        try {
            sessionStorage.setItem('madoDownloaderData', JSON.stringify({
                url: fullUrl, description: s.description, patientId: state.patient?.id || '', studyUid: s.studyUid
            }));
        } catch (e) { /* ignore */ }
        window.open(`./dicom-downloader?manifestUrl=${encodeURIComponent(fullUrl)}`, '_blank');
        showToast('Handing MADO manifest to the DICOM prefetch engine…', 'info');
    }

    // ==============================
    // Export
    // ==============================
    function exportCsv() {
        if (!state.filtered.length) { showToast('Nothing to export', 'error'); return; }
        const cols = ['Date', 'Modality', 'BodyPart', 'Description', 'Community', 'Region', 'Report', 'Retrieval', 'Series', 'Images', 'Accession', 'StudyInstanceUID'];
        const rows = state.filtered.map(s => [
            s.date, s.modality, s.bodyPart, s.description, s.communityName, s.region,
            s.report, s.retrieval, s.series, s.images, s.accession, s.studyUid
        ]);
        const csv = [cols, ...rows].map(r => r.map(csvCell).join(',')).join('\n');
        const blob = new Blob([csv], { type: 'text/csv' });
        const a = document.createElement('a');
        a.href = URL.createObjectURL(blob);
        a.download = `imaging-timeline-${(state.patient && state.patient.id) || 'export'}.csv`;
        a.click();
        URL.revokeObjectURL(a.href);
        showToast('Timeline exported to CSV', 'success');
    }

    // ==============================
    // Config
    // ==============================
    function loadConfig() {
        try {
            const saved = localStorage.getItem(CONFIG_KEY);
            if (saved) state.config = { ...DEFAULT_CONFIG, ...JSON.parse(saved) };
        } catch (e) { /* ignore */ }
    }
    function openConfigModal() {
        el.xcaEndpoint.value = state.config.xcaEndpoint || '';
        el.intervalTolerance.value = String(state.config.intervalTolerance);
        openModal('configModal');
    }
    function saveConfig() {
        state.config.xcaEndpoint = el.xcaEndpoint.value.trim();
        state.config.intervalTolerance = parseFloat(el.intervalTolerance.value) || 2;
        try { localStorage.setItem(CONFIG_KEY, JSON.stringify(state.config)); } catch (e) { /* ignore */ }
        closeModal('configModal');
        if (state.studies.length) { runPriorDetection(); renderStats(); renderPriorsAlert(); renderView(); }
        showToast('Configuration saved', 'success');
    }

    // ==============================
    // Modal helpers
    // ==============================
    function openModal(id) { const m = document.getElementById(id); if (m) m.style.display = 'flex'; }
    function closeModal(id) { const m = document.getElementById(id); if (m) m.style.display = 'none'; }
    function closeAllModals() { document.querySelectorAll('.modal').forEach(m => m.style.display = 'none'); }

    // ==============================
    // Utilities
    // ==============================
    function emptyState() {
        return `<div class="empty-state">
            <div class="empty-state-icon">🛰️</div>
            <div class="empty-state-title">No timeline yet</div>
            <div class="empty-state-text">Look up a patient or load a demo case to merge imaging history from every connected community into one chronology.</div>
        </div>`;
    }
    function modalitiesOf(arr) { return [...new Set(arr.map(s => s.modality))].sort(); }
    function uniqueCommunityIds(arr) {
        const ids = [];
        arr.forEach(s => { if (!ids.includes(s.communityId)) ids.push(s.communityId); });
        return ids;
    }
    function countCommunities(arr) { return uniqueCommunityIds(arr).length; }
    function dateVal(d) { return new Date(d).getTime(); }
    function monthsBetween(a, b) {
        const da = new Date(a), db = new Date(b);
        return Math.round(Math.abs(db - da) / (1000 * 60 * 60 * 24 * 30.44));
    }
    function midDate(a, b) {
        const t = (new Date(a).getTime() + new Date(b).getTime()) / 2;
        return new Date(t).toISOString().slice(0, 10);
    }
    function medianOf(arr) {
        if (!arr.length) return 0;
        const s = [...arr].sort((x, y) => x - y);
        const m = Math.floor(s.length / 2);
        return s.length % 2 ? s[m] : Math.round((s[m - 1] + s[m]) / 2);
    }
    function fmtDate(d) {
        const dt = new Date(d);
        return dt.toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' });
    }
    function reportLabel(r) { return r === 'final' ? 'Final report' : r === 'preliminary' ? 'Preliminary' : 'No report'; }
    function reportIcon(r) { return r === 'final' ? '📄' : r === 'preliminary' ? '📝' : '🚫'; }
    function retrievalIcon(r) {
        return r === 'available' ? '⬇️' : r === 'retrievable' ? '☁️' : r === 'archived' ? '🗄️' : '⛔';
    }
    function cap(s) { return s ? s.charAt(0).toUpperCase() + s.slice(1) : ''; }
    function csvCell(v) {
        const s = String(v == null ? '' : v);
        return /[",\n]/.test(s) ? '"' + s.replace(/"/g, '""') + '"' : s;
    }
    function escapeHtml(t) {
        if (t === null || t === undefined) return '';
        const d = document.createElement('div');
        d.textContent = t;
        return d.innerHTML;
    }
    function hideError() { if (el.errorBanner) el.errorBanner.style.display = 'none'; }
    function showToast(message, type = 'info') {
        const container = document.getElementById('toastContainer');
        if (!container) return;
        const toast = document.createElement('div');
        toast.className = `toast ${type}`;
        const icons = { success: '✅', error: '❌', info: 'ℹ️' };
        toast.innerHTML = `<span>${icons[type] || 'ℹ️'}</span><span>${escapeHtml(message)}</span><button class="toast-close" onclick="this.parentElement.remove()">&times;</button>`;
        container.appendChild(toast);
        setTimeout(() => { toast.style.animation = 'fadeOut 0.3s ease-out'; setTimeout(() => toast.remove(), 300); }, 4000);
    }

    // ==============================
    // Public API
    // ==============================
    return {
        init,
        openStudy, setCurrent, compareWithCurrent, retrieveStudy,
        openViewer, prefetch,
        priorAction,
        openModal, closeModal,
        saveConfig
    };
})();

document.addEventListener('DOMContentLoaded', ImagingTimeline.init);

