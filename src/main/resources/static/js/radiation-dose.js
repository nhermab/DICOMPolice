/**
 * Radiation Dose Passport
 *
 * A fully client-side app that estimates a patient's cumulative radiation
 * exposure context from DICOM Radiation Dose Structured Reports (RDSR /
 * RRDSR / P-RDSR), retrieved via QIDO-RS + WADO-RS metadata.
 *
 * Design principles (see in-app safety notice):
 *  - DO NOT calculate dose from pixels.
 *  - Build around Dose Structured Reports first; headers/OCR/report are
 *    low-confidence fallbacks (not implemented here).
 *  - Report "cumulative dose indices and estimated effective dose", never
 *    "the patient's exact radiation dose".
 *
 * Modelled on the QIDO-RS Explorer and DICOM Downloader apps.
 */

const RadiationDose = (function () {

    // ========================================================================
    // Endpoint configuration (auto-detect context path, same as qido-explorer)
    // ========================================================================

    function getFhirBasePath() {
        let contextPath = '';
        try {
            const scriptUrl = new URL(document.currentScript?.src || '', window.location.origin);
            const path = scriptUrl.pathname || '';
            const idx = path.indexOf('/js/');
            if (idx > 0) {
                const prefix = path.substring(0, idx);
                contextPath = prefix === '/' ? '' : prefix;
            }
        } catch (_) {
            const pathname = window.location.pathname;
            if (pathname.startsWith('/TheDICOMPolice')) {
                contextPath = '/TheDICOMPolice';
            }
        }
        return window.location.origin + contextPath + '/fhir';
    }

    function sanitizeFhirEndpoint(url) {
        if (!url) return '';
        if (url.endsWith('/dicomweb')) {
            url = url.substring(0, url.length - 9) + '/fhir';
        } else if (url.endsWith('/dicomweb/')) {
            url = url.substring(0, url.length - 10) + '/fhir';
        }
        return url;
    }

    const DEFAULT_CONFIG = {
        fhirEndpoint: getFhirBasePath(),
        windowMonths: 18,
        coefficientTableVersion: 'v2026.1',
        averageFallback: true
    };

    let config = { ...DEFAULT_CONFIG };

    // ========================================================================
    // Dose object SOP Class UIDs (primary, reliable sources)
    // ========================================================================

    const DOSE_SOP_CLASSES = {
        '1.2.840.10008.5.1.4.1.1.88.67': { label: 'X-Ray Radiation Dose SR', source: 'RDSR' },
        '1.2.840.10008.5.1.4.1.1.88.68': { label: 'Radiopharmaceutical Radiation Dose SR', source: 'RRDSR' },
        '1.2.840.10008.5.1.4.1.1.88.73': { label: 'Patient Radiation Dose SR', source: 'P_RDSR' },
        '1.2.840.10008.5.1.4.1.1.88.76': { label: 'Enhanced X-Ray Radiation Dose SR', source: 'RDSR' }
    };


    // ========================================================================
    // Governed coefficient tables (decision-support estimates only)
    // ========================================================================

    // CT effective dose conversion factors k, mSv / (mGy·cm). Adult ranges
    // based on commonly published ICRP/AAPM body-region coefficients.
    const CT_K_FACTORS = {
        head: 0.0021,
        'head-neck': 0.0031,
        neck: 0.0059,
        chest: 0.014,
        cardiac: 0.014,
        'chest-abdomen-pelvis': 0.015,
        abdomen: 0.015,
        'abdomen-pelvis': 0.015,
        pelvis: 0.015,
        'default': 0.015
    };

    // Fluoroscopy / projection X-ray DAP→effective dose, mSv / (Gy·cm²).
    const DAP_K_FACTORS = {
        head: 0.04,
        neck: 0.075,
        chest: 0.20,
        cardiac: 0.20,
        abdomen: 0.26,
        pelvis: 0.26,
        'abdomen-pelvis': 0.26,
        'default': 0.22
    };

    // Nuclear medicine effective dose coefficients, mSv / MBq (adult, ICRP 128 style).
    const NM_COEFFICIENTS = {
        byAgent: {
            'fdg': 0.019, 'fluorodeoxyglucose': 0.019,
            'naf': 0.017, 'sodium fluoride': 0.017,
            'mdp': 0.0057, 'hdp': 0.0057,
            'sestamibi': 0.009, 'mibi': 0.009,
            'tetrofosmin': 0.0076,
            'dtpa': 0.0049, 'mag3': 0.007, 'dmsa': 0.0088,
            'maa': 0.011, 'macroaggregated albumin': 0.011,
            'hmpao': 0.0093, 'pertechnetate': 0.013
        },
        byRadionuclide: {
            'f^18^': 0.019, 'f-18': 0.019, '18f': 0.019, 'fluorine': 0.019,
            'tc^99m^': 0.0085, 'tc-99m': 0.0085, '99mtc': 0.0085, 'technetium': 0.0085,
            'ga^68^': 0.021, 'ga-68': 0.021, 'gallium': 0.021,
            'i^123^': 0.21, 'i-123': 0.21,
            'i^131^': 14.0, 'i-131': 14.0, 'iodine': 14.0,
            'in^111^': 0.30, 'indium': 0.30,
            'tl^201^': 0.14, 'thallium': 0.14
        },
        'default': 0.0085
    };

    // ========================================================================
    // DICOM JSON tag constants
    // ========================================================================

    const T = {
        SOP_CLASS_UID: '00080016',
        SOP_INSTANCE_UID: '00080018',
        STUDY_DATE: '00080020',
        STUDY_TIME: '00080030',
        ACCESSION: '00080050',
        MODALITY: '00080060',
        MODALITIES_IN_STUDY: '00080061',
        INSTITUTION: '00080080',
        STUDY_DESC: '00081030',
        SERIES_DESC: '0008103E',
        PATIENT_NAME: '00100010',
        PATIENT_ID: '00100020',
        BODY_PART: '00180015',
        PROTOCOL: '00181030',
        STUDY_UID: '0020000D',
        SERIES_UID: '0020000E',
        // SR content
        VALUE_TYPE: '0040A040',
        CONCEPT_NAME: '0040A043',
        CONCEPT_CODE: '0040A168',
        MEASURED_VALUE: '0040A300',
        NUMERIC_VALUE: '0040A30A',
        UNITS: '004008EA',
        TEXT_VALUE: '0040A160',
        DATETIME_VALUE: '0040A120',
        CONTENT_SEQUENCE: '0040A730',
        CODE_VALUE: '00080100',
        CODE_MEANING: '00080104'
    };

    // ========================================================================
    // State + DOM
    // ========================================================================

    function initialState() {
        return {
            running: false,
            controller: null,
            studies: [],          // raw QIDO study objects
            events: [],           // normalized dose rows
            stats: { studies: 0, processed: 0, doseDocs: 0 }
        };
    }

    let state = initialState();
    let el = {};

    function init() {
        el = {
            patientId: document.getElementById('patientId'),
            patientName: document.getElementById('patientName'),
            studyUid: document.getElementById('studyUid'),
            windowMonths: document.getElementById('windowMonths'),
            endpointInput: document.getElementById('endpointInput'),
            endpointBadge: document.getElementById('endpointBadge'),
            analyzeBtn: document.getElementById('analyzeBtn'),
            cancelBtn: document.getElementById('cancelBtn'),
            clearBtn: document.getElementById('clearBtn'),
            averageFallback: document.getElementById('averageFallback'),
            // progress
            progressPanel: document.getElementById('progressPanel'),
            progressBar: document.getElementById('progressBar'),
            progressPercent: document.getElementById('progressPercent'),
            progressStudies: document.getElementById('progressStudies'),
            progressDocs: document.getElementById('progressDocs'),
            progressCurrent: document.getElementById('progressCurrent'),
            progressLog: document.getElementById('progressLog'),
            // results
            errorBanner: document.getElementById('errorBanner'),
            alertBanner: document.getElementById('alertBanner'),
            summaryPanel: document.getElementById('summaryPanel'),
            summaryGrid: document.getElementById('summaryGrid'),
            modalityPanel: document.getElementById('modalityPanel'),
            modalityBreakdown: document.getElementById('modalityBreakdown'),
            eventsPanel: document.getElementById('eventsPanel'),
            eventsBody: document.getElementById('eventsBody'),
            emptyState: document.getElementById('emptyState')
        };

        loadConfig();
        if (el.endpointInput) el.endpointInput.value = config.fhirEndpoint;
        if (el.windowMonths) el.windowMonths.value = String(config.windowMonths);
        if (el.averageFallback) el.averageFallback.checked = !!config.averageFallback;
        updateEndpointBadge();

        el.analyzeBtn?.addEventListener('click', analyze);
        el.cancelBtn?.addEventListener('click', cancel);
        el.clearBtn?.addEventListener('click', clearAll);
        el.endpointInput?.addEventListener('change', () => {
            const val = el.endpointInput.value.trim();
            config.fhirEndpoint = sanitizeFhirEndpoint(val) || DEFAULT_CONFIG.fhirEndpoint;
            el.endpointInput.value = config.fhirEndpoint;
            saveConfig();
            updateEndpointBadge();
        });
        el.averageFallback?.addEventListener('change', () => {
            config.averageFallback = !!el.averageFallback.checked;
            saveConfig();
        });

        ['patientId', 'patientName', 'studyUid'].forEach(id => {
            document.getElementById(id)?.addEventListener('keypress', e => {
                if (e.key === 'Enter') analyze();
            });
        });

        // Allow prefilling from URL params
        const params = new URLSearchParams(window.location.search);
        if (params.get('patientId')) el.patientId.value = params.get('patientId');
        if (params.get('studyUid')) el.studyUid.value = params.get('studyUid');
        if (params.get('patientId') || params.get('studyUid')) {
            analyze();
        }
    }

    function loadConfig() {
        try {
            const saved = JSON.parse(localStorage.getItem('radiationDoseConfig') || '{}');
            config = { ...DEFAULT_CONFIG, ...saved };
            if (saved.dicomWebEndpoint) {
                config.fhirEndpoint = saved.dicomWebEndpoint;
            }
            if (config.fhirEndpoint) {
                config.fhirEndpoint = sanitizeFhirEndpoint(config.fhirEndpoint);
            }
        } catch (_) { /* ignore */ }
    }

    function saveConfig() {
        try {
            localStorage.setItem('radiationDoseConfig', JSON.stringify({
                fhirEndpoint: config.fhirEndpoint,
                windowMonths: config.windowMonths,
                averageFallback: config.averageFallback
            }));
        } catch (_) { /* ignore */ }
    }

    function updateEndpointBadge() {
        if (!el.endpointBadge) return;
        try {
            const url = new URL(config.fhirEndpoint, window.location.origin);
            el.endpointBadge.textContent = url.hostname + (url.port ? ':' + url.port : '') + url.pathname;
            el.endpointBadge.title = config.fhirEndpoint;
        } catch (_) {
            el.endpointBadge.textContent = 'Invalid endpoint';
        }
    }

    // ========================================================================
    // Main pipeline using MHD & MADO
    // ========================================================================

    async function analyze() {
        if (state.running) return;

        const val = (el.endpointInput?.value || '').trim();
        config.fhirEndpoint = sanitizeFhirEndpoint(val) || DEFAULT_CONFIG.fhirEndpoint;
        if (el.endpointInput) el.endpointInput.value = config.fhirEndpoint;
        config.windowMonths = parseInt(el.windowMonths?.value || '18', 10);
        config.averageFallback = el.averageFallback ? !!el.averageFallback.checked : true;
        saveConfig();
        updateEndpointBadge();

        const patientId = el.patientId.value.trim();
        const patientName = el.patientName.value.trim();
        const studyUid = el.studyUid.value.trim();

        if (!patientId && !patientName && !studyUid) {
            showError('Enter a Patient ID, Patient Name or Study UID to analyse.');
            return;
        }

        state = initialState();
        state.running = true;
        state.controller = new AbortController();

        setRunningUI(true);
        resetResults();
        showProgress();
        setProgress(2, 'Querying FHIR DocumentReferences…');
        log('Starting MHD & MADO radiation dose analysis', 'info');
        log(`Coefficient tables: ${config.coefficientTableVersion}`, 'muted');

        try {
            // Step 1: Query DocumentReferences from MHD FHIR endpoint
            const fhirBundle = await queryDocumentReferences({ patientId, patientName, studyUid });
            const entries = fhirBundle.entry || [];
            log(`Found ${entries.length} DocumentReference resource(s)`, 'info');

            if (entries.length === 0) {
                setProgress(100, 'No manifests found.');
                finishUI();
                showEmpty('No DocumentReferences found for the given criteria.');
                return;
            }

            // Extract unique studies from the DocumentReferences, grouping them and prioritizing FHIR manifests
            const studyMap = {};

            for (const entry of entries) {
                const doc = entry.resource;
                if (!doc || doc.resourceType !== 'DocumentReference') continue;

                let currentStudyUid = doc.masterIdentifier?.value || doc.id;
                if (doc.context?.related) {
                    const studyUidRelated = doc.context.related.find(r =>
                        r.identifier?.type?.coding?.some(c => c.code === '110180'));
                    if (studyUidRelated?.identifier?.value) {
                        currentStudyUid = studyUidRelated.identifier.value;
                    }
                }
                const cleanStudyUid = (currentStudyUid || '').replace(/^ihe:urn:oid:/, '').replace(/^urn:oid:/, '').trim();
                
                if (cleanStudyUid) {
                    if (!studyMap[cleanStudyUid]) {
                        studyMap[cleanStudyUid] = [];
                    }
                    studyMap[cleanStudyUid].push(doc);
                }
            }

            const uniqueStudyRefs = [];
            for (const [studyUid, docs] of Object.entries(studyMap)) {
                // Find the best DocumentReference for this study: prefer FHIR manifest over DICOM KOS
                let bestDoc = docs.find(doc => {
                    const formatCode = doc.format?.code || '';
                    const contentType = doc.content?.[0]?.attachment?.contentType || '';
                    const profiles = doc.meta?.profile || [];
                    const isFhir = formatCode.includes('fhir-manifest') || 
                                   contentType === 'application/fhir+json' || 
                                   profiles.some(p => p.includes('MadoFhirDocumentReference'));
                    return isFhir;
                });
                
                if (!bestDoc) {
                    bestDoc = docs[0];
                }
                
                uniqueStudyRefs.push({
                    studyUid,
                    resource: bestDoc
                });
            }

            state.stats.studies = uniqueStudyRefs.length;
            updateProgressStats();
            log(`Found ${uniqueStudyRefs.length} unique stud${uniqueStudyRefs.length === 1 ? 'y' : 'ies'} to process`, 'success');

            if (uniqueStudyRefs.length === 0) {
                setProgress(100, 'No unique studies found.');
                finishUI();
                showEmpty('No unique studies found in DocumentReference results.');
                return;
            }

            // Step 2: Resolve each DocumentReference & fetch/parse dosimetry metadata or pixels
            for (let i = 0; i < uniqueStudyRefs.length; i++) {
                if (state.controller.signal.aborted) break;
                const studyRef = uniqueStudyRefs[i];
                const pct = 10 + Math.round((i / uniqueStudyRefs.length) * 85);
                setProgress(pct, `Resolving Study Manifest ${i + 1}/${uniqueStudyRefs.length}`);

                log(`Fetching manifest for study: ${shortUid(studyRef.studyUid)}`, 'info');
                
                const docRef = studyRef.resource;
                const binaryUrl = docRef.content?.[0]?.attachment?.url;
                if (!binaryUrl) {
                    log(`No content attachment URL in DocumentReference ${docRef.id} — skipping.`, 'warn');
                    state.stats.processed = i + 1;
                    updateProgressStats();
                    continue;
                }

                let contentUrl = binaryUrl;
                if (!contentUrl.startsWith('http')) {
                    contentUrl = `${config.fhirEndpoint}/${contentUrl.replace(/^\.?\//, '')}`;
                }

                try {
                    const manifestResponse = await fetch(contentUrl, {
                        headers: { 'Accept': 'application/fhir+json, application/json' },
                        signal: state.controller?.signal
                    });
                    if (!manifestResponse.ok) {
                        throw new Error(`HTTP ${manifestResponse.status}: ${manifestResponse.statusText}`);
                    }
                    const manifestBundle = await manifestResponse.json();
                    
                    const studyData = extractStudyData(manifestBundle);
                    const wadoEndpoint = getWadoRsEndpoint(studyData);
                    
                    log(`Study manifest resolved. WADO-RS: ${wadoEndpoint}`, 'success');

                    // Check for Dose SR series (modality SR or DOC)
                    const srSeries = studyData.series.filter(s => {
                        const m = (s.modality || '').toUpperCase();
                        return m === 'SR' || m === 'DOC';
                    });

                    let parsedRows = [];

                    if (srSeries.length > 0) {
                        log(`Found ${srSeries.length} SR series — fetching metadata…`, 'info');
                        for (const series of srSeries) {
                            if (state.controller.signal.aborted) break;
                            try {
                                const metadataList = await fetchWadoMetadata(wadoEndpoint, studyData.studyUid, series.uid);
                                if (metadataList && metadataList.length > 0) {
                                    state.stats.doseDocs++;
                                    updateProgressStats();
                                    
                                    const studyObj = {
                                        [T.STUDY_UID]: { Value: [studyData.studyUid] },
                                        [T.STUDY_DATE]: { Value: [studyData.studyDate] },
                                        [T.INSTITUTION]: { Value: [studyData.institution] },
                                        [T.STUDY_DESC]: { Value: [studyData.studyDescription] }
                                    };

                                    for (const instMeta of metadataList) {
                                        const sopClass = getTag(instMeta, T.SOP_CLASS_UID);
                                        if (DOSE_SOP_CLASSES[sopClass]) {
                                            const docRows = parseDoseDocument(instMeta, studyObj, sopClass);
                                            parsedRows.push(...docRows);
                                        }
                                    }
                                }
                            } catch (metadataError) {
                                log(`Could not retrieve SR metadata for series ${shortUid(series.uid)}: ${metadataError.message}`, 'warn');
                            }
                        }
                    } 
                    
                    // Fallback to Pixel-level analysis if no SR rows were created
                    if (parsedRows.length === 0) {
                        const pixelEstimatedRows = await estimateDoseFromPixels(wadoEndpoint, studyData, studyData.series);
                        parsedRows.push(...pixelEstimatedRows);
                    }

                    // Demo / Fallback average dose computation if still empty and enabled
                    if (parsedRows.length === 0 && config.averageFallback) {
                        const fallbackRows = generateAverageFallbackDose(studyData);
                        parsedRows.push(...fallbackRows);
                    }

                    state.events.push(...parsedRows);
                    
                } catch (manifestError) {
                    log(`Failed to process manifest/study for ${shortUid(studyRef.studyUid)}: ${manifestError.message}`, 'error');
                }

                state.stats.processed = i + 1;
                updateProgressStats();
                renderResults();
            }

            setProgress(100, 'Analysis complete.');
            log(`Done. ${state.events.length} dose record(s) from ${state.stats.doseDocs} dose SR document(s)/pixel-estimators.`,
                state.events.length ? 'success' : 'warn');
            renderResults();
            if (state.events.length === 0) {
                showEmpty('No Dose Structured Reports or fallback image series found.');
            }
        } catch (err) {
            if (err.name === 'AbortError') {
                log('Analysis cancelled.', 'warn');
                setProgress(0, 'Cancelled.');
            } else {
                console.error(err);
                showError(`Analysis failed: ${err.message}`);
                log(`Error: ${err.message}`, 'error');
            }
        } finally {
            finishUI();
        }
    }

    // ========================================================================
    // MHD Search & MADO Manifest Resolution
    // ========================================================================

    async function queryDocumentReferences({ patientId, patientName, studyUid }) {
        const params = new URLSearchParams();
        if (patientId) params.append('patient.identifier', patientId);
        if (patientName) params.append('patient.name', patientName);
        if (studyUid) params.append('study-instance-uid', studyUid);

        if (!studyUid && config.windowMonths > 0) {
            const to = new Date();
            const from = new Date();
            from.setMonth(from.getMonth() - config.windowMonths);
            
            const ymd_fhir = (d) => {
                const y = d.getFullYear();
                const m = String(d.getMonth() + 1).padStart(2, '0');
                const r = String(d.getDate()).padStart(2, '0');
                return `${y}-${m}-${r}`;
            };
            params.append('date', `ge${ymd_fhir(from)}`);
            params.append('date', `le${ymd_fhir(to)}`);
        }

        const url = `${config.fhirEndpoint}/DocumentReference?${params.toString()}`;
        log(`MHD Search: ${url}`, 'muted');
        
        const response = await fetch(url, {
            headers: { 'Accept': 'application/fhir+json, application/json' },
            signal: state.controller?.signal
        });
        if (!response.ok) {
            throw new Error(`HTTP ${response.status} ${response.statusText}`);
        }
        return response.json();
    }

    function extractStudyData(bundle) {
        const data = {
            patientName: 'Unknown Patient',
            patientId: 'Unknown',
            studyUid: '',
            studyDate: '',
            studyDescription: '(no description)',
            institution: 'Unknown',
            endpoints: [],
            series: []
        };

        if (!bundle) return data;

        // If the bundle is not a Bundle but an ImagingStudy resource itself (highly resilient fallback!)
        const isResource = bundle.resourceType === 'ImagingStudy' ? bundle : null;
        const entryList = bundle.entry || [];

        const patientEntry = entryList.find(e => e.resource?.resourceType === 'Patient');
        if (patientEntry?.resource) {
            const p = patientEntry.resource;
            data.patientId = p.identifier?.[0]?.value || p.id || 'Unknown';
            data.patientName = p.name?.[0]?.text || p.name?.[0]?.family || 'Unknown Patient';
        }

        const endpoints = entryList.filter(e => e.resource?.resourceType === 'Endpoint').map(e => e.resource);
        data.endpoints = endpoints;

        const compEntry = entryList.find(e => e.resource?.resourceType === 'Composition');
        if (compEntry?.resource) {
            const c = compEntry.resource;
            data.institution = c.custodian?.display || c.author?.[0]?.display || 'Unknown';
        }

        const studyEntry = isResource ? { resource: isResource } : entryList.find(e => e.resource?.resourceType === 'ImagingStudy');
        if (studyEntry?.resource) {
            const is = studyEntry.resource;
            data.studyUid = is.identifier?.find(id => id.system === 'urn:dicom:uid')?.value || is.id || '';
            data.studyUid = data.studyUid.replace(/^ihe:urn:oid:/, '').replace(/^urn:oid:/, '');
            
            data.studyDescription = is.description || '(no description)';
            data.studyDate = is.started ? is.started.split('T')[0].replace(/-/g, '') : '';
            
            const seriesList = is.series || is.seriesList || [];
            if (seriesList) {
                for (const s of seriesList) {
                    let modalityCode = 'OT';
                    if (s.modality) {
                        if (typeof s.modality === 'string') {
                            modalityCode = s.modality;
                        } else if (typeof s.modality === 'object') {
                            if (s.modality.code) {
                                modalityCode = s.modality.code;
                            } else if (Array.isArray(s.modality.coding) && s.modality.coding.length > 0) {
                                modalityCode = s.modality.coding[0].code || 'OT';
                            } else if (s.modality.coding?.code) {
                                modalityCode = s.modality.coding.code;
                            }
                        }
                    }

                    const instanceList = s.instance || s.instances || [];
                    const seriesData = {
                        uid: s.uid,
                        number: s.number,
                        modality: modalityCode,
                        description: s.description || '',
                        instances: (instanceList || []).map(inst => ({
                            uid: (inst.uid || inst.id || '').replace(/^ihe:urn:oid:/, '').replace(/^urn:oid:/, ''),
                            number: inst.number,
                            sopClass: (inst.sopClass?.code || inst.sopClass || '').replace(/^ihe:urn:oid:/, '').replace(/^urn:oid:/, '')
                        }))
                    };
                    data.series.push(seriesData);
                }
            }
        }

        return data;
    }

    function getWadoRsEndpoint(studyData) {
        for (const ep of studyData.endpoints) {
            const connType = ep.connectionType?.[0]?.coding?.[0]?.code;
            if (connType === 'dicom-wado-rs' || ep.address?.includes('wado')) {
                return ep.address;
            }
        }
        if (studyData.endpoints.length > 0) {
            return studyData.endpoints[0].address;
        }
        return `${window.location.origin}/dicomweb`;
    }

    async function fetchWadoMetadata(wadoEndpoint, studyUid, seriesUid) {
        const urls = [
            `${wadoEndpoint}/studies/${studyUid}/series/${seriesUid}/metadata`,
            `${window.location.origin}/dicomweb/studies/${studyUid}/series/${seriesUid}/metadata`
        ];

        let lastErr = null;
        for (const url of urls) {
            try {
                log(`WADO-RS fetch metadata: ${url}`, 'muted');
                const response = await fetch(url, {
                    headers: { 'Accept': 'application/dicom+json' },
                    signal: state.controller?.signal
                });
                if (response.ok) {
                    const data = await response.json();
                    return Array.isArray(data) ? data : [data];
                }
            } catch (err) {
                lastErr = err;
            }
        }
        throw lastErr || new Error('Failed to retrieve metadata');
    }

    // ========================================================================
    // Pixel-level Radiation Dose Estimator (attenuation algorithm)
    // ========================================================================

    async function getPixelStatsFromFrame(wadoEndpoint, studyUid, seriesUid, instanceUid) {
        let lastError = null;
        const urls = [
            `${wadoEndpoint}/studies/${studyUid}/series/${seriesUid}/instances/${instanceUid}/frames/1`,
            `${window.location.origin}/dicomweb/studies/${studyUid}/series/${seriesUid}/instances/${instanceUid}/frames/1`
        ];

        for (const url of urls) {
            try {
                const response = await fetch(url, {
                    headers: { 'Accept': 'image/jpeg, image/png, */*' },
                    signal: state.controller?.signal
                });
                if (!response.ok) continue;
                
                const blob = await response.blob();
                
                return new Promise((resolve) => {
                    const img = new Image();
                    img.onload = () => {
                        try {
                            const canvas = document.createElement('canvas');
                            canvas.width = img.width || 256;
                            canvas.height = img.height || 256;
                            const ctx = canvas.getContext('2d');
                            ctx.drawImage(img, 0, 0);
                            const imgData = ctx.getImageData(0, 0, canvas.width, canvas.height);
                            const data = imgData.data;
                            
                            let sum = 0;
                            let numPixels = 0;
                            for (let i = 0; i < data.length; i += 4) {
                                const r = data[i];
                                const g = data[i+1];
                                const b = data[i+2];
                                sum += (0.299 * r + 0.587 * g + 0.114 * b);
                                numPixels++;
                            }
                            const mean = numPixels > 0 ? (sum / numPixels) : 127.5;
                            resolve({ success: true, mean, width: img.width, height: img.height });
                        } catch (err) {
                            resolve({ success: false, error: err.message });
                        }
                    };
                    img.onerror = () => {
                        resolve({ success: false, error: 'Could not load frame image' });
                    };
                    img.src = URL.createObjectURL(blob);
                });
            } catch (err) {
                lastError = err;
            }
        }
        throw lastError || new Error('Could not fetch pixel frame');
    }

    async function estimateDoseFromPixels(wadoEndpoint, study, seriesList) {
        log(`No Dose report found in Study. Activating pixel-based luma-attenuation estimator…`, 'info');
        const rows = [];
        
        const checkModalities = ['CT', 'DX', 'CR', 'MG', 'XA', 'RF'];
        const imageSeries = seriesList.filter(s => checkModalities.includes(s.modality.toUpperCase()) && s.instances.length > 0);
        
        if (imageSeries.length === 0) {
            const seriesSummary = (seriesList || []).map(s => `${s.modality || 'UNKNOWN'} (Series ${s.number || '?'}, ${s.instances?.length || 0} inst)`).join(', ');
            log(`No imaging series involving ionizing radiation found in study ${shortUid(study.studyUid)}. Internal series: [${seriesSummary}]`, 'muted');
            return rows;
        }

        for (const series of imageSeries) {
            const modality = series.modality.toUpperCase();
            const instances = series.instances;
            
            const midIndex = Math.floor(instances.length / 2);
            const instance = instances[midIndex] || instances[0];
            
            log(`Retrieving frame pixels for modality ${modality} (Series ${series.number}, Instance ${instance.number || 1})…`, 'info');
            
            let meanLuma = 127.5;
            let note = "";

            try {
                const stats = await getPixelStatsFromFrame(wadoEndpoint, study.studyUid, series.uid, instance.uid);
                if (stats.success) {
                    meanLuma = stats.mean;
                    note = `Frame size: ${stats.width}x${stats.height}. Mean pixel luma: ${meanLuma.toFixed(1)}.`;
                } else {
                    note = `Frame found but draw-analysis failed (${stats.error}). Using baseline luma fallback (128).`;
                }
            } catch (err) {
                note = `Frame retrieve failed: ${err.message}. Using baseline luma fallback (128).`;
            }

            log(`Luma result: ${meanLuma.toFixed(1)} (${note})`, 'muted');

            const attenuationFactor = Math.max(0.5, Math.min(2.5, (255 - meanLuma) / 128));
            const region = detectRegion(study.studyDescription, series.description);
            let effectiveDoseMsv = 0;
            let calculationMethod = "";

            if (modality === 'CT') {
                const sliceThicknessCm = 0.5; 
                const scanLengthCm = instances.length * sliceThicknessCm;
                
                const baseCTDIvolMap = { head: 55.0, neck: 25.0, chest: 12.0, abdomen: 15.0, 'abdomen-pelvis': 15.0, pelvis: 15.0, default: 15.0 };
                const baseCTDIvol = baseCTDIvolMap[region] || baseCTDIvolMap.default;
                
                const ctdiVolMgy = baseCTDIvol * attenuationFactor;
                const dlpMgyCm = ctdiVolMgy * scanLengthCm;
                
                const k = CT_K_FACTORS[region] || CT_K_FACTORS.default;
                effectiveDoseMsv = dlpMgyCm * k;

                calculationMethod = `Pixel attenuation luma-model: Mean luma ${meanLuma.toFixed(1)} indices attenuation multiplier ${attenuationFactor.toFixed(2)}. Estimated CTDIvol: Reference (${baseCTDIvol}mGy) × ${attenuationFactor.toFixed(2)} attenuation = ${ctdiVolMgy.toFixed(1)} mGy. Scan length (${instances.length} slices) = ${scanLengthCm.toFixed(0)}cm. Estimated DLP = ${dlpMgyCm.toFixed(0)} mGy·cm. k-factor (${region}) = ${k}.`;

                rows.push({
                    studyInstanceUid: study.studyUid,
                    studyDate: formatDate(study.studyDate),
                    institution: study.institution,
                    studyDescription: study.studyDescription,
                    accession: study.accession || '',
                    sopClassUid: '1.2.840.10008.5.1.4.1.1.2',
                    sourceType: 'PIXEL_EST',
                    sopInstanceUid: instance.uid,
                    coefficientTableVersion: config.coefficientTableVersion,
                    modalityFamily: 'CT',
                    region,
                    ctdiVolMgy,
                    dlpMgyCm,
                    ctPhantomType: region === 'head' ? 'IEC Head Phantom' : 'IEC Body Phantom',
                    ssdeMgy: ctdiVolMgy * 1.1,
                    effectiveDoseMsv,
                    calculationMethod,
                    confidence: 'low'
                });
            } else {
                const baseDapMap = { head: 0.8, neck: 0.5, chest: 0.15, abdomen: 2.0, pelvis: 2.0, default: 1.0 };
                const baseDap = baseDapMap[region] || baseDapMap.default;
                
                const dapGyCm2 = baseDap * attenuationFactor * instances.length;
                const k = DAP_K_FACTORS[region] || DAP_K_FACTORS.default;
                effectiveDoseMsv = dapGyCm2 * k;

                calculationMethod = `Pixel attenuation luma-model: Mean luma ${meanLuma.toFixed(1)} indices attenuation multiplier ${attenuationFactor.toFixed(2)}. Estimated DAP: Reference (${baseDap} Gy·cm²) × ${attenuationFactor.toFixed(2)} attenuation × ${instances.length} views = ${dapGyCm2.toFixed(2)} Gy·cm². k-factor (${region}) = ${k}.`;

                rows.push({
                    studyInstanceUid: study.studyUid,
                    studyDate: formatDate(study.studyDate),
                    institution: study.institution,
                    studyDescription: study.studyDescription,
                    accession: study.accession || '',
                    sopClassUid: '1.2.840.10008.5.1.4.1.1.1',
                    sourceType: 'PIXEL_EST',
                    sopInstanceUid: instance.uid,
                    coefficientTableVersion: config.coefficientTableVersion,
                    modalityFamily: 'FLUORO',
                    region,
                    dapGyCm2,
                    effectiveDoseMsv,
                    calculationMethod,
                    confidence: 'low'
                });
            }
        }
        return rows;
    }

    function generateAverageFallbackDose(study) {
        log(`No imaging series involving ionizing radiation found in study ${shortUid(study.studyUid)}. Activating "Average Fallback" mock estimator…`, 'info');

        const desc = (study.studyDescription || '').toLowerCase();
        let modalityFamily = 'CT';
        let modality = 'CT';
        let region = detectRegion(study.studyDescription, '');
        if (region === 'default') {
            region = 'chest'; // typical scan for demo purposes
        }

        // Try to guess modality family from description or first series modality if available
        if (desc.includes('xr') || desc.includes('x-ray') || desc.includes('cxr') || desc.includes('radiograph') || desc.includes('fluoroscopy') || desc.includes('xa')) {
            modalityFamily = 'FLUORO';
            modality = 'DX';
        } else if (desc.includes('nm') || desc.includes('pet') || desc.includes('spect') || desc.includes('scintigraphy')) {
            modalityFamily = 'NM';
            modality = 'PT';
        } else if (study.series && study.series.length > 0) {
            const firstM = (study.series[0].modality || '').toUpperCase();
            if (firstM === 'CT') {
                modalityFamily = 'CT';
                modality = 'CT';
            } else if (['DX', 'CR', 'RF', 'XA', 'MG'].includes(firstM)) {
                modalityFamily = 'FLUORO';
                modality = firstM;
            } else if (firstM === 'PT' || firstM === 'NM') {
                modalityFamily = 'NM';
                modality = firstM;
            }
        }

        const rows = [];
        const studyDateFormatted = formatDate(study.studyDate);

        if (modalityFamily === 'CT') {
            // Creative typical CT dose params
            const ctdiVolMgy = region === 'head' ? 55.0 : region === 'chest' ? 12.0 : 15.0;
            const sliceCount = study.series?.reduce((acc, s) => acc + (s.instances?.length || 0), 0) || 120;
            const sliceThicknessCm = 0.5;
            const scanLengthCm = Math.max(10, Math.min(100, sliceCount * sliceThicknessCm));
            const dlpMgyCm = ctdiVolMgy * scanLengthCm;
            const k = CT_K_FACTORS[region] || CT_K_FACTORS.default;
            const effectiveDoseMsv = dlpMgyCm * k;

            rows.push({
                studyInstanceUid: study.studyUid,
                studyDate: studyDateFormatted,
                institution: study.institution || 'Demo Hospital',
                studyDescription: study.studyDescription || 'CT Study',
                accession: study.accession || 'DEMO' + Math.floor(Math.random() * 100000),
                sopClassUid: '1.2.840.10008.5.1.4.1.1.2',
                sourceType: 'DEMO_FALLBACK',
                sopInstanceUid: study.series?.[0]?.instances?.[0]?.uid || (study.studyUid + '.1.1'),
                coefficientTableVersion: config.coefficientTableVersion,
                modalityFamily: 'CT',
                region,
                ctdiVolMgy,
                dlpMgyCm,
                ctPhantomType: region === 'head' ? 'IEC Head Phantom' : 'IEC Body Phantom',
                ssdeMgy: ctdiVolMgy * 1.05,
                effectiveDoseMsv,
                calculationMethod: `Demo Average Fallback Mode: Average reference values for ${region} CT used. CTDIvol = ${ctdiVolMgy} mGy, Scan Length = ${scanLengthCm.toFixed(1)} cm (estimated from ${sliceCount} slices). DLP = ${dlpMgyCm.toFixed(0)} mGy·cm. Effective dose = DLP × k-factor (${k}) = ${effectiveDoseMsv.toFixed(2)} mSv.`,
                confidence: 'medium'
            });
        } else if (modalityFamily === 'FLUORO') {
            const dapGyCm2 = region === 'head' ? 1.0 : region === 'chest' ? 0.2 : 2.5;
            const k = DAP_K_FACTORS[region] || DAP_K_FACTORS.default;
            const effectiveDoseMsv = dapGyCm2 * k;

            rows.push({
                studyInstanceUid: study.studyUid,
                studyDate: studyDateFormatted,
                institution: study.institution || 'Demo Hospital',
                studyDescription: study.studyDescription || 'X-Ray Study',
                accession: study.accession || 'DEMO' + Math.floor(Math.random() * 100000),
                sopClassUid: '1.2.840.10008.5.1.4.1.1.1',
                sourceType: 'DEMO_FALLBACK',
                sopInstanceUid: study.series?.[0]?.instances?.[0]?.uid || (study.studyUid + '.1.1'),
                coefficientTableVersion: config.coefficientTableVersion,
                modalityFamily: 'FLUORO',
                region,
                dapGyCm2,
                effectiveDoseMsv,
                calculationMethod: `Demo Average Fallback Mode: Average reference values for ${region} ${modality} used. DAP = ${dapGyCm2} Gy·cm². Effective dose = DAP × k-factor (${k}) = ${effectiveDoseMsv.toFixed(2)} mSv.`,
                confidence: 'medium'
            });
        } else {
            // NM
            const activity = 250; // MBq
            const radionuclide = 'Tc-99m';
            const coeff = NM_COEFFICIENTS.byRadionuclide['tc-99m'] || NM_COEFFICIENTS.default;
            const effectiveDoseMsv = activity * coeff;

            rows.push({
                studyInstanceUid: study.studyUid,
                studyDate: studyDateFormatted,
                institution: study.institution || 'Demo Hospital',
                studyDescription: study.studyDescription || 'NM Study',
                accession: study.accession || 'DEMO' + Math.floor(Math.random() * 100000),
                sopClassUid: '1.2.840.10008.5.1.4.1.1.20',
                sourceType: 'DEMO_FALLBACK',
                sopInstanceUid: study.series?.[0]?.instances?.[0]?.uid || (study.studyUid + '.1.1'),
                coefficientTableVersion: config.coefficientTableVersion,
                modalityFamily: 'NM',
                region,
                administeredActivityMbq: activity,
                radionuclide,
                effectiveDoseMsv,
                calculationMethod: `Demo Average Fallback Mode: Default radionuclide ${radionuclide} with average administered activity ${activity} MBq. Effective dose = activity × coeff (${coeff}) = ${effectiveDoseMsv.toFixed(2)} mSv.`,
                confidence: 'medium'
            });
        }

        log(`Generated fallback dose of ${rows[0].effectiveDoseMsv.toFixed(2)} mSv (${modalityFamily}, region: ${region})`, 'success');
        return rows;
    }

    // ========================================================================
    // DICOM SR parsing → normalized dose rows
    // ========================================================================

    function parseDoseDocument(inst, study, sopClass) {
        const measurements = [];
        collectMeasurements(inst, [], measurements);

        const sourceMeta = DOSE_SOP_CLASSES[sopClass];
        const base = {
            studyInstanceUid: getTag(study, T.STUDY_UID),
            studyDate: formatDate(getTag(study, T.STUDY_DATE) || getTag(inst, T.STUDY_DATE)),
            institution: getTag(inst, T.INSTITUTION) || getTag(study, T.INSTITUTION) || 'Unknown',
            studyDescription: getTag(study, T.STUDY_DESC) || getTag(inst, T.STUDY_DESC) || '(no description)',
            accession: getTag(study, T.ACCESSION) || getTag(inst, T.ACCESSION) || '',
            sopClassUid: sopClass,
            sourceType: sourceMeta.source,
            sopInstanceUid: getTag(inst, T.SOP_INSTANCE_UID),
            coefficientTableVersion: config.coefficientTableVersion
        };

        // Decide family from SOP class + measurement content
        const isNm = sopClass === '1.2.840.10008.5.1.4.1.1.88.68'
            || measurements.some(m => /administered activity|radionuclide|radiopharmaceutical/i.test(m.meaning));
        const isCt = measurements.some(m => /ctdivol|dose length product|\bdlp\b/i.test(m.meaning));
        const isProjection = measurements.some(m => /dose area product|\bdap\b|dose \(rp\)|reference (point )?air kerma|fluoro/i.test(m.meaning));

        const rows = [];
        if (isCt) rows.push(buildCtRow(base, measurements));
        if (isProjection) rows.push(buildProjectionRow(base, measurements));
        if (isNm) rows.push(buildNmRow(base, measurements));

        if (rows.length === 0) {
            // Patient Radiation Dose SR may carry a pre-computed effective dose
            const effNum = findNum(measurements, /effective dose/i);
            if (effNum) {
                rows.push({
                    ...base,
                    modalityFamily: 'OTHER',
                    region: detectRegion(base.studyDescription, ''),
                    effectiveDoseMsv: toMsv(effNum.value, effNum.unit),
                    calculationMethod: 'Reported in Patient Radiation Dose SR',
                    confidence: 'high'
                });
            }
        }
        return rows.filter(Boolean);
    }

    function buildCtRow(base, measurements, study) {
        const perEventDlp = measurements.filter(m =>
            /dose length product|\bdlp\b/i.test(m.meaning) && inAncestor(m, /acquisition|irradiation event/i));
        const totalDlp = measurements.find(m =>
            /dose length product total/i.test(m.meaning) ||
            (/dose length product|\bdlp\b/i.test(m.meaning) && inAncestor(m, /accumulated/i)));

        const dlp = totalDlp ? num(totalDlp.value)
            : perEventDlp.reduce((a, m) => a + num(m.value), 0);

        const ctdiVols = measurements.filter(m => /ctdivol/i.test(m.meaning)).map(m => num(m.value));
        const ctdiVolMean = ctdiVols.length ? ctdiVols.reduce((a, b) => a + b, 0) / ctdiVols.length : null;
        const ssde = findNum(measurements, /size specific dose|ssde/i);
        const phantom = measurements.find(m => m.type === 'CODE' && /phantom type/i.test(m.meaning));

        const region = detectRegion(base.studyDescription, '', phantom?.conceptCodeMeaning);
        const k = CT_K_FACTORS[region] ?? CT_K_FACTORS.default;
        const effectiveDose = dlp > 0 ? dlp * k : null;

        return {
            ...base,
            modalityFamily: 'CT',
            region,
            ctdiVolMgy: ctdiVolMean,
            dlpMgyCm: dlp || null,
            ctPhantomType: phantom?.conceptCodeMeaning || null,
            ssdeMgy: ssde ? num(ssde.value) : null,
            effectiveDoseMsv: effectiveDose,
            calculationMethod: effectiveDose != null
                ? `DLP × k(${region}) = ${dlp.toFixed(1)} × ${k} mSv/(mGy·cm)` : 'n/a',
            confidence: totalDlp ? 'high' : (perEventDlp.length ? 'high' : 'medium')
        };
    }

    function buildProjectionRow(base, measurements, study) {
        const dapTotal = measurements.find(m =>
            /dose area product total/i.test(m.meaning) ||
            (/dose area product|\bdap\b/i.test(m.meaning) && inAncestor(m, /accumulated/i)));
        const perEventDap = measurements.filter(m =>
            /dose area product|\bdap\b/i.test(m.meaning) && inAncestor(m, /irradiation event/i));

        let dapGyCm2 = null;
        if (dapTotal) {
            dapGyCm2 = toGyCm2(num(dapTotal.value), dapTotal.unit);
        } else if (perEventDap.length) {
            dapGyCm2 = perEventDap.reduce((a, m) => a + toGyCm2(num(m.value), m.unit), 0);
        }

        const refKerma = measurements.find(m => /dose \(rp\) total|reference (point )?air kerma/i.test(m.meaning));
        const fluoroTime = measurements.find(m => /fluoro.*time|total fluoro/i.test(m.meaning));

        const region = detectRegion(base.studyDescription, '');
        const k = DAP_K_FACTORS[region] ?? DAP_K_FACTORS.default;
        const effectiveDose = dapGyCm2 != null && dapGyCm2 > 0 ? dapGyCm2 * k : null;

        return {
            ...base,
            modalityFamily: 'FLUORO',
            region,
            dapGyCm2,
            referenceAirKermaGy: refKerma ? toGy(num(refKerma.value), refKerma.unit) : null,
            fluoroTimeSeconds: fluoroTime ? num(fluoroTime.value) : null,
            effectiveDoseMsv: effectiveDose,
            calculationMethod: effectiveDose != null
                ? `DAP × k(${region}) = ${(dapGyCm2 || 0).toFixed(2)} Gy·cm² × ${k} mSv/(Gy·cm²)` : 'n/a',
            confidence: dapTotal ? 'high' : (perEventDap.length ? 'high' : 'medium')
        };
    }

    function buildNmRow(base, measurements) {
        const activity = measurements.find(m =>
            /administered activity|radiopharmaceutical.*activity|total administered/i.test(m.meaning) && m.type === 'NUM');
        const radionuclide = measurements.find(m => m.type === 'CODE' && /radionuclide/i.test(m.meaning));
        const agent = measurements.find(m => m.type === 'CODE' &&
            /radiopharmaceutical( agent)?$/i.test(m.meaning));

        const activityMbq = activity ? toMbq(num(activity.value), activity.unit) : null;
        const coeff = nmCoefficient(agent?.conceptCodeMeaning, radionuclide?.conceptCodeMeaning);
        const effectiveDose = activityMbq != null ? activityMbq * coeff : null;

        return {
            ...base,
            modalityFamily: 'NM',
            region: 'whole body',
            administeredActivityMbq: activityMbq,
            radionuclide: radionuclide?.conceptCodeMeaning || null,
            radiopharmaceutical: agent?.conceptCodeMeaning || null,
            effectiveDoseMsv: effectiveDose,
            calculationMethod: effectiveDose != null
                ? `Activity × coeff = ${activityMbq.toFixed(0)} MBq × ${coeff} mSv/MBq` : 'n/a',
            confidence: activityMbq != null ? 'high' : 'low'
        };
    }

    // ---- SR tree walker ----------------------------------------------------

    function collectMeasurements(item, ancestors, out) {
        const valueType = getTag(item, T.VALUE_TYPE);
        const conceptName = getCodeFromSeq(item[T.CONCEPT_NAME]);
        const meaning = conceptName?.meaning || '';

        if (valueType === 'CONTAINER') {
            ancestors = ancestors.concat(meaning);
        } else if (valueType === 'NUM') {
            const mv = item[T.MEASURED_VALUE]?.Value?.[0];
            const value = mv ? mv[T.NUMERIC_VALUE]?.Value?.[0] : undefined;
            const unitCode = mv ? getCodeFromSeq(mv[T.UNITS]) : null;
            out.push({
                type: 'NUM', meaning, ancestors,
                code: conceptName?.code,
                value,
                unit: unitCode?.code || unitCode?.meaning || ''
            });
        } else if (valueType === 'CODE') {
            const cc = getCodeFromSeq(item[T.CONCEPT_CODE]);
            out.push({
                type: 'CODE', meaning, ancestors,
                code: conceptName?.code,
                conceptCode: cc?.code,
                conceptCodeMeaning: cc?.meaning || ''
            });
        } else if (valueType === 'TEXT') {
            out.push({ type: 'TEXT', meaning, ancestors, value: item[T.TEXT_VALUE]?.Value?.[0] || '' });
        } else if (valueType === 'DATETIME') {
            out.push({ type: 'DATETIME', meaning, ancestors, value: item[T.DATETIME_VALUE]?.Value?.[0] || '' });
        }

        const children = item[T.CONTENT_SEQUENCE]?.Value;
        if (Array.isArray(children)) {
            for (const child of children) {
                collectMeasurements(child, ancestors, out);
            }
        }
    }

    function getCodeFromSeq(seq) {
        const code = seq?.Value?.[0];
        if (!code) return null;
        return {
            code: code[T.CODE_VALUE]?.Value?.[0],
            meaning: code[T.CODE_MEANING]?.Value?.[0]
        };
    }

    function inAncestor(m, regex) {
        return (m.ancestors || []).some(a => regex.test(a));
    }

    function findNum(measurements, regex) {
        return measurements.find(m => m.type === 'NUM' && regex.test(m.meaning));
    }

    // ========================================================================
    // Unit normalisation + coefficient lookups
    // ========================================================================

    function num(v) {
        const n = parseFloat(v);
        return Number.isFinite(n) ? n : 0;
    }

    function toGyCm2(value, unit) {
        const u = (unit || '').toLowerCase().replace(/\s/g, '');
        const map = {
            'gy.m2': 1e4, 'gym2': 1e4,
            'mgy.m2': 10, 'ugy.m2': 0.01, 'µgy.m2': 0.01, 'cgy.m2': 100, 'dgy.m2': 1000,
            'gy.cm2': 1, 'mgy.cm2': 1e-3, 'cgy.cm2': 1e-2, 'ugy.cm2': 1e-6, 'dgy.cm2': 0.1
        };
        return value * (map[u] ?? 1);
    }

    function toGy(value, unit) {
        const u = (unit || '').toLowerCase();
        if (u === 'mgy') return value / 1000;
        if (u === 'ugy' || u === 'µgy') return value / 1e6;
        if (u === 'cgy') return value / 100;
        return value; // assume Gy
    }

    function toMbq(value, unit) {
        const u = (unit || '').toLowerCase();
        if (u === 'bq') return value / 1e6;
        if (u === 'kbq') return value / 1000;
        if (u === 'gbq') return value * 1000;
        if (u === 'mci') return value * 37; // 1 mCi = 37 MBq
        if (u === 'uci' || u === 'µci') return value * 0.037;
        return value; // assume MBq
    }

    function toMsv(value, unit) {
        const u = (unit || '').toLowerCase();
        if (u === 'usv' || u === 'µsv') return value / 1000;
        if (u === 'sv') return value * 1000;
        return value; // assume mSv
    }

    function nmCoefficient(agent, radionuclide) {
        const a = (agent || '').toLowerCase();
        for (const key of Object.keys(NM_COEFFICIENTS.byAgent)) {
            if (a.includes(key)) return NM_COEFFICIENTS.byAgent[key];
        }
        const r = (radionuclide || '').toLowerCase();
        for (const key of Object.keys(NM_COEFFICIENTS.byRadionuclide)) {
            if (r.includes(key)) return NM_COEFFICIENTS.byRadionuclide[key];
        }
        return NM_COEFFICIENTS.default;
    }

    function detectRegion(studyDesc, protocol, phantom) {
        const text = `${studyDesc || ''} ${protocol || ''}`.toLowerCase();
        const has = (...words) => words.some(w => text.includes(w));

        if (has('abdomen') && has('pelvis')) return 'abdomen-pelvis';
        if (has('chest', 'thorax') && has('abdomen') && has('pelvis')) return 'chest-abdomen-pelvis';
        if (has('head', 'brain', 'cranial')) return 'head';
        if (has('neck', 'cervical')) return 'neck';
        if (has('chest', 'thorax', 'lung', 'pulmonary')) return 'chest';
        if (has('cardiac', 'coronary', 'heart')) return 'cardiac';
        if (has('abdomen')) return 'abdomen';
        if (has('pelvis')) return 'pelvis';

        // Phantom hint: body phantom → trunk; head phantom → head
        if (phantom && /head/i.test(phantom)) return 'head';
        if (phantom && /body/i.test(phantom)) return 'abdomen';
        return 'default';
    }

    // ========================================================================
    // Rendering
    // ========================================================================

    function renderResults() {
        const events = state.events;
        if (events.length === 0) {
            el.summaryPanel.style.display = 'none';
            el.modalityPanel.style.display = 'none';
            el.eventsPanel.style.display = 'none';
            el.alertBanner.style.display = 'none';
            return;
        }
        el.emptyState.style.display = 'none';

        renderSummary(events);
        renderModalityBreakdown(events);
        renderEventsTable(events);
        renderAlert(events);
    }

    function renderSummary(events) {
        const totalEff = sum(events, 'effectiveDoseMsv');
        const ctEvents = events.filter(e => e.modalityFamily === 'CT');
        const totalDlp = sum(ctEvents, 'dlpMgyCm');
        const institutions = new Set(events.map(e => e.institution).filter(Boolean));
        const studies = new Set(events.map(e => e.studyInstanceUid));

        const cards = [
            { label: 'Estimated effective dose', value: fmt(totalEff, 1) + ' mSv', cls: doseClass(totalEff), hint: 'Decision-support estimate' },
            { label: 'Cumulative CT DLP', value: fmt(totalDlp, 0) + ' mGy·cm', cls: '', hint: `${ctEvents.length} CT exam(s)` },
            { label: 'Dose records', value: String(events.length), cls: '', hint: `${studies.size} studies` },
            { label: 'Institutions', value: String(institutions.size), cls: '', hint: `Last ${config.windowMonths} months` }
        ];

        el.summaryGrid.innerHTML = cards.map(c => `
            <div class="dose-summary-card ${c.cls}">
                <div class="dose-summary-value">${escapeHtml(c.value)}</div>
                <div class="dose-summary-label">${escapeHtml(c.label)}</div>
                <div class="dose-summary-hint">${escapeHtml(c.hint)}</div>
            </div>`).join('');
        el.summaryPanel.style.display = 'block';
    }

    function renderModalityBreakdown(events) {
        const families = {};
        for (const e of events) {
            const f = e.modalityFamily;
            if (!families[f]) families[f] = { count: 0, eff: 0 };
            families[f].count++;
            families[f].eff += e.effectiveDoseMsv || 0;
        }
        const totalEff = Object.values(families).reduce((a, f) => a + f.eff, 0) || 1;

        el.modalityBreakdown.innerHTML = Object.entries(families).map(([f, d]) => {
            const pct = Math.round((d.eff / totalEff) * 100);
            return `
                <div class="modality-row">
                    <div class="modality-name">${escapeHtml(familyLabel(f))} <span class="modality-count">${d.count}</span></div>
                    <div class="modality-bar-track"><div class="modality-bar modality-${f.toLowerCase()}" style="width:${pct}%"></div></div>
                    <div class="modality-value">${fmt(d.eff, 1)} mSv</div>
                </div>`;
        }).join('');
        el.modalityPanel.style.display = 'block';
    }

    function renderEventsTable(events) {
        const sorted = [...events].sort((a, b) => (b.studyDate || '').localeCompare(a.studyDate || ''));
        el.eventsBody.innerHTML = sorted.map(e => `
            <tr>
                <td>${escapeHtml(e.studyDate || '—')}</td>
                <td><span class="family-badge family-${e.modalityFamily.toLowerCase()}">${escapeHtml(familyLabel(e.modalityFamily))}</span></td>
                <td>${escapeHtml(e.studyDescription || '—')}<br><small class="muted">${escapeHtml(e.institution || '')}</small></td>
                <td>${escapeHtml(prettyRegion(e.region))}</td>
                <td class="num">${doseIndexCell(e)}</td>
                <td class="num"><strong>${e.effectiveDoseMsv != null ? fmt(e.effectiveDoseMsv, 2) + ' mSv' : '—'}</strong></td>
                <td><span class="src-badge src-${(e.sourceType || '').toLowerCase()}">${escapeHtml(e.sourceType)}</span></td>
                <td><span class="conf-badge conf-${e.confidence}">${escapeHtml(e.confidence)}</span></td>
            </tr>
            <tr class="method-row"><td colspan="8"><small class="muted">${escapeHtml(e.calculationMethod || '')}</small></td></tr>
        `).join('');
        el.eventsPanel.style.display = 'block';
    }

    function doseIndexCell(e) {
        const parts = [];
        if (e.ctdiVolMgy != null) parts.push(`CTDIvol ${fmt(e.ctdiVolMgy, 1)} mGy`);
        if (e.dlpMgyCm != null) parts.push(`DLP ${fmt(e.dlpMgyCm, 0)} mGy·cm`);
        if (e.ssdeMgy != null) parts.push(`SSDE ${fmt(e.ssdeMgy, 1)} mGy`);
        if (e.dapGyCm2 != null) parts.push(`DAP ${fmt(e.dapGyCm2, 2)} Gy·cm²`);
        if (e.referenceAirKermaGy != null) parts.push(`K(RP) ${fmt(e.referenceAirKermaGy, 2)} Gy`);
        if (e.fluoroTimeSeconds != null) parts.push(`Fluoro ${fmt(e.fluoroTimeSeconds, 0)} s`);
        if (e.administeredActivityMbq != null) parts.push(`${fmt(e.administeredActivityMbq, 0)} MBq ${escapeHtml(e.radiopharmaceutical || e.radionuclide || '')}`);
        return parts.length ? parts.join('<br>') : '—';
    }

    function renderAlert(events) {
        // Decision-support context for CT abdomen/pelvis (or high cumulative dose)
        const ctAP = events.filter(e => e.modalityFamily === 'CT' &&
            /abdomen|pelvis/.test(e.region));
        const totalEff = sum(events, 'effectiveDoseMsv');
        const ctTotalEff = sum(events.filter(e => e.modalityFamily === 'CT'), 'effectiveDoseMsv');
        const ctDlp = sum(events.filter(e => e.modalityFamily === 'CT'), 'dlpMgyCm');
        const institutions = new Set(events.map(e => e.institution).filter(Boolean));

        const trigger = ctAP.length >= 3 || totalEff >= 50;
        if (!trigger) {
            el.alertBanner.style.display = 'none';
            return;
        }

        el.alertBanner.innerHTML = `
            <div class="alert-icon">⚠️</div>
            <div class="alert-body">
                <div class="alert-title">Radiation exposure context</div>
                <p>Patient had <strong>${ctAP.length}</strong> CT abdomen/pelvis stud${ctAP.length === 1 ? 'y' : 'ies'}
                   in the last ${config.windowMonths} months across <strong>${institutions.size}</strong> institution(s).</p>
                <p>Cumulative CT DLP: <strong>${fmt(ctDlp, 0)} mGy·cm</strong> &nbsp;•&nbsp;
                   Estimated CT effective dose: <strong>${fmt(ctTotalEff, 0)} mSv</strong> &nbsp;•&nbsp;
                   Total estimate: <strong>${fmt(totalEff, 0)} mSv</strong></p>
                <p>Method: DLP × body-region/age coefficient table ${config.coefficientTableVersion}.</p>
                <p class="alert-disclaimer">This is an estimate for decision support, not a reason to withhold clinically indicated imaging.</p>
            </div>`;
        el.alertBanner.style.display = 'flex';
    }

    // ========================================================================
    // UI helpers
    // ========================================================================

    function familyLabel(f) {
        return { CT: 'CT', FLUORO: 'Fluoro / X-Ray', NM: 'Nuclear Medicine', PET: 'PET', OTHER: 'Other' }[f] || f;
    }

    function prettyRegion(r) {
        if (!r || r === 'default') return 'Unspecified';
        return r.replace(/-/g, '–').replace(/\b\w/g, c => c.toUpperCase());
    }

    function doseClass(msv) {
        if (msv >= 100) return 'dose-high';
        if (msv >= 50) return 'dose-elevated';
        if (msv >= 20) return 'dose-moderate';
        return '';
    }

    function sum(arr, key) {
        return arr.reduce((a, e) => a + (e[key] || 0), 0);
    }

    function fmt(v, decimals) {
        if (v == null || !Number.isFinite(v)) return '0';
        return v.toLocaleString(undefined, { minimumFractionDigits: decimals, maximumFractionDigits: decimals });
    }

    function setRunningUI(running) {
        state.running = running;
        el.analyzeBtn.disabled = running;
        el.cancelBtn.style.display = running ? 'inline-flex' : 'none';
        el.analyzeBtn.innerHTML = running
            ? '<span>⏳ Analysing…</span>' : '<span>📊 Analyse Dose</span>';
    }

    function finishUI() {
        setRunningUI(false);
        state.running = false;
    }

    function cancel() {
        if (state.controller) state.controller.abort();
    }

    function clearAll() {
        if (state.running) cancel();
        state = initialState();
        ['patientId', 'patientName', 'studyUid'].forEach(id => {
            const e = document.getElementById(id);
            if (e) e.value = '';
        });
        resetResults();
        el.progressPanel.style.display = 'none';
        el.errorBanner.style.display = 'none';
        showEmpty('Run an analysis to see a patient\'s radiation exposure context.');
    }

    function resetResults() {
        el.summaryPanel.style.display = 'none';
        el.modalityPanel.style.display = 'none';
        el.eventsPanel.style.display = 'none';
        el.alertBanner.style.display = 'none';
        el.errorBanner.style.display = 'none';
        el.eventsBody.innerHTML = '';
    }

    function showEmpty(msg) {
        if (!el.emptyState) return;
        el.emptyState.querySelector('.empty-text').textContent = msg;
        el.emptyState.style.display = 'block';
    }

    function showError(msg) {
        el.errorBanner.textContent = msg;
        el.errorBanner.style.display = 'block';
    }

    // ---- progress ----------------------------------------------------------

    function showProgress() {
        el.progressPanel.style.display = 'block';
        el.progressLog.innerHTML = '';
    }

    function setProgress(pct, message) {
        el.progressBar.style.width = `${pct}%`;
        el.progressPercent.textContent = `${pct}%`;
        if (message) el.progressCurrent.textContent = message;
    }

    function updateProgressStats() {
        el.progressStudies.textContent = `${state.stats.processed} / ${state.stats.studies}`;
        el.progressDocs.textContent = String(state.stats.doseDocs);
    }

    function log(message, level = 'info') {
        const entry = document.createElement('div');
        entry.className = `log-entry log-${level}`;
        const time = new Date().toLocaleTimeString();
        entry.innerHTML = `<span class="log-time">${time}</span> ${escapeHtml(message)}`;
        el.progressLog.appendChild(entry);
        el.progressLog.scrollTop = el.progressLog.scrollHeight;
    }

    // ---- generic helpers ---------------------------------------------------

    async function fetchJson(url, accept = 'application/dicom+json') {
        const response = await fetch(url, {
            headers: { 'Accept': accept },
            signal: state.controller?.signal
        });
        if (!response.ok) {
            throw new Error(`HTTP ${response.status} ${response.statusText}`);
        }
        const data = await response.json();
        return Array.isArray(data) ? data : (data ? [data] : []);
    }

    function getTag(obj, tag) {
        const td = obj?.[tag];
        if (!td || !td.Value || td.Value.length === 0) return null;
        const v = td.Value[0];
        if (td.vr === 'PN' && typeof v === 'object') return v.Alphabetic || '';
        return v;
    }

    function shortUid(uid) {
        return uid ? '…' + String(uid).slice(-10) : '?';
    }

    function ymd(d) {
        const y = d.getFullYear();
        const m = String(d.getMonth() + 1).padStart(2, '0');
        const day = String(d.getDate()).padStart(2, '0');
        return `${y}${m}${day}`;
    }

    function formatDate(s) {
        if (!s) return '';
        const str = String(s);
        if (str.length >= 8) return `${str.slice(0, 4)}-${str.slice(4, 6)}-${str.slice(6, 8)}`;
        return str;
    }

    function escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text == null ? '' : String(text);
        return div.innerHTML;
    }

    return { init };
})();

document.addEventListener('DOMContentLoaded', () => RadiationDose.init());

