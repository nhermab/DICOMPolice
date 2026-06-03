/**
 * MADO Viewer Module
 * Handles searching and viewing MADO manifests with advanced actions
 */

const MadoViewer = (function() {
    // Configuration defaults
    const CONFIG_KEY = 'madoViewerConfig';
    const COLUMNS_KEY = 'madoViewerColumns';
    const DEFAULT_CONFIG = {
        fhirEndpoint: '',  // Empty means use local
        ohifViewerUrl: 'https://ihebelgium.ehealthhub.be/ohif/mado',
        // OAuth2 client_credentials authentication (browser-only).
        // When authEnabled is false, requests are sent without an Authorization header
        // (keeps the existing local/no-auth backend working).
        authEnabled: false,
        tokenUrl: '',
        clientId: '',
        clientSecret: '',
        scope: '',
        audience: '',
        resource: '',
        providerId: ''  // sent as the fixed X-Provider header on authenticated requests
    };

    // ==============================
    // Column Definitions
    // ==============================

    // All available columns with their rendering logic
    const ALL_COLUMNS = {
        date:            { label: 'Study Date',   sortKey: 'date',           render: (doc) => `<td class="date-cell">${doc.dateString}</td>` },
        modalities:      { label: 'Modality',     sortKey: 'modalities',     render: (doc) => `<td class="modality-cell">${doc.modalities.map(m => '<span class="modality-badge">' + m + '</span>').join('')}</td>` },
        bodySite:        { label: 'Body Site',    sortKey: 'bodySite',       render: (doc, esc) => `<td class="bodysite-cell">${esc(doc.bodySite) || '-'}</td>` },
        description:     { label: 'Description',  sortKey: 'description',    render: (doc, esc) => `<td class="description-cell" title="${esc(doc.description)}">${esc(doc.description)}</td>` },
        accessionNumber: { label: 'Accession',    sortKey: 'accessionNumber',render: (doc, esc) => `<td>${esc(doc.accessionNumber)}</td>` },
        institutionName: { label: 'Institution',  sortKey: 'institutionName',render: (doc, esc) => `<td class="institution-cell" title="${esc(doc.institutionName)}">${esc(doc.institutionName) || '-'}</td>` },
        manifestFormat:  { label: 'Formats',      sortKey: 'manifestFormat', render: (doc) => `<td class="format-cell">${doc.documentCategory === 'other' ? '<span class="format-badge format-other" title="' + (doc.contentType || doc.manifestFormat) + '">' + getFormatIcon(doc.manifestFormat) + ' ' + doc.manifestFormat + '</span>' : (doc.hasFhir ? '<span class="format-badge format-fhir" title="FHIR DocumentReference available">🔥 FHIR</span>' : '') + (doc.hasKos ? '<span class="format-badge format-kos" title="DICOM KOS DocumentReference available">🩻 KOS</span>' : '') + (doc.isPaired ? '<span class="linked-badge" title="FHIR and KOS are linked via relatesTo">🔗</span>' : '')}</td>` },
        author:          { label: 'Author',       sortKey: 'author',         render: (doc, esc) => `<td class="author-cell" title="${esc(doc.author)}">${esc(doc.author)}</td>` },
        studyUid:        { label: 'Study UID',    sortKey: 'studyUid',       render: (doc, esc) => `<td class="uid-cell" title="${esc(doc.studyUid)}">${esc(doc.studyUid)}</td>` },
        patientId:       { label: 'Patient ID',   sortKey: 'patientId',      render: (doc, esc) => `<td class="patient-cell">${esc(doc.patientId)}</td>` },
        patientName:     { label: 'Patient Name', sortKey: 'patientName',    render: (doc, esc) => `<td class="patient-cell">${esc(doc.patientName)}</td>` },
        size:            { label: 'Size',          sortKey: 'size',           render: (doc) => `<td>${doc.size ? (doc.size / 1024).toFixed(1) + ' KB' : '-'}</td>` }
    };

    // Default visible columns in preferred order
    const DEFAULT_COLUMNS = ['date', 'modalities', 'bodySite', 'description', 'accessionNumber', 'institutionName', 'manifestFormat', 'patientId', 'patientName'];

    // Application State
    const state = {
        allDocuments: [],
        filteredDocuments: [],
        rawBundle: null,
        currentPage: 1,
        pageSize: 50,
        sortColumn: 'date',
        sortDirection: 'desc',
        selectedDocument: null,
        jsonViewMode: 'formatted',
        config: { ...DEFAULT_CONFIG },
        visibleColumns: [...DEFAULT_COLUMNS],  // ordered list of column keys
        tokenCache: null  // { token, expiresAt } for OAuth2 access token caching
    };

    // DOM Elements
    let elements = {};

    function init() {
        loadConfig();
        loadColumnConfig();

        elements = {
            searchBtn: document.getElementById('searchBtn'),
            clearBtn: document.getElementById('clearBtn'),
            refreshBtn: document.getElementById('refreshBtn'),
            exportBtn: document.getElementById('exportBtn'),
            configBtn: document.getElementById('configBtn'),
            inspectJsonBtn: document.getElementById('inspectJsonBtn'),
            columnsBtn: document.getElementById('columnsBtn'),
            quickSearch: document.getElementById('quickSearch'),
            tableContent: document.getElementById('tableContent'),
            pagination: document.getElementById('pagination'),
            paginationControls: document.getElementById('paginationControls'),
            statsGrid: document.getElementById('statsGrid'),
            errorBanner: document.getElementById('errorBanner'),
            endpointBadge: document.getElementById('endpointBadge'),
            patientId: document.getElementById('patientId'),
            studyUid: document.getElementById('studyUid'),
            accessionNumber: document.getElementById('accessionNumber'),
            modality: document.getElementById('modality'),
            dateFrom: document.getElementById('dateFrom'),
            dateTo: document.getElementById('dateTo'),
            fhirEndpoint: document.getElementById('fhirEndpoint'),
            ohifViewerUrl: document.getElementById('ohifViewerUrl'),
            authEnabled: document.getElementById('authEnabled'),
            tokenUrl: document.getElementById('tokenUrl'),
            clientId: document.getElementById('clientId'),
            clientSecret: document.getElementById('clientSecret'),
            authScope: document.getElementById('authScope'),
            authAudience: document.getElementById('authAudience'),
            authResource: document.getElementById('authResource'),
            providerId: document.getElementById('providerId'),
            authFields: document.getElementById('authFields'),
            jsonContent: document.getElementById('jsonContent'),
            docJsonContent: document.getElementById('docJsonContent')
        };

        setupEventListeners();
        updateEndpointBadge();
    }

    function setupEventListeners() {
        if (elements.searchBtn) elements.searchBtn.addEventListener('click', performSearch);
        if (elements.clearBtn) elements.clearBtn.addEventListener('click', clearSearch);
        if (elements.refreshBtn) elements.refreshBtn.addEventListener('click', performSearch);
        if (elements.exportBtn) elements.exportBtn.addEventListener('click', exportToCSV);
        if (elements.configBtn) elements.configBtn.addEventListener('click', openConfigModal);
        if (elements.inspectJsonBtn) elements.inspectJsonBtn.addEventListener('click', openJsonInspector);
        if (elements.columnsBtn) elements.columnsBtn.addEventListener('click', openColumnsModal);
        if (elements.quickSearch) elements.quickSearch.addEventListener('input', handleQuickFilter);
        if (elements.authEnabled) elements.authEnabled.addEventListener('change', toggleAuthFields);

        document.querySelectorAll('.search-field input, .search-field select').forEach(field => {
            field.addEventListener('keypress', (e) => {
                if (e.key === 'Enter') performSearch();
            });
        });

        document.addEventListener('keydown', (e) => {
            if (e.key === 'Escape') closeAllModals();
        });
    }

    // ==============================
    // Column Configuration
    // ==============================

    function loadColumnConfig() {
        try {
            const saved = localStorage.getItem(COLUMNS_KEY);
            if (saved) {
                const parsed = JSON.parse(saved);
                // Validate: only keep keys that exist in ALL_COLUMNS
                const valid = parsed.filter(k => ALL_COLUMNS[k]);
                if (valid.length > 0) {
                    state.visibleColumns = valid;
                }
            }
        } catch (e) {
            console.warn('Failed to load column config:', e);
        }
    }

    function saveColumnConfig() {
        try {
            localStorage.setItem(COLUMNS_KEY, JSON.stringify(state.visibleColumns));
        } catch (e) {
            console.warn('Failed to save column config:', e);
        }
    }

    function openColumnsModal() {
        const container = document.getElementById('columnsListContainer');
        if (!container) return;

        renderColumnsList(container);
        openModal('columnsModal');
    }

    function renderColumnsList(container) {
        // Build the drag-and-drop list: visible columns first (in order), then hidden ones
        const visibleSet = new Set(state.visibleColumns);
        const hiddenKeys = Object.keys(ALL_COLUMNS).filter(k => !visibleSet.has(k));

        let html = '<div class="columns-section"><h4>Visible Columns <small>(drag to reorder)</small></h4>';
        html += '<ul id="columnsSortable" class="columns-list">';
        for (const key of state.visibleColumns) {
            const col = ALL_COLUMNS[key];
            html += `
                <li class="column-item" data-key="${key}" draggable="true">
                    <span class="column-drag-handle">☰</span>
                    <label>
                        <input type="checkbox" checked data-col-key="${key}">
                        ${escapeHtml(col.label)}
                    </label>
                </li>`;
        }
        html += '</ul></div>';

        if (hiddenKeys.length > 0) {
            html += '<div class="columns-section"><h4>Hidden Columns</h4>';
            html += '<ul class="columns-list columns-hidden">';
            for (const key of hiddenKeys) {
                const col = ALL_COLUMNS[key];
                html += `
                    <li class="column-item column-item-hidden" data-key="${key}">
                        <span class="column-drag-handle" style="visibility:hidden">☰</span>
                        <label>
                            <input type="checkbox" data-col-key="${key}">
                            ${escapeHtml(col.label)}
                        </label>
                    </li>`;
            }
            html += '</ul></div>';
        }

        html += `<div class="columns-actions">
            <button type="button" class="btn-secondary btn-small" onclick="MadoViewer.resetColumns()">Reset to Default</button>
        </div>`;

        container.innerHTML = html;

        // Setup drag-and-drop for the sortable list
        setupColumnDragAndDrop();

        // Setup checkbox change listeners
        container.querySelectorAll('input[type="checkbox"]').forEach(cb => {
            cb.addEventListener('change', () => {
                applyColumnCheckboxes();
            });
        });
    }

    function setupColumnDragAndDrop() {
        const list = document.getElementById('columnsSortable');
        if (!list) return;

        let dragItem = null;

        list.querySelectorAll('.column-item').forEach(item => {
            item.addEventListener('dragstart', (e) => {
                dragItem = item;
                item.classList.add('dragging');
                e.dataTransfer.effectAllowed = 'move';
            });

            item.addEventListener('dragend', () => {
                item.classList.remove('dragging');
                dragItem = null;
                // Update state from DOM order
                updateColumnsFromDOM();
            });

            item.addEventListener('dragover', (e) => {
                e.preventDefault();
                e.dataTransfer.dropEffect = 'move';
                const afterElement = getDragAfterElement(list, e.clientY);
                if (afterElement == null) {
                    list.appendChild(dragItem);
                } else {
                    list.insertBefore(dragItem, afterElement);
                }
            });
        });
    }

    function getDragAfterElement(container, y) {
        const draggableElements = [...container.querySelectorAll('.column-item:not(.dragging)')];
        return draggableElements.reduce((closest, child) => {
            const box = child.getBoundingClientRect();
            const offset = y - box.top - box.height / 2;
            if (offset < 0 && offset > closest.offset) {
                return { offset: offset, element: child };
            } else {
                return closest;
            }
        }, { offset: Number.NEGATIVE_INFINITY }).element;
    }

    function updateColumnsFromDOM() {
        const list = document.getElementById('columnsSortable');
        if (!list) return;
        const newOrder = [];
        list.querySelectorAll('.column-item').forEach(item => {
            newOrder.push(item.dataset.key);
        });
        state.visibleColumns = newOrder;
        // Don't save yet - wait for "Apply" button
    }

    function applyColumnCheckboxes() {
        const container = document.getElementById('columnsListContainer');
        if (!container) return;

        const checked = [];
        const list = document.getElementById('columnsSortable');

        // First, get checked items from the sortable (visible) list in order
        if (list) {
            list.querySelectorAll('.column-item').forEach(item => {
                const cb = item.querySelector('input[type="checkbox"]');
                if (cb && cb.checked) {
                    checked.push(item.dataset.key);
                }
            });
        }

        // Then check hidden section for newly checked items
        container.querySelectorAll('.columns-hidden .column-item').forEach(item => {
            const cb = item.querySelector('input[type="checkbox"]');
            if (cb && cb.checked) {
                checked.push(item.dataset.key);
            }
        });

        state.visibleColumns = checked;
        // Re-render the list to move items between sections
        renderColumnsList(container);
    }

    function saveColumnsConfig() {
        saveColumnConfig();
        closeModal('columnsModal');
        sortAndRender();
        showToast('Column configuration saved', 'success');
    }

    function resetColumns() {
        state.visibleColumns = [...DEFAULT_COLUMNS];
        const container = document.getElementById('columnsListContainer');
        if (container) renderColumnsList(container);
        showToast('Columns reset to defaults', 'info');
    }

    // ==============================
    // Configuration Management
    // ==============================

    function loadConfig() {
        try {
            const saved = localStorage.getItem(CONFIG_KEY);
            if (saved) {
                state.config = { ...DEFAULT_CONFIG, ...JSON.parse(saved) };
            }
        } catch (e) {
            console.warn('Failed to load config:', e);
        }
    }

    function saveConfig() {
        const fhirEndpoint = elements.fhirEndpoint?.value.trim() || '';
        const ohifViewerUrl = elements.ohifViewerUrl?.value.trim() || DEFAULT_CONFIG.ohifViewerUrl;
        const authEnabled = !!elements.authEnabled?.checked;
        const tokenUrl = elements.tokenUrl?.value.trim() || '';
        const clientId = elements.clientId?.value.trim() || '';
        const clientSecret = elements.clientSecret?.value.trim() || '';
        const scope = elements.authScope?.value.trim() || '';
        const audience = elements.authAudience?.value.trim() || '';
        const resource = elements.authResource?.value.trim() || '';
        const providerId = elements.providerId?.value.trim() || '';

        state.config = {
            fhirEndpoint, ohifViewerUrl,
            authEnabled, tokenUrl, clientId, clientSecret, scope, audience, resource, providerId
        };
        // Invalidate any cached token when the config changes.
        state.tokenCache = null;

        try {
            localStorage.setItem(CONFIG_KEY, JSON.stringify(state.config));
            showToast('Configuration saved successfully', 'success');
        } catch (e) {
            showToast('Failed to save configuration', 'error');
        }

        closeModal('configModal');
        updateEndpointBadge();
    }

    function applyPreset(preset) {
        switch (preset) {
            case 'local':
                elements.fhirEndpoint.value = '';
                elements.ohifViewerUrl.value = 'https://ihebelgium.ehealthhub.be/ohif/mado';
                setAuthEnabled(false);
                break;
            case 'ihe':
                elements.fhirEndpoint.value = 'https://ihebelgium.ehealthhub.be/TheDICOMPolice/fhir';
                elements.ohifViewerUrl.value = 'https://ihebelgium.ehealthhub.be/ohif/mado';
                setAuthEnabled(false);
                break;
            case 'abrumet':
                elements.fhirEndpoint.value = 'https://fhir-qa.abrumet.plus/fhirstation-rest/api/fhir/';
                elements.tokenUrl.value = 'https://fhir-qa.abrumet.plus/auth/realms/fhir-station/protocol/openid-connect/token';
                elements.clientId.value = 'vzn-viewer-abrumet-acc';
                elements.clientSecret.value = '5MNRpkv1xWDhGagaDivuvt53ZhFGAWGt';
                elements.authScope.value = 'fhir-station-application';
                elements.authAudience.value = '';
                elements.authResource.value = '';
                if (elements.providerId) elements.providerId.value = '87082839962';
                setAuthEnabled(true);
                break;
        }
        showToast(`Applied ${preset} preset`, 'info');
    }

    function setAuthEnabled(enabled) {
        if (elements.authEnabled) elements.authEnabled.checked = enabled;
        toggleAuthFields();
    }

    function toggleAuthFields() {
        if (elements.authFields) {
            elements.authFields.style.display = elements.authEnabled?.checked ? 'block' : 'none';
        }
    }

    function openConfigModal() {
        elements.fhirEndpoint.value = state.config.fhirEndpoint || '';
        elements.ohifViewerUrl.value = state.config.ohifViewerUrl || DEFAULT_CONFIG.ohifViewerUrl;
        if (elements.authEnabled) elements.authEnabled.checked = !!state.config.authEnabled;
        if (elements.tokenUrl) elements.tokenUrl.value = state.config.tokenUrl || '';
        if (elements.clientId) elements.clientId.value = state.config.clientId || '';
        if (elements.clientSecret) elements.clientSecret.value = state.config.clientSecret || '';
        if (elements.authScope) elements.authScope.value = state.config.scope || '';
        if (elements.authAudience) elements.authAudience.value = state.config.audience || '';
        if (elements.authResource) elements.authResource.value = state.config.resource || '';
        if (elements.providerId) elements.providerId.value = state.config.providerId || '';
        toggleAuthFields();
        openModal('configModal');
    }

    function updateEndpointBadge() {
        if (elements.endpointBadge) {
            const endpoint = state.config.fhirEndpoint;
            const lock = state.config.authEnabled ? '🔐 ' : '';
            if (endpoint) {
                try {
                    const url = new URL(endpoint);
                    elements.endpointBadge.textContent = lock + url.hostname;
                    elements.endpointBadge.title = (state.config.authEnabled ? 'Authenticated • ' : '') + endpoint;
                } catch {
                    elements.endpointBadge.textContent = lock + endpoint;
                }
            } else {
                elements.endpointBadge.textContent = 'Local';
                elements.endpointBadge.title = 'Using local server';
            }
        }
    }

    // ==============================
    // Modal Management
    // ==============================

    function openModal(modalId) {
        const modal = document.getElementById(modalId);
        if (modal) {
            modal.style.display = 'flex';
            document.body.style.overflow = 'hidden';
        }
    }

    function closeModal(modalId) {
        const modal = document.getElementById(modalId);
        if (modal) {
            modal.style.display = 'none';
            document.body.style.overflow = '';
        }
    }

    function closeAllModals() {
        ['configModal', 'jsonModal', 'actionsModal', 'docJsonModal', 'columnsModal'].forEach(closeModal);
    }

    // ==============================
    // Authentication (OAuth2 client_credentials)
    // ==============================

    // Server-side reverse proxy endpoints (avoid browser CORS against the
    // secured FHIR endpoint and its Keycloak token endpoint).
    const PROXY_TOKEN_URL = './api/mhd-proxy/token';
    const PROXY_FHIR_URL = './api/mhd-proxy/fhir';

    /**
     * Obtain (and cache) an OAuth2 access token using the client_credentials grant.
     * The request is routed through the local server-side proxy so the browser is
     * never blocked by CORS. Returns null when authentication is disabled. The token
     * is cached until shortly before it expires to avoid a request on every FHIR call.
     */
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
        if (!data.access_token) {
            throw new Error('Token response did not contain an access_token');
        }
        const expiresInMs = (data.expires_in || 300) * 1000;
        state.tokenCache = { token: data.access_token, expiresAt: now + expiresInMs };
        return state.tokenCache.token;
    }

    /**
     * fetch() wrapper for FHIR endpoint calls. When OAuth2 auth is enabled, the request
     * is routed through the local server-side proxy (which forwards it to the secured
     * FHIR endpoint), and a Bearer token plus the fixed X-Provider header are attached.
     * When auth is disabled it behaves like a plain fetch, preserving the existing
     * local / no-auth backend behaviour.
     */
    async function fhirFetch(url, options = {}) {
        const headers = Object.assign({}, options.headers || {});
        if (state.config.authEnabled) {
            const token = await getAccessToken();
            if (token) headers['Authorization'] = 'Bearer ' + token;
            // Fixed provider header required by the secured FHIR endpoint.
            if (state.config.providerId) headers['X-Provider'] = state.config.providerId;
            // Route the call through the server-side proxy to avoid CORS.
            const proxiedUrl = `${PROXY_FHIR_URL}?target=${encodeURIComponent(url)}`;
            return fetch(proxiedUrl, Object.assign({}, options, { headers }));
        }
        return fetch(url, Object.assign({}, options, { headers }));
    }

    // ==============================
    // Search
    // ==============================

    async function performSearch() {
        showLoading();
        hideError();

        try {
            const params = new URLSearchParams();

            const patientId = elements.patientId.value.trim();
            if (patientId) params.append('patient.identifier', patientId);

            // The secured FHIR endpoint always requires a patient identifier (sent as X-Patient).
            if (state.config.authEnabled && !patientId) {
                hideError();
                showLoading();
                renderEmptyState('Patient ID required');
                elements.statsGrid.style.display = 'none';
                elements.pagination.style.display = 'none';
                showToast('A Patient ID is required when querying the authenticated FHIR endpoint', 'error');
                return;
            }

            const studyUid = elements.studyUid.value.trim();
            if (studyUid) params.append('study-instance-uid', studyUid);

            const accessionNumber = elements.accessionNumber.value.trim();
            if (accessionNumber) params.append('accession', accessionNumber);

            const modality = elements.modality.value;
            if (modality) params.append('modality', modality);

            const dateFrom = elements.dateFrom.value;
            const dateTo = elements.dateTo.value;
            if (dateFrom) params.append('date', `ge${dateFrom}`);
            if (dateTo) params.append('date', `le${dateTo}`);

            console.log('Search params:', params.toString());

            const baseUrl = (state.config.fhirEndpoint || './fhir').replace(/\/+$/, '');
            const url = `${baseUrl}/DocumentReference${params.toString() ? '?' + params.toString() : ''}`;

            const headers = { 'Accept': 'application/fhir+json' };
            // The secured FHIR endpoint requires the patient identifier on every request.
            if (state.config.authEnabled && patientId) headers['X-Patient'] = patientId;

            const response = await fhirFetch(url, { headers });

            if (!response.ok) {
                throw new Error(`HTTP ${response.status}: ${response.statusText}`);
            }

            const bundle = await response.json();
            state.rawBundle = bundle;
            state.allDocuments = parseDocumentBundle(bundle);
            state.filteredDocuments = [...state.allDocuments];
            state.currentPage = 1;

            updateStats();
            sortAndRender();

            if (state.allDocuments.length > 0) {
                const pairedCount = state.allDocuments.filter(d => d.fhirDoc && d.kosDoc).length;
                const fhirOnlyCount = state.allDocuments.filter(d => d.fhirDoc && !d.kosDoc).length;
                const kosOnlyCount = state.allDocuments.filter(d => !d.fhirDoc && d.kosDoc).length;
                const otherCount = state.allDocuments.filter(d => d.documentCategory === 'other').length;
                let msg = `Found ${state.allDocuments.length} document(s)`;
                if (pairedCount > 0) msg += `, ${pairedCount} paired (FHIR+KOS)`;
                if (fhirOnlyCount > 0) msg += `, ${fhirOnlyCount} FHIR-only`;
                if (kosOnlyCount > 0) msg += `, ${kosOnlyCount} KOS-only`;
                if (otherCount > 0) msg += `, ${otherCount} non-imaging`;
                showToast(msg, 'success');
            }

        } catch (error) {
            console.error('Search error:', error);
            showError(`Failed to fetch documents: ${error.message}`);
            state.allDocuments = [];
            state.filteredDocuments = [];
            state.rawBundle = null;
            renderEmptyState('Error loading data');
        }
    }

    // ==============================
    // Document Parsing
    // ==============================

    function parseSingleEntry(doc) {
        const patientId = doc.subject?.identifier?.value ||
                         doc.subject?.reference?.split('/').pop() || 'Unknown';
        const patientName = doc.subject?.display || 'Unknown Patient';
        const date = doc.date ? new Date(doc.date) : null;

        let modalities = [];
        const modalityExt = doc.extension?.find(e =>
            e.url === 'http://hl7.org/fhir/5.0/StructureDefinition/extension-DocumentReference.modality');
        if (modalityExt?.valueCodeableConcept?.coding) {
            modalities = modalityExt.valueCodeableConcept.coding.map(c => c.code).filter(Boolean);
        }
        if (modalities.length === 0) modalities = ['OT'];

        let bodySite = '';
        const bodySiteExt = doc.extension?.find(e =>
            e.url === 'http://hl7.org/fhir/5.0/StructureDefinition/extension-DocumentReference.bodySite');
        if (bodySiteExt) {
            const conceptExt = bodySiteExt.extension?.find(e => e.url === 'concept');
            if (conceptExt?.valueCodeableConcept) {
                const coding = conceptExt.valueCodeableConcept.coding;
                if (coding && coding.length > 0) {
                    bodySite = coding[0].display || coding[0].code || '';
                }
                if (!bodySite) {
                    bodySite = conceptExt.valueCodeableConcept.text || '';
                }
            }
        }

        const description = doc.description || doc.content?.[0]?.attachment?.title || 'No Description';

        let accessionNumber = '-';
        if (doc.context?.related) {
            const accRelated = doc.context.related.find(r =>
                r.identifier?.type?.coding?.some(c => c.code === '121022' || c.code === 'ACSN'));
            if (accRelated?.identifier?.value) accessionNumber = accRelated.identifier.value;
        }

        let studyUid = doc.masterIdentifier?.value || doc.id;
        if (doc.context?.related) {
            const studyUidRelated = doc.context.related.find(r =>
                r.identifier?.type?.coding?.some(c => c.code === '110180'));
            if (studyUidRelated?.identifier?.value) studyUid = studyUidRelated.identifier.value;
        }

        const formatCode = doc.content?.[0]?.format?.code || '';
        const contentType = doc.content?.[0]?.attachment?.contentType || '';
        const profiles = doc.meta?.profile || [];
        let manifestFormat = 'FHIR';
        let documentCategory = 'mado'; // 'mado' | 'other'
        if (formatCode === '1.2.840.10008.5.1.4.1.1.88.59' ||
            contentType === 'application/dicom' ||
            profiles.some(p => p.includes('MadoDicomKosDocumentReference'))) {
            manifestFormat = 'DICOM KOS';
        } else if (formatCode.includes('fhir-manifest') ||
                   contentType === 'application/fhir+json' ||
                   profiles.some(p => p.includes('MadoFhirDocumentReference'))) {
            manifestFormat = 'FHIR';
        } else {
            // Non-MADO document types
            documentCategory = 'other';
            if (contentType === 'application/pdf') {
                manifestFormat = 'PDF';
            } else if (contentType === 'text/xml' || contentType === 'application/xml' ||
                       contentType === 'application/hl7-v3+xml' || contentType === 'application/cda+xml') {
                manifestFormat = 'CDA';
            } else if (contentType === 'application/json' || contentType === 'application/fhir+json') {
                manifestFormat = 'FHIR JSON';
            } else if (contentType === 'text/html') {
                manifestFormat = 'HTML';
            } else if (contentType === 'text/plain') {
                manifestFormat = 'Text';
            } else if (contentType && contentType.startsWith('image/')) {
                manifestFormat = 'Image';
            } else if (contentType) {
                manifestFormat = contentType.split('/').pop().toUpperCase();
            } else {
                manifestFormat = 'Other';
            }
        }

        const binaryUrl = doc.content?.[0]?.attachment?.url || '';

        let author = 'Unknown';
        if (doc.author && doc.author.length > 0) {
            const authorParts = doc.author.map(a => a.display).filter(Boolean);
            author = authorParts.join(', ') || 'Unknown';
        }

        const institutionName = doc.custodian?.display || '';

        let relatesToRef = null;
        if (doc.relatesTo && doc.relatesTo.length > 0) {
            const kosRelation = doc.relatesTo.find(r => r.code === 'transforms');
            if (kosRelation?.target?.reference) relatesToRef = kosRelation.target.reference;
        }

        return {
            id: doc.id, studyUid, patientId, patientName, date,
            dateString: date ? formatDate(date) : '-',
            modalities, bodySite, description, accessionNumber, author,
            institutionName, binaryUrl, manifestFormat, documentCategory,
            contentType, relatesToRef,
            size: doc.content?.[0]?.attachment?.size || 0,
            rawResource: doc
        };
    }

    function parseDocumentBundle(bundle) {
        if (!bundle || !bundle.entry || bundle.entry.length === 0) return [];

        const allEntries = bundle.entry.map(entry => parseSingleEntry(entry.resource));
        const fhirEntries = allEntries.filter(e => e.documentCategory === 'mado' && e.manifestFormat === 'FHIR');
        const kosEntries = allEntries.filter(e => e.documentCategory === 'mado' && e.manifestFormat === 'DICOM KOS');
        const otherEntries = allEntries.filter(e => e.documentCategory === 'other');

        const kosById = new Map();
        kosEntries.forEach(k => kosById.set(k.id, k));

        const pairedKosIds = new Set();
        const combined = [];

        for (const fhir of fhirEntries) {
            let kosMatch = null;
            if (fhir.relatesToRef) {
                const kosId = fhir.relatesToRef.replace('DocumentReference/', '');
                kosMatch = kosById.get(kosId) || null;
            }
            if (!kosMatch) {
                kosMatch = kosEntries.find(k => !pairedKosIds.has(k.id) && k.studyUid === fhir.studyUid);
            }
            if (kosMatch) pairedKosIds.add(kosMatch.id);
            combined.push(createCombinedEntry(fhir, kosMatch));
        }

        for (const kos of kosEntries) {
            if (!pairedKosIds.has(kos.id)) {
                combined.push(createCombinedEntry(null, kos));
            }
        }

        // Add non-MADO documents as standalone entries
        for (const other of otherEntries) {
            combined.push(createOtherEntry(other));
        }

        return combined;
    }

    function createCombinedEntry(fhirDoc, kosDoc) {
        const primary = fhirDoc || kosDoc;
        return {
            studyUid: primary.studyUid, patientId: primary.patientId,
            patientName: primary.patientName, date: primary.date,
            dateString: primary.dateString, modalities: primary.modalities,
            bodySite: primary.bodySite, description: primary.description,
            accessionNumber: primary.accessionNumber, author: primary.author,
            institutionName: primary.institutionName,
            fhirDoc: fhirDoc || null, kosDoc: kosDoc || null,
            hasFhir: !!fhirDoc, hasKos: !!kosDoc,
            isPaired: !!(fhirDoc && kosDoc),
            binaryUrl: kosDoc?.binaryUrl || fhirDoc?.binaryUrl || '',
            fhirBinaryUrl: fhirDoc?.binaryUrl || '',
            kosBinaryUrl: kosDoc?.binaryUrl || '',
            manifestFormat: fhirDoc && kosDoc ? 'FHIR+KOS' : (fhirDoc ? 'FHIR' : 'DICOM KOS'),
            size: (fhirDoc?.size || 0) + (kosDoc?.size || 0),
            rawFhirResource: fhirDoc?.rawResource || null,
            rawKosResource: kosDoc?.rawResource || null,
            documentCategory: 'mado',
            contentType: primary.contentType || ''
        };
    }

    function createOtherEntry(entry) {
        return {
            studyUid: entry.studyUid, patientId: entry.patientId,
            patientName: entry.patientName, date: entry.date,
            dateString: entry.dateString, modalities: entry.modalities,
            bodySite: entry.bodySite, description: entry.description,
            accessionNumber: entry.accessionNumber, author: entry.author,
            institutionName: entry.institutionName,
            fhirDoc: null, kosDoc: null,
            hasFhir: false, hasKos: false, isPaired: false,
            binaryUrl: entry.binaryUrl || '',
            fhirBinaryUrl: '', kosBinaryUrl: '',
            manifestFormat: entry.manifestFormat,
            size: entry.size || 0,
            rawFhirResource: null, rawKosResource: null,
            rawOtherResource: entry.rawResource,
            documentCategory: 'other',
            contentType: entry.contentType || ''
        };
    }

    // ==============================
    // Stats, Filter, Sort
    // ==============================

    function updateStats() {
        const docs = state.allDocuments;
        if (docs.length === 0) { elements.statsGrid.style.display = 'none'; return; }

        elements.statsGrid.style.display = 'grid';
        const pairedCount = docs.filter(d => d.isPaired).length;
        document.getElementById('totalCount').textContent = `${docs.length} (${pairedCount} paired)`;

        const uniquePatients = new Set(docs.map(d => d.patientId));
        document.getElementById('patientCount').textContent = uniquePatients.size;

        const uniqueModalities = new Set(docs.flatMap(d => d.modalities));
        document.getElementById('modalityCount').textContent = uniqueModalities.size;

        const dates = docs.map(d => d.date).filter(Boolean).sort((a, b) => a - b);
        if (dates.length > 0) {
            const minDate = formatDate(dates[0]);
            const maxDate = formatDate(dates[dates.length - 1]);
            document.getElementById('dateRange').textContent = dates.length === 1 ? minDate : `${minDate} - ${maxDate}`;
        } else {
            document.getElementById('dateRange').textContent = '-';
        }
    }

    function handleQuickFilter() {
        const query = elements.quickSearch.value.toLowerCase().trim();
        if (!query) {
            state.filteredDocuments = [...state.allDocuments];
        } else {
            state.filteredDocuments = state.allDocuments.filter(doc => {
                return doc.patientId.toLowerCase().includes(query) ||
                       doc.patientName.toLowerCase().includes(query) ||
                       doc.description.toLowerCase().includes(query) ||
                       doc.accessionNumber.toLowerCase().includes(query) ||
                       doc.bodySite.toLowerCase().includes(query) ||
                       doc.modalities.some(m => m.toLowerCase().includes(query)) ||
                       doc.manifestFormat.toLowerCase().includes(query) ||
                       doc.institutionName.toLowerCase().includes(query) ||
                       doc.author.toLowerCase().includes(query) ||
                       doc.studyUid.toLowerCase().includes(query) ||
                       (doc.contentType || '').toLowerCase().includes(query);
            });
        }
        state.currentPage = 1;
        sortAndRender();
    }

    function sortAndRender() {
        const sorted = [...state.filteredDocuments].sort((a, b) => {
            let aVal = a[state.sortColumn];
            let bVal = b[state.sortColumn];
            if (state.sortColumn === 'date') {
                aVal = a.date?.getTime() || 0;
                bVal = b.date?.getTime() || 0;
            }
            if (typeof aVal === 'string') {
                aVal = aVal.toLowerCase();
                bVal = bVal?.toLowerCase() || '';
            }
            if (state.sortDirection === 'asc') {
                return aVal > bVal ? 1 : aVal < bVal ? -1 : 0;
            } else {
                return aVal < bVal ? 1 : aVal > bVal ? -1 : 0;
            }
        });
        state.displayedDocuments = sorted;
        renderTable(sorted);
    }

    // ==============================
    // Table Rendering (dynamic columns)
    // ==============================

    function renderTable(documents) {
        if (documents.length === 0) {
            renderEmptyState(elements.quickSearch.value ? 'No matching results' : 'No documents found');
            elements.pagination.style.display = 'none';
            return;
        }

        const totalPages = Math.ceil(documents.length / state.pageSize);
        const startIdx = (state.currentPage - 1) * state.pageSize;
        const endIdx = Math.min(startIdx + state.pageSize, documents.length);
        const pageDocuments = documents.slice(startIdx, endIdx);

        const cols = state.visibleColumns;

        // Build header
        let headerHTML = '';
        for (const key of cols) {
            const col = ALL_COLUMNS[key];
            if (col) {
                headerHTML += `<th data-sort="${col.sortKey}">${escapeHtml(col.label)}<span class="sort-indicator"></span></th>`;
            }
        }
        headerHTML += '<th>Actions</th>';

        // Build rows
        let bodyHTML = '';
        for (let idx = 0; idx < pageDocuments.length; idx++) {
            const doc = pageDocuments[idx];
            const globalIdx = startIdx + idx;
            const isOther = doc.documentCategory === 'other';
            const rowClass = isOther ? 'other-row' : (doc.isPaired ? 'paired-row' : (doc.hasKos ? 'kos-row' : 'fhir-row'));

            bodyHTML += `<tr data-doc-index="${globalIdx}" class="${rowClass}">`;
            for (const key of cols) {
                const col = ALL_COLUMNS[key];
                if (col) {
                    bodyHTML += col.render(doc, escapeHtml);
                }
            }
            if (isOther) {
                bodyHTML += `<td class="action-cell">
                    <div class="action-buttons">
                        <button class="action-btn download" onclick="MadoViewer.quickAction('download-other', ${globalIdx}, event)" title="Download Document">💾</button>
                        ${isJsonInspectable(doc) ? `<button class="action-btn view" onclick="MadoViewer.quickAction('inspect-other', ${globalIdx}, event)" title="Inspect JSON">📋</button>` : ''}
                        <button class="action-btn more" onclick="MadoViewer.quickAction('inspect-docref', ${globalIdx}, event)" title="Inspect DocumentReference">🔍</button>
                    </div>
                </td>`;
            } else {
                bodyHTML += `<td class="action-cell">
                    <div class="action-buttons">
                        <button class="action-btn view" onclick="MadoViewer.quickAction('view', ${globalIdx}, event)" title="View in OHIF (uses KOS)">👁️</button>
                        <button class="action-btn download" onclick="MadoViewer.quickAction('download', ${globalIdx}, event)" title="Download DICOM">💾</button>
                        <button class="action-btn validate" onclick="MadoViewer.quickAction('validate', ${globalIdx}, event)" title="Validate MADO">✅</button>
                        <button class="action-btn bridge" onclick="MadoViewer.quickAction('bridge', ${globalIdx}, event)" title="DICOM↔FHIR Bridge">🔄</button>
                        <button class="action-btn more" onclick="MadoViewer.openActionsModal(${globalIdx}, event)" title="More Actions">⋯</button>
                    </div>
                </td>`;
            }
            bodyHTML += '</tr>';
        }

        const tableHTML = `
            <div class="table-wrapper">
                <table class="data-table">
                    <thead><tr>${headerHTML}</tr></thead>
                    <tbody>${bodyHTML}</tbody>
                </table>
            </div>`;

        elements.tableContent.innerHTML = tableHTML;

        // Sort listeners
        document.querySelectorAll('.data-table th[data-sort]').forEach(th => {
            th.addEventListener('click', () => {
                const column = th.dataset.sort;
                if (state.sortColumn === column) {
                    state.sortDirection = state.sortDirection === 'asc' ? 'desc' : 'asc';
                } else {
                    state.sortColumn = column;
                    state.sortDirection = 'desc';
                }
                document.querySelectorAll('.data-table th').forEach(h => h.classList.remove('sorted-asc', 'sorted-desc'));
                th.classList.add(`sorted-${state.sortDirection}`);
                sortAndRender();
            });
        });

        const currentSortTh = document.querySelector(`th[data-sort="${state.sortColumn}"]`);
        if (currentSortTh) currentSortTh.classList.add(`sorted-${state.sortDirection}`);

        renderPagination(documents.length, startIdx, endIdx, totalPages);
    }

    function renderPagination(total, startIdx, endIdx, totalPages) {
        elements.pagination.style.display = 'flex';
        document.getElementById('showingRange').textContent = `${startIdx + 1}-${endIdx}`;
        document.getElementById('totalEntries').textContent = total;

        let html = `<button class="page-btn" ${state.currentPage === 1 ? 'disabled' : ''} onclick="goToPage(${state.currentPage - 1})">← Previous</button>`;

        const maxVisible = 7;
        let startPage = Math.max(1, state.currentPage - Math.floor(maxVisible / 2));
        let endPage = Math.min(totalPages, startPage + maxVisible - 1);
        if (endPage - startPage < maxVisible - 1) startPage = Math.max(1, endPage - maxVisible + 1);

        if (startPage > 1) {
            html += `<button class="page-btn" onclick="goToPage(1)">1</button>`;
            if (startPage > 2) html += `<span style="padding: 8px;">...</span>`;
        }
        for (let i = startPage; i <= endPage; i++) {
            html += `<button class="page-btn ${i === state.currentPage ? 'active' : ''}" onclick="goToPage(${i})">${i}</button>`;
        }
        if (endPage < totalPages) {
            if (endPage < totalPages - 1) html += `<span style="padding: 8px;">...</span>`;
            html += `<button class="page-btn" onclick="goToPage(${totalPages})">${totalPages}</button>`;
        }

        html += `<button class="page-btn" ${state.currentPage === totalPages ? 'disabled' : ''} onclick="goToPage(${state.currentPage + 1})">Next →</button>`;
        elements.paginationControls.innerHTML = html;
    }

    function goToPage(page) {
        state.currentPage = page;
        sortAndRender();
        window.scrollTo({ top: 0, behavior: 'smooth' });
    }

    function renderEmptyState(message) {
        elements.tableContent.innerHTML = `
            <div class="empty-state">
                <div class="empty-state-icon">📭</div>
                <div class="empty-state-title">${message}</div>
                <div class="empty-state-text">Try adjusting your search criteria</div>
            </div>`;
    }

    function showLoading() {
        elements.tableContent.innerHTML = `
            <div class="loading-overlay">
                <div class="loading-spinner">
                    <div class="spinner"></div>
                    <p style="color: var(--text-secondary); font-weight: 500;">Loading documents...</p>
                </div>
            </div>`;
        elements.pagination.style.display = 'none';
        elements.statsGrid.style.display = 'none';
    }

    function clearSearch() {
        elements.patientId.value = '';
        elements.studyUid.value = '';
        elements.accessionNumber.value = '';
        elements.modality.value = '';
        elements.dateFrom.value = '';
        elements.dateTo.value = '';
        elements.quickSearch.value = '';
        state.allDocuments = [];
        state.filteredDocuments = [];
        state.currentPage = 1;
        renderEmptyState('Ready to Search');
        elements.statsGrid.style.display = 'none';
        elements.pagination.style.display = 'none';
    }

    // ==============================
    // OHIF Viewer
    // ==============================

    function openMADOViewer(binaryUrl, event) {
        if (event) event.stopPropagation();
        if (!binaryUrl) { showToast('No manifest URL available for this document', 'error'); return; }
        const fullBinaryUrl = binaryUrl.startsWith('http') ? binaryUrl : window.location.origin + '/' + binaryUrl.replace(/^\.\//, '');
        const ohifBaseUrl = state.config.ohifViewerUrl || DEFAULT_CONFIG.ohifViewerUrl;
        window.open(`${ohifBaseUrl}?manifestUrl=${encodeURIComponent(fullBinaryUrl)}`, '_blank');
    }

    // ==============================
    // Quick Actions
    // ==============================

    function quickAction(action, docIndex, event) {
        if (event) event.stopPropagation();
        const doc = state.displayedDocuments?.[docIndex] || state.filteredDocuments[docIndex];
        if (!doc) { showToast('Document not found', 'error'); return; }
        state.selectedDocument = doc;
        executeAction(action);
    }

    function openActionsModal(docIndex, event) {
        if (event) event.stopPropagation();
        const doc = state.displayedDocuments?.[docIndex] || state.filteredDocuments[docIndex];
        if (!doc) { showToast('Document not found', 'error'); return; }
        state.selectedDocument = doc;

        const preview = document.getElementById('documentPreview');
        if (preview) {
            preview.innerHTML = `
                <div class="document-preview-title">${escapeHtml(doc.description)}</div>
                <div class="document-preview-meta">
                    <span><strong>Patient:</strong> ${escapeHtml(doc.patientName)} (${escapeHtml(doc.patientId)})</span>
                    <span><strong>Date:</strong> ${doc.dateString}</span>
                    <span><strong>Modality:</strong> ${doc.modalities.join(', ')}</span>
                    <span><strong>Body Site:</strong> ${escapeHtml(doc.bodySite) || '-'}</span>
                    <span><strong>Accession:</strong> ${escapeHtml(doc.accessionNumber)}</span>
                    <span><strong>Institution:</strong> ${escapeHtml(doc.institutionName) || '-'}</span>
                    <span><strong>Available Formats:</strong>
                        ${doc.documentCategory === 'other'
                            ? '<span class="format-badge format-other">' + getFormatIcon(doc.manifestFormat) + ' ' + escapeHtml(doc.manifestFormat) + '</span>'
                            : (doc.hasFhir ? '<span class="format-badge format-fhir">🔥 FHIR</span>' : '') +
                              (doc.hasKos ? '<span class="format-badge format-kos">🩻 KOS</span>' : '') +
                              (doc.isPaired ? '<span class="linked-badge">🔗 Linked</span>' : '')}
                    </span>
                </div>`;
        }

        const actionsGrid = document.getElementById('actionsGridContent');
        if (actionsGrid) {
            let h = '';
            if (doc.documentCategory === 'other') {
                // Non-MADO document actions
                h += actionCard('download-other', '💾', 'Download Document', `Download ${doc.manifestFormat} document`);
                h += actionCard('inspect-docref', '🔍', 'Inspect DocumentReference', 'View raw FHIR DocumentReference JSON');
                if (isJsonInspectable(doc)) {
                    h += actionCard('inspect-other', '📋', 'Inspect Content JSON', 'View the JSON content of the document');
                }
            } else {
                // MADO document actions
                if (doc.hasKos)  h += actionCard('view', '👁️', 'View in OHIF', 'Open DICOM KOS manifest in OHIF viewer');
                if (doc.hasKos)  h += actionCard('download-kos', '💾', 'Download DICOM KOS', 'Download KOS manifest and images');
                if (doc.hasFhir) h += actionCard('download-fhir', '💾', 'Download FHIR Manifest', 'Download FHIR ImagingStudy manifest');
                if (doc.hasKos)  h += actionCard('validate-kos', '✅', 'Validate DICOM KOS', 'Validate KOS against DICOM/IHE profiles');
                if (doc.hasFhir) h += actionCard('validate-fhir', '✅', 'Validate FHIR Manifest', 'Validate FHIR manifest against profiles');
                if (doc.hasKos)  h += actionCard('bridge-kos', '🔄', 'Bridge from KOS', 'Convert DICOM KOS to FHIR');
                if (doc.hasFhir) h += actionCard('bridge-fhir', '🔄', 'Bridge from FHIR', 'Convert FHIR manifest to DICOM');
                if (doc.hasFhir) h += actionCard('inspect-fhir', '📋', 'Inspect FHIR JSON', 'View raw FHIR DocumentReference');
                if (doc.hasKos)  h += actionCard('inspect-kos', '📋', 'Inspect KOS JSON', 'View raw KOS DocumentReference');
            }
            actionsGrid.innerHTML = h;
        }

        openModal('actionsModal');
    }

    function actionCard(action, icon, title, desc) {
        return `<button class="action-card" onclick="MadoViewer.executeAction('${action}')">
            <span class="action-icon">${icon}</span>
            <span class="action-title">${title}</span>
            <span class="action-desc">${desc}</span>
        </button>`;
    }

    function executeAction(action) {
        const doc = state.selectedDocument;
        if (!doc) { showToast('❌ No document selected', 'error'); return; }
        closeModal('actionsModal');

        switch (action) {
            case 'view':          openMADOViewer(doc.kosBinaryUrl || doc.binaryUrl); break;
            case 'download':      navigateToDownloader(doc, doc.kosBinaryUrl || doc.fhirBinaryUrl); break;
            case 'download-kos':  navigateToDownloader(doc, doc.kosBinaryUrl); break;
            case 'download-fhir': navigateToDownloader(doc, doc.fhirBinaryUrl); break;
            case 'validate':      navigateToValidator(doc, doc.kosBinaryUrl || doc.fhirBinaryUrl); break;
            case 'validate-kos':  navigateToValidator(doc, doc.kosBinaryUrl); break;
            case 'validate-fhir': navigateToValidator(doc, doc.fhirBinaryUrl); break;
            case 'bridge':        navigateToBridge(doc, doc.kosBinaryUrl || doc.fhirBinaryUrl); break;
            case 'bridge-kos':    navigateToBridge(doc, doc.kosBinaryUrl); break;
            case 'bridge-fhir':   navigateToBridge(doc, doc.fhirBinaryUrl); break;
            case 'inspect':       openDocJsonInspector(doc.rawFhirResource || doc.rawKosResource); break;
            case 'inspect-fhir':  openDocJsonInspector(doc.rawFhirResource); break;
            case 'inspect-kos':   openDocJsonInspector(doc.rawKosResource); break;
            case 'download-other': downloadOtherDocument(doc); break;
            case 'inspect-other':  inspectOtherContent(doc); break;
            case 'inspect-docref': openDocJsonInspector(doc.rawOtherResource || doc.rawFhirResource || doc.rawKosResource); break;
            default: showToast(`Unknown action: "${action}"`, 'error');
        }
    }

    function makeFullUrl(url) {
        return url.startsWith('http') ? url : window.location.origin + '/' + url.replace(/^\.\//, '');
    }

    function navigateToValidator(doc, url) {
        if (!url) { showToast('No manifest URL available for validation', 'error'); return; }
        try { sessionStorage.setItem('madoValidationData', JSON.stringify({ url, description: doc.description, patientId: doc.patientId, studyUid: doc.studyUid })); } catch (e) {}
        window.open(`./?loadUrl=${encodeURIComponent(makeFullUrl(url))}`, '_blank');
        showToast('Opening validator...', 'info');
    }

    function navigateToBridge(doc, url) {
        if (!url) { showToast('No manifest URL available', 'error'); return; }
        try { sessionStorage.setItem('madoBridgeData', JSON.stringify({ url, description: doc.description, patientId: doc.patientId, studyUid: doc.studyUid })); } catch (e) {}
        window.open(`./converter?loadUrl=${encodeURIComponent(makeFullUrl(url))}`, '_blank');
        showToast('Opening DICOM↔FHIR Bridge...', 'info');
    }

    function navigateToDownloader(doc, url) {
        if (!url) { showToast('No manifest URL available', 'error'); return; }
        try { sessionStorage.setItem('madoDownloaderData', JSON.stringify({ url, description: doc.description, patientId: doc.patientId, studyUid: doc.studyUid })); } catch (e) {}
        window.open(`./dicom-downloader?manifestUrl=${encodeURIComponent(makeFullUrl(url))}`, '_blank');
        showToast('Opening DICOM Downloader...', 'info');
    }

    // ==============================
    // Non-MADO Document Actions
    // ==============================

    function downloadOtherDocument(doc) {
        const url = doc.binaryUrl;
        if (!url) {
            showToast('No download URL available for this document', 'error');
            return;
        }
        const fullUrl = makeFullUrl(url);
        const ext = getFileExtension(doc.manifestFormat, doc.contentType);
        const filename = `document-${doc.studyUid || doc.description || 'unknown'}${ext}`;

        // Try fetching and downloading as blob
        const fetchOpts = {};
        if (state.config.authEnabled && doc.patientId) fetchOpts.headers = { 'X-Patient': doc.patientId };
        fhirFetch(fullUrl, fetchOpts)
            .then(resp => {
                if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
                return resp.blob();
            })
            .then(blob => {
                const link = document.createElement('a');
                link.href = URL.createObjectURL(blob);
                link.download = filename;
                link.click();
                URL.revokeObjectURL(link.href);
                showToast(`Downloaded ${doc.manifestFormat} document`, 'success');
            })
            .catch(err => {
                // Fallback: open in new tab
                window.open(fullUrl, '_blank');
                showToast('Opening document in new tab...', 'info');
            });
    }

    function getFileExtension(format, contentType) {
        const extMap = {
            'PDF': '.pdf', 'CDA': '.xml', 'HTML': '.html', 'Text': '.txt',
            'FHIR JSON': '.json', 'Image': '.png'
        };
        if (extMap[format]) return extMap[format];
        // Try from content type
        const ctMap = {
            'application/pdf': '.pdf', 'text/xml': '.xml', 'application/xml': '.xml',
            'application/json': '.json', 'application/fhir+json': '.json',
            'text/html': '.html', 'text/plain': '.txt',
            'image/png': '.png', 'image/jpeg': '.jpg'
        };
        return ctMap[contentType] || '';
    }

    async function inspectOtherContent(doc) {
        const url = doc.binaryUrl;
        if (!url) {
            showToast('No content URL available for this document', 'error');
            return;
        }
        try {
            const fullUrl = makeFullUrl(url);
            const inspectHeaders = { 'Accept': 'application/json, application/fhir+json' };
            if (state.config.authEnabled && doc.patientId) inspectHeaders['X-Patient'] = doc.patientId;
            const resp = await fhirFetch(fullUrl, {
                headers: inspectHeaders
            });
            if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
            const json = await resp.json();
            renderJsonContent(json, 'docJsonContent');
            openModal('docJsonModal');
        } catch (err) {
            showToast(`Failed to load JSON content: ${err.message}`, 'error');
        }
    }

    // ==============================
    // JSON Inspector
    // ==============================

    function openJsonInspector() {
        if (!state.rawBundle) { showToast('No data available. Please perform a search first.', 'error'); return; }
        renderJsonContent(state.rawBundle, 'jsonContent');
        updateJsonStats(state.rawBundle, state.allDocuments.length);
        openModal('jsonModal');
    }

    function openDocJsonInspector(rawResource) {
        if (!rawResource) { showToast('No document data available for this format', 'error'); return; }
        renderJsonContent(rawResource, 'docJsonContent');
        openModal('docJsonModal');
    }

    function renderJsonContent(data, elementId) {
        const container = document.getElementById(elementId);
        if (!container) return;
        if (state.jsonViewMode === 'formatted') {
            container.innerHTML = syntaxHighlight(JSON.stringify(data, null, 2));
        } else {
            container.textContent = JSON.stringify(data);
        }
    }

    function setJsonView(mode) {
        state.jsonViewMode = mode;
        document.getElementById('viewFormattedBtn')?.classList.toggle('active', mode === 'formatted');
        document.getElementById('viewRawBtn')?.classList.toggle('active', mode === 'raw');
        if (state.rawBundle) renderJsonContent(state.rawBundle, 'jsonContent');
    }

    function syntaxHighlight(json) {
        return json
            .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
            .replace(/("(\\u[a-zA-Z0-9]{4}|\\[^u]|[^\\"])*"(\s*:)?|\b(true|false|null)\b|-?\d+(?:\.\d*)?(?:[eE][+\-]?\d+)?)/g, function(match) {
                let cls = 'number';
                if (/^"/.test(match)) {
                    if (/:$/.test(match)) { cls = 'key'; match = match.slice(0, -1) + '</span>:'; return '<span class="' + cls + '">' + match; }
                    else { cls = 'string'; }
                } else if (/true|false/.test(match)) { cls = 'boolean'; }
                else if (/null/.test(match)) { cls = 'null'; }
                return '<span class="' + cls + '">' + match + '</span>';
            });
    }

    function updateJsonStats(bundle, entryCount) {
        document.getElementById('jsonEntryCount').textContent = `${entryCount} entries`;
        const sizeKb = (new Blob([JSON.stringify(bundle)]).size / 1024).toFixed(1);
        document.getElementById('jsonSize').textContent = `${sizeKb} KB`;
    }

    function copyJson() {
        if (!state.rawBundle) return;
        navigator.clipboard.writeText(JSON.stringify(state.rawBundle, null, 2))
            .then(() => showToast('JSON copied to clipboard', 'success'))
            .catch(() => showToast('Failed to copy JSON', 'error'));
    }

    function downloadJson() {
        if (!state.rawBundle) return;
        const blob = new Blob([JSON.stringify(state.rawBundle, null, 2)], { type: 'application/json' });
        const link = document.createElement('a');
        link.href = URL.createObjectURL(blob);
        link.download = `mhd-bundle-${new Date().toISOString().split('T')[0]}.json`;
        link.click();
        showToast('JSON downloaded', 'success');
    }

    function copyDocJson() {
        if (!state.selectedDocument) return;
        const raw = state.selectedDocument.rawFhirResource || state.selectedDocument.rawKosResource;
        if (!raw) return;
        navigator.clipboard.writeText(JSON.stringify(raw, null, 2))
            .then(() => showToast('DocumentReference JSON copied', 'success'))
            .catch(() => showToast('Failed to copy JSON', 'error'));
    }

    function downloadDocJson() {
        if (!state.selectedDocument) return;
        const raw = state.selectedDocument.rawFhirResource || state.selectedDocument.rawKosResource;
        if (!raw) return;
        const blob = new Blob([JSON.stringify(raw, null, 2)], { type: 'application/json' });
        const link = document.createElement('a');
        link.href = URL.createObjectURL(blob);
        link.download = `document-reference-${state.selectedDocument.studyUid || 'unknown'}.json`;
        link.click();
        showToast('DocumentReference JSON downloaded', 'success');
    }

    // ==============================
    // CSV Export (uses visible columns)
    // ==============================

    function exportToCSV() {
        if (state.filteredDocuments.length === 0) { alert('No data to export'); return; }

        // Map column keys to CSV-friendly extractors
        const csvExtractors = {
            date: doc => doc.dateString,
            modalities: doc => doc.modalities.join('; '),
            bodySite: doc => doc.bodySite,
            description: doc => doc.description,
            accessionNumber: doc => doc.accessionNumber,
            institutionName: doc => doc.institutionName,
            manifestFormat: doc => doc.manifestFormat + (doc.documentCategory === 'other' ? ' (' + (doc.contentType || '') + ')' : ''),
            author: doc => doc.author,
            studyUid: doc => doc.studyUid,
            patientId: doc => doc.patientId,
            patientName: doc => doc.patientName,
            size: doc => doc.size ? String(doc.size) : ''
        };

        const cols = state.visibleColumns;
        const headers = cols.map(k => ALL_COLUMNS[k]?.label || k);
        const rows = state.filteredDocuments.map(doc =>
            cols.map(k => (csvExtractors[k] || (() => ''))(doc))
        );

        const csv = [headers, ...rows]
            .map(row => row.map(cell => `"${String(cell).replace(/"/g, '""')}"`).join(','))
            .join('\n');

        const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
        const link = document.createElement('a');
        link.href = URL.createObjectURL(blob);
        link.download = `mado-documents-${new Date().toISOString().split('T')[0]}.csv`;
        link.click();
    }

    // ==============================
    // Toast / Error / Utility
    // ==============================

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

    function showError(message) {
        elements.errorBanner.textContent = `⚠️ ${message}`;
        elements.errorBanner.style.display = 'block';
    }

    function hideError() { elements.errorBanner.style.display = 'none'; }

    function formatDate(date) {
        if (!date) return '-';
        const d = new Date(date);
        const year = d.getFullYear();
        const month = String(d.getMonth() + 1).padStart(2, '0');
        const day = String(d.getDate()).padStart(2, '0');
        const hours = String(d.getHours()).padStart(2, '0');
        const minutes = String(d.getMinutes()).padStart(2, '0');
        return `${year}-${month}-${day} ${hours}:${minutes}`;
    }

    function escapeHtml(text) {
        if (text === null || text === undefined) return '';
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    function getFormatIcon(format) {
        const icons = {
            'PDF': '📕', 'CDA': '📋', 'FHIR JSON': '🔥', 'HTML': '🌐',
            'Text': '📝', 'Image': '🖼️', 'Other': '📎'
        };
        return icons[format] || '📎';
    }

    function isJsonInspectable(doc) {
        return doc.contentType === 'application/json' ||
               doc.contentType === 'application/fhir+json' ||
               doc.manifestFormat === 'FHIR JSON';
    }

    // ==============================
    // Public API
    // ==============================

    return {
        init, openMADOViewer, goToPage,
        openModal, closeModal,
        saveConfig, applyPreset,
        quickAction, openActionsModal, executeAction,
        setJsonView, copyJson, downloadJson, copyDocJson, downloadDocJson,
        // Column configuration
        saveColumnsConfig, resetColumns
    };
})();

// Initialize on load
document.addEventListener('DOMContentLoaded', MadoViewer.init);

// Expose global functions for inline event handlers
window.openMADOViewer = MadoViewer.openMADOViewer;
window.goToPage = MadoViewer.goToPage;

