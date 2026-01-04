/**
 * MADO Viewer Module
 * Handles searching and viewing MADO manifests
 */

const MadoViewer = (function() {
    // Application State
    const state = {
        allDocuments: [],
        filteredDocuments: [],
        currentPage: 1,
        pageSize: 50,
        sortColumn: 'date',
        sortDirection: 'desc'
    };

    // DOM Elements
    let elements = {};

    function init() {
        // Initialize DOM elements
        elements = {
            searchBtn: document.getElementById('searchBtn'),
            clearBtn: document.getElementById('clearBtn'),
            refreshBtn: document.getElementById('refreshBtn'),
            exportBtn: document.getElementById('exportBtn'),
            quickSearch: document.getElementById('quickSearch'),
            tableContent: document.getElementById('tableContent'),
            pagination: document.getElementById('pagination'),
            paginationControls: document.getElementById('paginationControls'),
            statsGrid: document.getElementById('statsGrid'),
            errorBanner: document.getElementById('errorBanner'),
            // Search fields
            patientId: document.getElementById('patientId'),
            studyUid: document.getElementById('studyUid'),
            accessionNumber: document.getElementById('accessionNumber'),
            modality: document.getElementById('modality'),
            dateFrom: document.getElementById('dateFrom'),
            dateTo: document.getElementById('dateTo')
        };

        setupEventListeners();
    }

    function setupEventListeners() {
        if (elements.searchBtn) elements.searchBtn.addEventListener('click', performSearch);
        if (elements.clearBtn) elements.clearBtn.addEventListener('click', clearSearch);
        if (elements.refreshBtn) elements.refreshBtn.addEventListener('click', performSearch);
        if (elements.exportBtn) elements.exportBtn.addEventListener('click', exportToCSV);
        if (elements.quickSearch) elements.quickSearch.addEventListener('input', handleQuickFilter);

        // Enter key in search fields
        document.querySelectorAll('.search-field input, .search-field select').forEach(field => {
            field.addEventListener('keypress', (e) => {
                if (e.key === 'Enter') performSearch();
            });
        });
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

            // Fetch from FHIR endpoint
            const url = `./fhir/DocumentReference${params.toString() ? '?' + params.toString() : ''}`;
            const response = await fetch(url, {
                headers: {
                    'Accept': 'application/fhir+json'
                }
            });

            if (!response.ok) {
                throw new Error(`HTTP ${response.status}: ${response.statusText}`);
            }

            const bundle = await response.json();

            // Extract documents from bundle
            state.allDocuments = parseDocumentBundle(bundle);
            state.filteredDocuments = [...state.allDocuments];
            state.currentPage = 1;

            // Update UI
            updateStats();
            sortAndRender();

        } catch (error) {
            console.error('Search error:', error);
            showError(`Failed to fetch documents: ${error.message}`);
            state.allDocuments = [];
            state.filteredDocuments = [];
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
                size: doc.content?.[0]?.attachment?.size || 0
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
                            <th data-sort="author">
                                Author
                                <span class="sort-indicator"></span>
                            </th>
                            <th>Action</th>
                        </tr>
                    </thead>
                    <tbody>
                        ${pageDocuments.map(doc => `
                            <tr onclick="openMADOViewer('${escapeHtml(doc.binaryUrl)}', event)">
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
                                <td>${escapeHtml(doc.author)}</td>
                                <td class="action-cell">
                                    <button class="view-btn" onclick="openMADOViewer('${escapeHtml(doc.binaryUrl)}', event)">
                                        👁️ View
                                    </button>
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
            alert('No manifest URL available for this document');
            return;
        }

        // Construct full URL (handle relative URLs)
        const fullBinaryUrl = binaryUrl.startsWith('http') ? binaryUrl : window.location.origin + '/' + binaryUrl.replace(/^\.\//, '');

        // Open in OHIF viewer
        const ohifUrl = `https://ihebelgium.ehealthhub.be/ohif/mado?manifestUrl=${encodeURIComponent(fullBinaryUrl)}`;
        window.open(ohifUrl, '_blank');
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
        goToPage: goToPage
    };
})();

// Initialize on load
document.addEventListener('DOMContentLoaded', MadoViewer.init);

// Expose global functions for inline event handlers
window.openMADOViewer = MadoViewer.openMADOViewer;
window.goToPage = MadoViewer.goToPage;

