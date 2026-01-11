/**
 * MADO Viewer Module
 * Handles searching and viewing MADO manifests with advanced actions
 */

const MadoViewer = (function() {
    // Configuration defaults
    const CONFIG_KEY = 'madoViewerConfig';
    const DEFAULT_CONFIG = {
        fhirEndpoint: '',  // Empty means use local
        ohifViewerUrl: 'https://ihebelgium.ehealthhub.be/ohif/mado'
    };

    // Application State
    const state = {
        allDocuments: [],
        filteredDocuments: [],
        rawBundle: null,  // Store raw FHIR Bundle for inspection
        currentPage: 1,
        pageSize: 50,
        sortColumn: 'date',
        sortDirection: 'desc',
        selectedDocument: null,  // For actions modal
        jsonViewMode: 'formatted',
        config: { ...DEFAULT_CONFIG }
    };

    // DOM Elements
    let elements = {};

    function init() {
        // Load saved configuration
        loadConfig();

        // Initialize DOM elements
        elements = {
            searchBtn: document.getElementById('searchBtn'),
            clearBtn: document.getElementById('clearBtn'),
            refreshBtn: document.getElementById('refreshBtn'),
            exportBtn: document.getElementById('exportBtn'),
            configBtn: document.getElementById('configBtn'),
            inspectJsonBtn: document.getElementById('inspectJsonBtn'),
            quickSearch: document.getElementById('quickSearch'),
            tableContent: document.getElementById('tableContent'),
            pagination: document.getElementById('pagination'),
            paginationControls: document.getElementById('paginationControls'),
            statsGrid: document.getElementById('statsGrid'),
            errorBanner: document.getElementById('errorBanner'),
            endpointBadge: document.getElementById('endpointBadge'),
            // Search fields
            patientId: document.getElementById('patientId'),
            studyUid: document.getElementById('studyUid'),
            accessionNumber: document.getElementById('accessionNumber'),
            modality: document.getElementById('modality'),
            dateFrom: document.getElementById('dateFrom'),
            dateTo: document.getElementById('dateTo'),
            // Config modal fields
            fhirEndpoint: document.getElementById('fhirEndpoint'),
            ohifViewerUrl: document.getElementById('ohifViewerUrl'),
            // JSON display
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
        if (elements.quickSearch) elements.quickSearch.addEventListener('input', handleQuickFilter);

        // Enter key in search fields
        document.querySelectorAll('.search-field input, .search-field select').forEach(field => {
            field.addEventListener('keypress', (e) => {
                if (e.key === 'Enter') performSearch();
            });
        });

        // Close modals on Escape
        document.addEventListener('keydown', (e) => {
            if (e.key === 'Escape') {
                closeAllModals();
            }
        });
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

        state.config = {
            fhirEndpoint,
            ohifViewerUrl
        };

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
                break;
            case 'ihe':
                elements.fhirEndpoint.value = 'https://ihebelgium.ehealthhub.be/TheDICOMPolice/fhir';
                elements.ohifViewerUrl.value = 'https://ihebelgium.ehealthhub.be/ohif/mado';
                break;
        }
        showToast(`Applied ${preset} preset`, 'info');
    }

    function openConfigModal() {
        elements.fhirEndpoint.value = state.config.fhirEndpoint || '';
        elements.ohifViewerUrl.value = state.config.ohifViewerUrl || DEFAULT_CONFIG.ohifViewerUrl;
        openModal('configModal');
    }

    function updateEndpointBadge() {
        if (elements.endpointBadge) {
            const endpoint = state.config.fhirEndpoint;
            if (endpoint) {
                try {
                    const url = new URL(endpoint);
                    elements.endpointBadge.textContent = url.hostname;
                    elements.endpointBadge.title = endpoint;
                } catch {
                    elements.endpointBadge.textContent = endpoint;
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
        ['configModal', 'jsonModal', 'actionsModal', 'docJsonModal'].forEach(closeModal);
    }

    async function performSearch() {
        showLoading();
        hideError();

        try {
            const params = new URLSearchParams();

            // Build search parameters
            const patientId = elements.patientId.value.trim();
            if (patientId) {
                params.append('patient.identifier', patientId);
            }

            const studyUid = elements.studyUid.value.trim();
            if (studyUid) {
                params.append('study-instance-uid', studyUid);
            }

            const accessionNumber = elements.accessionNumber.value.trim();
            if (accessionNumber) {
                params.append('accession', accessionNumber);
            }

            const modality = elements.modality.value;
            if (modality) {
                params.append('modality', modality);
            }

            const dateFrom = elements.dateFrom.value;
            const dateTo = elements.dateTo.value;

            if (dateFrom) {
                params.append('date', `ge${dateFrom}`);
            }
            if (dateTo) {
                params.append('date', `le${dateTo}`);
            }

            console.log('Search params:', params.toString());

            // Determine FHIR endpoint
            const baseUrl = state.config.fhirEndpoint || './fhir';
            const url = `${baseUrl}/DocumentReference${params.toString() ? '?' + params.toString() : ''}`;

            const response = await fetch(url, {
                headers: {
                    'Accept': 'application/fhir+json'
                }
            });

            if (!response.ok) {
                throw new Error(`HTTP ${response.status}: ${response.statusText}`);
            }

            const bundle = await response.json();

            // Store raw bundle for inspection
            state.rawBundle = bundle;

            // Extract documents from bundle
            state.allDocuments = parseDocumentBundle(bundle);
            state.filteredDocuments = [...state.allDocuments];
            state.currentPage = 1;

            // Update UI
            updateStats();
            sortAndRender();

            // Show success toast
            if (state.allDocuments.length > 0) {
                showToast(`Found ${state.allDocuments.length} document(s)`, 'success');
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

    function parseDocumentBundle(bundle) {
        if (!bundle || !bundle.entry || bundle.entry.length === 0) {
            return [];
        }

        return bundle.entry.map(entry => {
            const doc = entry.resource;

            // Extract patient info
            const patientId = doc.subject?.identifier?.value ||
                             doc.subject?.reference?.split('/').pop() || 'Unknown';
            const patientName = doc.subject?.display || 'Unknown Patient';

            // Extract date
            const date = doc.date ? new Date(doc.date) : null;

            // Extract modalities from context
            const modalities = doc.context?.event?.map(e => e.coding?.[0]?.code).filter(Boolean) || ['OT'];

            // Extract study description
            const description = doc.description || doc.content?.[0]?.attachment?.title || 'No Description';

            // Extract accession number
            const accessionNumber = doc.context?.related?.find(r => r.type === 'ServiceRequest')?.identifier?.value || '-';

            // Extract study UID from master identifier
            const studyUid = doc.masterIdentifier?.value || doc.id;

            // Extract Binary URL for MADO manifest
            const binaryUrl = doc.content?.[0]?.attachment?.url || '';

            // Extract author
            const author = doc.author?.[0]?.display || 'Unknown';

            return {
                id: doc.id,
                studyUid,
                patientId,
                patientName,
                date,
                dateString: date ? formatDate(date) : '-',
                modalities,
                description,
                accessionNumber,
                author,
                binaryUrl,
                size: doc.content?.[0]?.attachment?.size || 0,
                rawResource: doc  // Store raw resource for inspection
            };
        });
    }

    function updateStats() {
        const docs = state.allDocuments;

        if (docs.length === 0) {
            elements.statsGrid.style.display = 'none';
            return;
        }

        elements.statsGrid.style.display = 'grid';

        // Total count
        document.getElementById('totalCount').textContent = docs.length;

        // Unique patients
        const uniquePatients = new Set(docs.map(d => d.patientId));
        document.getElementById('patientCount').textContent = uniquePatients.size;

        // Unique modalities
        const uniqueModalities = new Set(docs.flatMap(d => d.modalities));
        document.getElementById('modalityCount').textContent = uniqueModalities.size;

        // Date range
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
                       doc.modalities.some(m => m.toLowerCase().includes(query)) ||
                       doc.author.toLowerCase().includes(query);
            });
        }

        state.currentPage = 1;
        sortAndRender();
    }

    function sortAndRender() {
        // Sort filtered documents
        const sorted = [...state.filteredDocuments].sort((a, b) => {
            let aVal = a[state.sortColumn];
            let bVal = b[state.sortColumn];

            // Handle date sorting
            if (state.sortColumn === 'date') {
                aVal = a.date?.getTime() || 0;
                bVal = b.date?.getTime() || 0;
            }

            // Handle string sorting
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

        // Store the sorted documents so buttons can reference the correct array
        state.displayedDocuments = sorted;
        renderTable(sorted);
    }

    function renderTable(documents) {
        if (documents.length === 0) {
            renderEmptyState(elements.quickSearch.value ? 'No matching results' : 'No documents found');
            elements.pagination.style.display = 'none';
            return;
        }

        // Pagination
        const totalPages = Math.ceil(documents.length / state.pageSize);
        const startIdx = (state.currentPage - 1) * state.pageSize;
        const endIdx = Math.min(startIdx + state.pageSize, documents.length);
        const pageDocuments = documents.slice(startIdx, endIdx);

        // Build table
        const tableHTML = `
            <div class="table-wrapper">
                <table class="data-table">
                    <thead>
                        <tr>
                            <th data-sort="date">
                                Study Date
                                <span class="sort-indicator"></span>
                            </th>
                            <th data-sort="patientId">
                                Patient ID
                                <span class="sort-indicator"></span>
                            </th>
                            <th data-sort="patientName">
                                Patient Name
                                <span class="sort-indicator"></span>
                            </th>
                            <th data-sort="modalities">
                                Modality
                                <span class="sort-indicator"></span>
                            </th>
                            <th data-sort="description">
                                Description
                                <span class="sort-indicator"></span>
                            </th>
                            <th data-sort="accessionNumber">
                                Accession
                                <span class="sort-indicator"></span>
                            </th>
                            <th>Actions</th>
                        </tr>
                    </thead>
                    <tbody>
                        ${pageDocuments.map((doc, idx) => `
                            <tr data-doc-index="${startIdx + idx}">
                                <td class="date-cell">${doc.dateString}</td>
                                <td class="patient-cell">${escapeHtml(doc.patientId)}</td>
                                <td class="patient-cell">${escapeHtml(doc.patientName)}</td>
                                <td class="modality-cell">
                                    ${doc.modalities.map(m => `<span class="modality-badge">${m}</span>`).join('')}
                                </td>
                                <td class="description-cell" title="${escapeHtml(doc.description)}">
                                    ${escapeHtml(doc.description)}
                                </td>
                                <td>${escapeHtml(doc.accessionNumber)}</td>
                                <td class="action-cell">
                                    <div class="action-buttons">
                                        <button class="action-btn view" onclick="MadoViewer.quickAction('view', ${startIdx + idx}, event)" title="View in OHIF">
                                            👁️
                                        </button>
                                        <button class="action-btn download" onclick="MadoViewer.quickAction('download', ${startIdx + idx}, event)" title="Download DICOM">
                                            💾
                                        </button>
                                        <button class="action-btn validate" onclick="MadoViewer.quickAction('validate', ${startIdx + idx}, event)" title="Validate MADO">
                                            ✅
                                        </button>
                                        <button class="action-btn bridge" onclick="MadoViewer.quickAction('bridge', ${startIdx + idx}, event)" title="DICOM↔FHIR Bridge">
                                            🔄
                                        </button>
                                        <button class="action-btn more" onclick="MadoViewer.openActionsModal(${startIdx + idx}, event)" title="More Actions">
                                            ⋯
                                        </button>
                                    </div>
                                </td>
                            </tr>
                        `).join('')}
                    </tbody>
                </table>
            </div>
        `;

        elements.tableContent.innerHTML = tableHTML;

        // Add sort listeners
        document.querySelectorAll('.data-table th[data-sort]').forEach(th => {
            th.addEventListener('click', () => {
                const column = th.dataset.sort;
                if (state.sortColumn === column) {
                    state.sortDirection = state.sortDirection === 'asc' ? 'desc' : 'asc';
                } else {
                    state.sortColumn = column;
                    state.sortDirection = 'desc';
                }

                // Update sort indicators
                document.querySelectorAll('.data-table th').forEach(h => {
                    h.classList.remove('sorted-asc', 'sorted-desc');
                });
                th.classList.add(`sorted-${state.sortDirection}`);

                sortAndRender();
            });
        });

        // Update current sort indicator
        const currentSortTh = document.querySelector(`th[data-sort="${state.sortColumn}"]`);
        if (currentSortTh) {
            currentSortTh.classList.add(`sorted-${state.sortDirection}`);
        }

        // Update pagination
        renderPagination(documents.length, startIdx, endIdx, totalPages);
    }

    function renderPagination(total, startIdx, endIdx, totalPages) {
        elements.pagination.style.display = 'flex';

        document.getElementById('showingRange').textContent = `${startIdx + 1}-${endIdx}`;
        document.getElementById('totalEntries').textContent = total;

        let paginationHTML = '';

        // Previous button
        paginationHTML += `
            <button class="page-btn" ${state.currentPage === 1 ? 'disabled' : ''}
                    onclick="goToPage(${state.currentPage - 1})">
                ← Previous
            </button>
        `;

        // Page numbers (show max 7 pages)
        const maxVisible = 7;
        let startPage = Math.max(1, state.currentPage - Math.floor(maxVisible / 2));
        let endPage = Math.min(totalPages, startPage + maxVisible - 1);

        if (endPage - startPage < maxVisible - 1) {
            startPage = Math.max(1, endPage - maxVisible + 1);
        }

        if (startPage > 1) {
            paginationHTML += `<button class="page-btn" onclick="goToPage(1)">1</button>`;
            if (startPage > 2) {
                paginationHTML += `<span style="padding: 8px;">...</span>`;
            }
        }

        for (let i = startPage; i <= endPage; i++) {
            paginationHTML += `
                <button class="page-btn ${i === state.currentPage ? 'active' : ''}"
                        onclick="goToPage(${i})">
                    ${i}
                </button>
            `;
        }

        if (endPage < totalPages) {
            if (endPage < totalPages - 1) {
                paginationHTML += `<span style="padding: 8px;">...</span>`;
            }
            paginationHTML += `<button class="page-btn" onclick="goToPage(${totalPages})">${totalPages}</button>`;
        }

        // Next button
        paginationHTML += `
            <button class="page-btn" ${state.currentPage === totalPages ? 'disabled' : ''}
                    onclick="goToPage(${state.currentPage + 1})">
                Next →
            </button>
        `;

        elements.paginationControls.innerHTML = paginationHTML;
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
            </div>
        `;
    }

    function showLoading() {
        elements.tableContent.innerHTML = `
            <div class="loading-overlay">
                <div class="loading-spinner">
                    <div class="spinner"></div>
                    <p style="color: var(--text-secondary); font-weight: 500;">Loading documents...</p>
                </div>
            </div>
        `;
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

    function openMADOViewer(binaryUrl, event) {
        if (event) {
            event.stopPropagation();
        }

        if (!binaryUrl) {
            showToast('No manifest URL available for this document', 'error');
            return;
        }

        // Construct full URL (handle relative URLs)
        const fullBinaryUrl = binaryUrl.startsWith('http') ? binaryUrl : window.location.origin + '/' + binaryUrl.replace(/^\.\//, '');

        // Open in OHIF viewer using configured URL
        const ohifBaseUrl = state.config.ohifViewerUrl || DEFAULT_CONFIG.ohifViewerUrl;
        const ohifUrl = `${ohifBaseUrl}?manifestUrl=${encodeURIComponent(fullBinaryUrl)}`;
        window.open(ohifUrl, '_blank');
    }

    // ==============================
    // Quick Actions
    // ==============================

    function quickAction(action, docIndex, event) {
        if (event) {
            event.stopPropagation();
        }

        // Use displayedDocuments (sorted) instead of filteredDocuments
        const doc = state.displayedDocuments?.[docIndex] || state.filteredDocuments[docIndex];
        if (!doc) {
            showToast('Document not found', 'error');
            return;
        }

        state.selectedDocument = doc;
        executeAction(action);
    }

    function openActionsModal(docIndex, event) {
        if (event) {
            event.stopPropagation();
        }

        // Use displayedDocuments (sorted) instead of filteredDocuments
        const doc = state.displayedDocuments?.[docIndex] || state.filteredDocuments[docIndex];
        if (!doc) {
            showToast('Document not found', 'error');
            return;
        }

        state.selectedDocument = doc;

        // Populate document preview
        const preview = document.getElementById('documentPreview');
        if (preview) {
            preview.innerHTML = `
                <div class="document-preview-title">${escapeHtml(doc.description)}</div>
                <div class="document-preview-meta">
                    <span><strong>Patient:</strong> ${escapeHtml(doc.patientName)} (${escapeHtml(doc.patientId)})</span>
                    <span><strong>Date:</strong> ${doc.dateString}</span>
                    <span><strong>Modality:</strong> ${doc.modalities.join(', ')}</span>
                    <span><strong>Accession:</strong> ${escapeHtml(doc.accessionNumber)}</span>
                </div>
            `;
        }

        openModal('actionsModal');
    }

    function executeAction(action) {
        const doc = state.selectedDocument;
        if (!doc) {
            showToast('No document selected', 'error');
            return;
        }

        closeModal('actionsModal');

        switch (action) {
            case 'view':
                openMADOViewer(doc.binaryUrl);
                break;

            case 'download':
                navigateToDownloader(doc);
                break;

            case 'validate':
                navigateToValidator(doc);
                break;

            case 'bridge':
                navigateToBridge(doc);
                break;

            case 'inspect':
                openDocJsonInspector(doc);
                break;

            default:
                showToast('Unknown action', 'error');
        }
    }

    function navigateToValidator(doc) {
        if (!doc.binaryUrl) {
            showToast('No manifest URL available for validation', 'error');
            return;
        }

        // Store document info for the validator page
        const validationData = {
            url: doc.binaryUrl,
            description: doc.description,
            patientId: doc.patientId,
            studyUid: doc.studyUid
        };

        try {
            sessionStorage.setItem('madoValidationData', JSON.stringify(validationData));
        } catch (e) {
            console.warn('Could not store validation data:', e);
        }

        // Navigate to validator with URL parameter
        const fullUrl = doc.binaryUrl.startsWith('http')
            ? doc.binaryUrl
            : window.location.origin + '/' + doc.binaryUrl.replace(/^\.\//, '');

        window.open(`./?loadUrl=${encodeURIComponent(fullUrl)}`, '_blank');
        showToast('Opening validator...', 'info');
    }

    function navigateToBridge(doc) {
        if (!doc.binaryUrl) {
            showToast('No manifest URL available', 'error');
            return;
        }

        // Store document info for the bridge page
        const bridgeData = {
            url: doc.binaryUrl,
            description: doc.description,
            patientId: doc.patientId,
            studyUid: doc.studyUid
        };

        try {
            sessionStorage.setItem('madoBridgeData', JSON.stringify(bridgeData));
        } catch (e) {
            console.warn('Could not store bridge data:', e);
        }

        // Navigate to converter/bridge
        const fullUrl = doc.binaryUrl.startsWith('http')
            ? doc.binaryUrl
            : window.location.origin + '/' + doc.binaryUrl.replace(/^\.\//, '');

        window.open(`./converter?loadUrl=${encodeURIComponent(fullUrl)}`, '_blank');
        showToast('Opening DICOM↔FHIR Bridge...', 'info');
    }

    function navigateToDownloader(doc) {
        if (!doc.binaryUrl) {
            showToast('No manifest URL available', 'error');
            return;
        }

        // Store document info for the downloader page
        const downloaderData = {
            url: doc.binaryUrl,
            description: doc.description,
            patientId: doc.patientId,
            studyUid: doc.studyUid
        };

        try {
            sessionStorage.setItem('madoDownloaderData', JSON.stringify(downloaderData));
        } catch (e) {
            console.warn('Could not store downloader data:', e);
        }

        // Navigate to DICOM downloader
        const fullUrl = doc.binaryUrl.startsWith('http')
            ? doc.binaryUrl
            : window.location.origin + '/' + doc.binaryUrl.replace(/^\.\//, '');

        window.open(`./dicom-downloader?manifestUrl=${encodeURIComponent(fullUrl)}`, '_blank');
        showToast('Opening DICOM Downloader...', 'info');
    }

    // ==============================
    // JSON Inspector Functions
    // ==============================

    function openJsonInspector() {
        if (!state.rawBundle) {
            showToast('No data available. Please perform a search first.', 'error');
            return;
        }

        renderJsonContent(state.rawBundle, 'jsonContent');
        updateJsonStats(state.rawBundle, state.allDocuments.length);
        openModal('jsonModal');
    }

    function openDocJsonInspector(doc) {
        if (!doc || !doc.rawResource) {
            showToast('No document data available', 'error');
            return;
        }

        renderJsonContent(doc.rawResource, 'docJsonContent');
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

        // Update button states
        document.getElementById('viewFormattedBtn')?.classList.toggle('active', mode === 'formatted');
        document.getElementById('viewRawBtn')?.classList.toggle('active', mode === 'raw');

        // Re-render
        if (state.rawBundle) {
            renderJsonContent(state.rawBundle, 'jsonContent');
        }
    }

    function syntaxHighlight(json) {
        return json
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/("(\\u[a-zA-Z0-9]{4}|\\[^u]|[^\\"])*"(\s*:)?|\b(true|false|null)\b|-?\d+(?:\.\d*)?(?:[eE][+\-]?\d+)?)/g, function(match) {
                let cls = 'number';
                if (/^"/.test(match)) {
                    if (/:$/.test(match)) {
                        cls = 'key';
                        match = match.slice(0, -1) + '</span>:';
                        return '<span class="' + cls + '">' + match;
                    } else {
                        cls = 'string';
                    }
                } else if (/true|false/.test(match)) {
                    cls = 'boolean';
                } else if (/null/.test(match)) {
                    cls = 'null';
                }
                return '<span class="' + cls + '">' + match + '</span>';
            });
    }

    function updateJsonStats(bundle, entryCount) {
        document.getElementById('jsonEntryCount').textContent = `${entryCount} entries`;

        const jsonStr = JSON.stringify(bundle);
        const sizeKb = (new Blob([jsonStr]).size / 1024).toFixed(1);
        document.getElementById('jsonSize').textContent = `${sizeKb} KB`;
    }

    function copyJson() {
        if (!state.rawBundle) return;

        const jsonStr = JSON.stringify(state.rawBundle, null, 2);
        navigator.clipboard.writeText(jsonStr)
            .then(() => showToast('JSON copied to clipboard', 'success'))
            .catch(() => showToast('Failed to copy JSON', 'error'));
    }

    function downloadJson() {
        if (!state.rawBundle) return;

        const jsonStr = JSON.stringify(state.rawBundle, null, 2);
        const blob = new Blob([jsonStr], { type: 'application/json' });
        const link = document.createElement('a');
        link.href = URL.createObjectURL(blob);
        link.download = `mhd-bundle-${new Date().toISOString().split('T')[0]}.json`;
        link.click();

        showToast('JSON downloaded', 'success');
    }

    function copyDocJson() {
        if (!state.selectedDocument?.rawResource) return;

        const jsonStr = JSON.stringify(state.selectedDocument.rawResource, null, 2);
        navigator.clipboard.writeText(jsonStr)
            .then(() => showToast('DocumentReference JSON copied', 'success'))
            .catch(() => showToast('Failed to copy JSON', 'error'));
    }

    function downloadDocJson() {
        if (!state.selectedDocument?.rawResource) return;

        const jsonStr = JSON.stringify(state.selectedDocument.rawResource, null, 2);
        const blob = new Blob([jsonStr], { type: 'application/json' });
        const link = document.createElement('a');
        link.href = URL.createObjectURL(blob);
        link.download = `document-reference-${state.selectedDocument.id || 'unknown'}.json`;
        link.click();

        showToast('DocumentReference JSON downloaded', 'success');
    }

    // ==============================
    // Toast Notifications
    // ==============================

    function showToast(message, type = 'info') {
        const container = document.getElementById('toastContainer');
        if (!container) return;

        const toast = document.createElement('div');
        toast.className = `toast ${type}`;

        const icons = {
            success: '✅',
            error: '❌',
            info: 'ℹ️'
        };

        toast.innerHTML = `
            <span>${icons[type] || 'ℹ️'}</span>
            <span>${escapeHtml(message)}</span>
            <button class="toast-close" onclick="this.parentElement.remove()">&times;</button>
        `;

        container.appendChild(toast);

        // Auto-remove after 4 seconds
        setTimeout(() => {
            toast.style.animation = 'fadeOut 0.3s ease-out';
            setTimeout(() => toast.remove(), 300);
        }, 4000);
    }

    function exportToCSV() {
        if (state.filteredDocuments.length === 0) {
            alert('No data to export');
            return;
        }

        const headers = ['Study Date', 'Patient ID', 'Patient Name', 'Modality', 'Description', 'Accession', 'Author', 'Study UID'];
        const rows = state.filteredDocuments.map(doc => [
            doc.dateString,
            doc.patientId,
            doc.patientName,
            doc.modalities.join('; '),
            doc.description,
            doc.accessionNumber,
            doc.author,
            doc.studyUid
        ]);

        const csv = [headers, ...rows]
            .map(row => row.map(cell => `"${cell.replace(/"/g, '""')}"`).join(','))
            .join('\n');

        const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
        const link = document.createElement('a');
        link.href = URL.createObjectURL(blob);
        link.download = `mado-documents-${new Date().toISOString().split('T')[0]}.csv`;
        link.click();
    }

    function showError(message) {
        elements.errorBanner.textContent = `⚠️ ${message}`;
        elements.errorBanner.style.display = 'block';
    }

    function hideError() {
        elements.errorBanner.style.display = 'none';
    }

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

    return {
        init: init,
        openMADOViewer: openMADOViewer,
        goToPage: goToPage,
        // Modal management
        openModal: openModal,
        closeModal: closeModal,
        // Configuration
        saveConfig: saveConfig,
        applyPreset: applyPreset,
        // Actions
        quickAction: quickAction,
        openActionsModal: openActionsModal,
        executeAction: executeAction,
        // JSON inspector
        setJsonView: setJsonView,
        copyJson: copyJson,
        downloadJson: downloadJson,
        copyDocJson: copyDocJson,
        downloadDocJson: downloadDocJson
    };
})();

// Initialize on load
document.addEventListener('DOMContentLoaded', MadoViewer.init);

// Expose global functions for inline event handlers
window.openMADOViewer = MadoViewer.openMADOViewer;
window.goToPage = MadoViewer.goToPage;

