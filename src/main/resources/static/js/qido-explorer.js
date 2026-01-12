/**
 * QIDO-RS MADO Explorer
 *
 * Explores DICOM studies, series, and instances via QIDO-RS endpoints.
 * Displays DICOM tags with human-readable names.
 */

// ============================================================================
// Configuration & State
// ============================================================================

/**
 * Automatically detect the base path for QIDO-RS endpoint
 * - Local development: /dicomweb
 * - Deployed: /TheDICOMPolice/dicomweb
 *
 * Uses the same context path detection as api.js for consistency.
 */
function getQidoBasePath() {
    let contextPath = '';

    try {
        // Try to derive context path from current script URL
        const scriptUrl = new URL(document.currentScript?.src || '', window.location.origin);
        const path = scriptUrl.pathname || '';
        const idx = path.indexOf('/js/');
        if (idx > 0) {
            const prefix = path.substring(0, idx);
            contextPath = prefix === '/' ? '' : prefix;
        }
    } catch (_) {
        // Fallback: check if pathname starts with /TheDICOMPolice
        const pathname = window.location.pathname;
        if (pathname.startsWith('/TheDICOMPolice')) {
            contextPath = '/TheDICOMPolice';
        }
    }

    return window.location.origin + contextPath + '/dicomweb';
}

const DEFAULT_CONFIG = {
    qidoEndpoint: getQidoBasePath(),
    autoExpandSeries: false,
    autoExpandInstances: false
};

let config = { ...DEFAULT_CONFIG };
let currentStudies = [];
let currentSeries = [];
let currentInstances = [];
let selectedStudyUid = null;
let selectedSeriesUid = null;

// DICOM Tag Dictionary (common tags with names)
const TAG_NAMES = {
    '00080005': 'Specific Character Set',
    '00080008': 'Image Type',
    '00080016': 'SOP Class UID',
    '00080018': 'SOP Instance UID',
    '00080020': 'Study Date',
    '00080021': 'Series Date',
    '00080022': 'Acquisition Date',
    '00080023': 'Content Date',
    '00080030': 'Study Time',
    '00080031': 'Series Time',
    '00080032': 'Acquisition Time',
    '00080033': 'Content Time',
    '00080050': 'Accession Number',
    '00080060': 'Modality',
    '00080061': 'Modalities in Study',
    '00080070': 'Manufacturer',
    '00080080': 'Institution Name',
    '00080081': 'Institution Address',
    '00080090': 'Referring Physician Name',
    '00081010': 'Station Name',
    '00081030': 'Study Description',
    '0008103E': 'Series Description',
    '00081040': 'Institutional Department Name',
    '00081050': 'Performing Physician Name',
    '00081060': 'Name of Physician(s) Reading Study',
    '00081070': 'Operators Name',
    '00081080': 'Admitting Diagnoses Description',
    '00081090': 'Manufacturer Model Name',
    '00100010': 'Patient Name',
    '00100020': 'Patient ID',
    '00100030': 'Patient Birth Date',
    '00100040': 'Patient Sex',
    '00101010': 'Patient Age',
    '00101020': 'Patient Size',
    '00101030': 'Patient Weight',
    '00102000': 'Medical Alerts',
    '00102110': 'Allergies',
    '00180015': 'Body Part Examined',
    '00180050': 'Slice Thickness',
    '00181030': 'Protocol Name',
    '00181151': 'X-Ray Tube Current',
    '00185100': 'Patient Position',
    '00200010': 'Study ID',
    '00200011': 'Series Number',
    '00200013': 'Instance Number',
    '00200020': 'Patient Orientation',
    '00200032': 'Image Position Patient',
    '00200037': 'Image Orientation Patient',
    '00200052': 'Frame of Reference UID',
    '0020000D': 'Study Instance UID',
    '0020000E': 'Series Instance UID',
    '00200060': 'Laterality',
    '00201041': 'Slice Location',
    '00280002': 'Samples per Pixel',
    '00280004': 'Photometric Interpretation',
    '00280008': 'Number of Frames',
    '00280010': 'Rows',
    '00280011': 'Columns',
    '00280030': 'Pixel Spacing',
    '00280100': 'Bits Allocated',
    '00280101': 'Bits Stored',
    '00280102': 'High Bit',
    '00280103': 'Pixel Representation',
    '00281050': 'Window Center',
    '00281051': 'Window Width',
    '00281052': 'Rescale Intercept',
    '00281053': 'Rescale Slope',
    '00400244': 'Performed Procedure Step Start Date',
    '00400245': 'Performed Procedure Step Start Time',
    '00400253': 'Performed Procedure Step ID',
    '00400254': 'Performed Procedure Step Description',
    '00400275': 'Request Attributes Sequence',
    '00081190': 'Retrieve URL',
    '00081195': 'Retrieve Location UID',
    '00880200': 'Icon Image Sequence',
};

// ============================================================================
// Initialization
// ============================================================================

document.addEventListener('DOMContentLoaded', () => {
    loadConfig();
    initializeEventListeners();
    updateEndpointBadge();
});

function loadConfig() {
    const saved = localStorage.getItem('qidoExplorerConfig');
    if (saved) {
        try {
            config = { ...DEFAULT_CONFIG, ...JSON.parse(saved) };
        } catch (e) {
            console.error('Failed to parse saved config:', e);
        }
    }
}

function saveConfig() {
    localStorage.setItem('qidoExplorerConfig', JSON.stringify(config));
}

function initializeEventListeners() {
    // Search actions
    document.getElementById('searchBtn').addEventListener('click', performSearch);
    document.getElementById('clearBtn').addEventListener('click', clearSearch);

    // Configuration
    document.getElementById('configBtn').addEventListener('click', openConfigModal);
    document.getElementById('closeConfigBtn').addEventListener('click', closeConfigModal);
    document.getElementById('saveConfigBtn').addEventListener('click', saveConfiguration);
    document.getElementById('resetConfigBtn').addEventListener('click', resetConfiguration);

    // Navigation
    document.getElementById('backToStudiesBtn').addEventListener('click', showStudiesView);
    document.getElementById('backToSeriesBtn').addEventListener('click', showSeriesView);

    // Export buttons
    document.getElementById('exportStudiesBtn').addEventListener('click', () => exportToCSV(currentStudies, 'studies'));
    document.getElementById('exportSeriesBtn').addEventListener('click', () => exportToCSV(currentSeries, 'series'));
    document.getElementById('exportInstancesBtn').addEventListener('click', () => exportToCSV(currentInstances, 'instances'));

    // Tag modal
    document.getElementById('closeTagModalBtn').addEventListener('click', closeTagModal);
    document.getElementById('closeTagModal2Btn').addEventListener('click', closeTagModal);
    document.getElementById('exportTagsBtn').addEventListener('click', exportTags);

    // Filter inputs
    document.getElementById('studyFilter').addEventListener('input', (e) => filterCards('study', e.target.value));
    document.getElementById('seriesFilter').addEventListener('input', (e) => filterCards('series', e.target.value));
    document.getElementById('instanceFilter').addEventListener('input', (e) => filterCards('instance', e.target.value));

    // Tag search
    document.getElementById('tagSearchInput').addEventListener('input', (e) => filterTags(e.target.value));

    // Enter key on search fields
    const searchFields = ['patientId', 'patientName', 'studyUid', 'accessionNumber', 'studyDescription'];
    searchFields.forEach(fieldId => {
        document.getElementById(fieldId).addEventListener('keypress', (e) => {
            if (e.key === 'Enter') performSearch();
        });
    });
}

function updateEndpointBadge() {
    const badge = document.getElementById('endpointBadge');
    const url = new URL(config.qidoEndpoint);
    badge.textContent = url.hostname + (url.port ? ':' + url.port : '');
    badge.title = config.qidoEndpoint;
}

// ============================================================================
// Search Functionality
// ============================================================================

async function performSearch() {
    const searchBtn = document.getElementById('searchBtn');
    const errorBanner = document.getElementById('errorBanner');

    // Build query parameters
    const params = new URLSearchParams();

    const patientId = document.getElementById('patientId').value.trim();
    const patientName = document.getElementById('patientName').value.trim();
    const studyUid = document.getElementById('studyUid').value.trim();
    const accessionNumber = document.getElementById('accessionNumber').value.trim();
    const modality = document.getElementById('modality').value;
    const studyDate = document.getElementById('studyDate').value;
    const studyDescription = document.getElementById('studyDescription').value.trim();
    const limit = document.getElementById('limit').value;

    if (patientId) params.append('PatientID', patientId);
    if (patientName) params.append('PatientName', patientName + '*');
    if (studyUid) params.append('StudyInstanceUID', studyUid);
    if (accessionNumber) params.append('AccessionNumber', accessionNumber);
    if (modality) params.append('ModalitiesInStudy', modality);
    if (studyDate) params.append('StudyDate', studyDate.replace(/-/g, ''));
    if (studyDescription) params.append('StudyDescription', '*' + studyDescription + '*');
    if (limit) params.append('limit', limit);

    // Request all fields
    params.append('includefield', 'all');

    searchBtn.disabled = true;
    searchBtn.innerHTML = '<span>⏳ Searching...</span>';
    errorBanner.style.display = 'none';

    try {
        const url = `${config.qidoEndpoint}/studies?${params.toString()}`;
        console.log('Searching:', url);

        const response = await fetch(url, {
            headers: {
                'Accept': 'application/dicom+json'
            }
        });

        if (!response.ok) {
            throw new Error(`HTTP ${response.status}: ${response.statusText}`);
        }

        currentStudies = await response.json();
        console.log('Found studies:', currentStudies.length);

        displayStudies(currentStudies);
        updateStats();
        showStudiesView();

    } catch (error) {
        console.error('Search error:', error);
        errorBanner.textContent = `Search failed: ${error.message}`;
        errorBanner.style.display = 'flex';
    } finally {
        searchBtn.disabled = false;
        searchBtn.innerHTML = '<span>🔍 Search Studies</span>';
    }
}

function clearSearch() {
    document.getElementById('patientId').value = '';
    document.getElementById('patientName').value = '';
    document.getElementById('studyUid').value = '';
    document.getElementById('accessionNumber').value = '';
    document.getElementById('modality').value = '';
    document.getElementById('studyDate').value = '';
    document.getElementById('studyDescription').value = '';
    document.getElementById('studyFilter').value = '';
    document.getElementById('errorBanner').style.display = 'none';
}

// ============================================================================
// Display Functions
// ============================================================================

function displayStudies(studies) {
    const studyList = document.getElementById('studyList');

    if (!studies || studies.length === 0) {
        studyList.innerHTML = `
            <div class="empty-state">
                <div class="empty-icon">📭</div>
                <h3>No Studies Found</h3>
                <p>Try adjusting your search criteria</p>
            </div>
        `;
        return;
    }

    studyList.innerHTML = studies.map((study, index) => {
        const patientName = getTagValue(study, '00100010') || 'Unknown';
        const patientId = getTagValue(study, '00100020') || 'Unknown';
        const studyDate = formatDate(getTagValue(study, '00080020'));
        const studyTime = formatTime(getTagValue(study, '00080030'));
        const studyDesc = getTagValue(study, '0008103') || getTagValue(study, '00081030') || 'No description';
        const accession = getTagValue(study, '00080050') || 'N/A';
        const modalities = getTagValue(study, '00080061') || 'Unknown';
        const studyUid = getTagValue(study, '0020000D');
        const numSeries = getTagValue(study, '00201206') || '?';
        const numInstances = getTagValue(study, '00201208') || '?';

        return `
            <div class="study-card" data-study-uid="${studyUid}" onclick="selectStudy('${studyUid}')">
                <div class="card-header-row">
                    <div>
                        <div class="card-title">${escapeHtml(patientName)}</div>
                        <div class="card-subtitle">ID: ${escapeHtml(patientId)}</div>
                    </div>
                    <div class="card-badge">${modalities}</div>
                </div>
                <div class="card-tags">
                    <span class="tag tag-primary">📅 ${studyDate} ${studyTime}</span>
                    <span class="tag tag-success">📊 ${numSeries} series</span>
                    <span class="tag tag-warning">🖼️ ${numInstances} instances</span>
                </div>
                <div style="margin-bottom: 0.5rem; color: #374151; font-size: 0.875rem;">
                    <strong>Study:</strong> ${escapeHtml(studyDesc)}
                </div>
                <div class="card-metadata">
                    <div class="metadata-item">
                        <div class="metadata-label">Accession Number</div>
                        <div class="metadata-value">${escapeHtml(accession)}</div>
                    </div>
                    <div class="metadata-item">
                        <div class="metadata-label">Study Instance UID</div>
                        <div class="metadata-value" style="font-size: 0.7rem;">${escapeHtml(studyUid)}</div>
                    </div>
                </div>
                <div class="card-actions">
                    <button type="button" class="btn-card-action" onclick="event.stopPropagation(); viewStudyTags('${studyUid}')">
                        🏷️ View Tags
                    </button>
                    <button type="button" class="btn-card-action" onclick="event.stopPropagation(); selectStudy('${studyUid}')">
                        📁 View Series
                    </button>
                </div>
            </div>
        `;
    }).join('');
}

async function selectStudy(studyUid) {
    selectedStudyUid = studyUid;
    const seriesSection = document.getElementById('seriesSection');
    const seriesList = document.getElementById('seriesList');
    const seriesTitle = document.getElementById('seriesSectionTitle');

    // Show series section with loading state
    showSeriesView();
    seriesList.innerHTML = '<div class="loading-state">Loading series...</div>';

    // Update title
    const study = currentStudies.find(s => getTagValue(s, '0020000D') === studyUid);
    if (study) {
        const patientName = getTagValue(study, '00100010') || 'Unknown';
        seriesTitle.textContent = `Series for ${patientName}`;
    }

    try {
        const url = `${config.qidoEndpoint}/studies/${studyUid}/series?includefield=all`;
        const response = await fetch(url, {
            headers: { 'Accept': 'application/dicom+json' }
        });

        if (!response.ok) {
            throw new Error(`HTTP ${response.status}: ${response.statusText}`);
        }

        currentSeries = await response.json();
        console.log('Found series:', currentSeries.length);

        displaySeries(currentSeries);

    } catch (error) {
        console.error('Error loading series:', error);
        seriesList.innerHTML = `
            <div class="empty-state">
                <div class="empty-icon">⚠️</div>
                <h3>Error Loading Series</h3>
                <p>${error.message}</p>
            </div>
        `;
    }
}

function displaySeries(series) {
    const seriesList = document.getElementById('seriesList');

    if (!series || series.length === 0) {
        seriesList.innerHTML = `
            <div class="empty-state">
                <div class="empty-icon">📭</div>
                <h3>No Series Found</h3>
            </div>
        `;
        return;
    }

    seriesList.innerHTML = series.map(ser => {
        const seriesUid = getTagValue(ser, '0020000E');
        const seriesNumber = getTagValue(ser, '00200011') || 'N/A';
        const modality = getTagValue(ser, '00080060') || 'Unknown';
        const seriesDesc = getTagValue(ser, '0008103E') || 'No description';
        const seriesDate = formatDate(getTagValue(ser, '00080021'));
        const seriesTime = formatTime(getTagValue(ser, '00080031'));
        const numInstances = getTagValue(ser, '00201209') || '?';
        const bodyPart = getTagValue(ser, '00180015') || 'N/A';

        return `
            <div class="series-card" data-series-uid="${seriesUid}" onclick="selectSeries('${seriesUid}')">
                <div class="card-header-row">
                    <div>
                        <div class="card-title">Series ${seriesNumber}: ${escapeHtml(modality)}</div>
                        <div class="card-subtitle">${escapeHtml(seriesDesc)}</div>
                    </div>
                    <div class="card-badge">${numInstances} images</div>
                </div>
                <div class="card-tags">
                    <span class="tag tag-primary">📅 ${seriesDate} ${seriesTime}</span>
                    <span class="tag">👤 ${escapeHtml(bodyPart)}</span>
                </div>
                <div class="card-metadata">
                    <div class="metadata-item">
                        <div class="metadata-label">Series Instance UID</div>
                        <div class="metadata-value" style="font-size: 0.7rem;">${escapeHtml(seriesUid)}</div>
                    </div>
                </div>
                <div class="card-actions">
                    <button type="button" class="btn-card-action" onclick="event.stopPropagation(); viewSeriesTags('${seriesUid}')">
                        🏷️ View Tags
                    </button>
                    <button type="button" class="btn-card-action" onclick="event.stopPropagation(); selectSeries('${seriesUid}')">
                        🖼️ View Instances
                    </button>
                </div>
            </div>
        `;
    }).join('');
}

async function selectSeries(seriesUid) {
    selectedSeriesUid = seriesUid;
    const instanceList = document.getElementById('instanceList');
    const instancesTitle = document.getElementById('instancesSectionTitle');

    // Show instances section with loading state
    showInstancesView();
    instanceList.innerHTML = '<div class="loading-state">Loading instances...</div>';

    // Update title
    const series = currentSeries.find(s => getTagValue(s, '0020000E') === seriesUid);
    if (series) {
        const seriesNumber = getTagValue(series, '00200011') || 'Unknown';
        const modality = getTagValue(series, '00080060') || 'Unknown';
        instancesTitle.textContent = `Instances - Series ${seriesNumber} (${modality})`;
    }

    try {
        const url = `${config.qidoEndpoint}/studies/${selectedStudyUid}/series/${seriesUid}/instances?includefield=all`;
        const response = await fetch(url, {
            headers: { 'Accept': 'application/dicom+json' }
        });

        if (!response.ok) {
            throw new Error(`HTTP ${response.status}: ${response.statusText}`);
        }

        currentInstances = await response.json();
        console.log('Found instances:', currentInstances.length);

        displayInstances(currentInstances);

    } catch (error) {
        console.error('Error loading instances:', error);
        instanceList.innerHTML = `
            <div class="empty-state">
                <div class="empty-icon">⚠️</div>
                <h3>Error Loading Instances</h3>
                <p>${error.message}</p>
            </div>
        `;
    }
}

function displayInstances(instances) {
    const instanceList = document.getElementById('instanceList');

    if (!instances || instances.length === 0) {
        instanceList.innerHTML = `
            <div class="empty-state">
                <div class="empty-icon">📭</div>
                <h3>No Instances Found</h3>
            </div>
        `;
        return;
    }

    instanceList.innerHTML = instances.map(inst => {
        const sopInstanceUid = getTagValue(inst, '00080018');
        const instanceNumber = getTagValue(inst, '00200013') || 'N/A';
        const sopClassUid = getTagValue(inst, '00080016') || 'Unknown';
        const sopClassName = getSOPClassName(sopClassUid);
        const rows = getTagValue(inst, '00280010') || 'N/A';
        const columns = getTagValue(inst, '00280011') || 'N/A';
        const numFrames = getTagValue(inst, '00280008') || '1';
        const retrieveUrl = getTagValue(inst, '00081190') || '';

        return `
            <div class="instance-card" data-instance-uid="${sopInstanceUid}">
                <div class="card-header-row">
                    <div>
                        <div class="card-title">Instance ${instanceNumber}</div>
                        <div class="card-subtitle">${escapeHtml(sopClassName)}</div>
                    </div>
                    <div class="card-badge">${numFrames} frame(s)</div>
                </div>
                <div class="card-tags">
                    <span class="tag">📐 ${rows} × ${columns}</span>
                </div>
                <div class="card-metadata">
                    <div class="metadata-item">
                        <div class="metadata-label">SOP Instance UID</div>
                        <div class="metadata-value" style="font-size: 0.7rem;">${escapeHtml(sopInstanceUid)}</div>
                    </div>
                    ${retrieveUrl ? `
                    <div class="metadata-item">
                        <div class="metadata-label">Retrieve URL</div>
                        <div class="metadata-value" style="font-size: 0.7rem;">${escapeHtml(retrieveUrl)}</div>
                    </div>
                    ` : ''}
                </div>
                <div class="card-actions">
                    <button type="button" class="btn-card-action" onclick="viewInstanceTags('${sopInstanceUid}')">
                        🏷️ View Tags
                    </button>
                    ${retrieveUrl ? `
                    <button type="button" class="btn-card-action" onclick="window.open('${escapeHtml(retrieveUrl)}', '_blank')">
                        📥 Download
                    </button>
                    ` : ''}
                </div>
            </div>
        `;
    }).join('');
}

// ============================================================================
// View Management
// ============================================================================

function showStudiesView() {
    document.getElementById('studiesSection').style.display = 'block';
    document.getElementById('seriesSection').style.display = 'none';
    document.getElementById('instancesSection').style.display = 'none';
}

function showSeriesView() {
    document.getElementById('studiesSection').style.display = 'none';
    document.getElementById('seriesSection').style.display = 'block';
    document.getElementById('instancesSection').style.display = 'none';
}

function showInstancesView() {
    document.getElementById('studiesSection').style.display = 'none';
    document.getElementById('seriesSection').style.display = 'none';
    document.getElementById('instancesSection').style.display = 'block';
}

// ============================================================================
// Tag Viewing
// ============================================================================

function viewStudyTags(studyUid) {
    const study = currentStudies.find(s => getTagValue(s, '0020000D') === studyUid);
    if (study) {
        showTagModal(study, 'Study Tags');
    }
}

function viewSeriesTags(seriesUid) {
    const series = currentSeries.find(s => getTagValue(s, '0020000E') === seriesUid);
    if (series) {
        showTagModal(series, 'Series Tags');
    }
}

function viewInstanceTags(sopInstanceUid) {
    const instance = currentInstances.find(i => getTagValue(i, '00080018') === sopInstanceUid);
    if (instance) {
        showTagModal(instance, 'Instance Tags');
    }
}

let currentTagData = null;

function showTagModal(dicomObject, title) {
    currentTagData = dicomObject;
    const modal = document.getElementById('tagModal');
    const tagList = document.getElementById('tagListContent');

    // Update title
    modal.querySelector('.modal-header h2').textContent = `🏷️ ${title}`;

    // Build tag list
    const tags = [];
    for (const tag in dicomObject) {
        const tagName = TAG_NAMES[tag.toUpperCase()] || tag;
        const value = getTagValue(dicomObject, tag);
        tags.push({ tag, tagName, value });
    }

    // Sort by tag number
    tags.sort((a, b) => a.tag.localeCompare(b.tag));

    tagList.innerHTML = tags.map(({ tag, tagName, value }) => `
        <div class="tag-item" data-tag="${tag}">
            <div class="tag-name">${escapeHtml(tagName)}<br><span style="color: #9ca3af; font-size: 0.75rem;">(${tag})</span></div>
            <div class="tag-value">${escapeHtml(String(value || ''))}</div>
        </div>
    `).join('');

    modal.style.display = 'flex';
}

function closeTagModal() {
    document.getElementById('tagModal').style.display = 'none';
    currentTagData = null;
}

function filterTags(searchText) {
    const items = document.querySelectorAll('.tag-item');
    const lower = searchText.toLowerCase();

    items.forEach(item => {
        const text = item.textContent.toLowerCase();
        item.style.display = text.includes(lower) ? 'grid' : 'none';
    });
}

function exportTags() {
    if (!currentTagData) return;

    const dataStr = JSON.stringify(currentTagData, null, 2);
    const blob = new Blob([dataStr], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'dicom-tags.json';
    a.click();
    URL.revokeObjectURL(url);
}

// ============================================================================
// Configuration Modal
// ============================================================================

function openConfigModal() {
    document.getElementById('qidoEndpoint').value = config.qidoEndpoint;
    document.getElementById('autoExpandSeries').checked = config.autoExpandSeries;
    document.getElementById('autoExpandInstances').checked = config.autoExpandInstances;
    document.getElementById('configModal').style.display = 'flex';
}

function closeConfigModal() {
    document.getElementById('configModal').style.display = 'none';
}

function saveConfiguration() {
    config.qidoEndpoint = document.getElementById('qidoEndpoint').value.trim();
    config.autoExpandSeries = document.getElementById('autoExpandSeries').checked;
    config.autoExpandInstances = document.getElementById('autoExpandInstances').checked;

    saveConfig();
    updateEndpointBadge();
    closeConfigModal();

    // Show success message
    const errorBanner = document.getElementById('errorBanner');
    errorBanner.style.background = '#d1fae5';
    errorBanner.style.borderColor = '#a7f3d0';
    errorBanner.style.color = '#065f46';
    errorBanner.textContent = '✅ Configuration saved successfully';
    errorBanner.style.display = 'flex';

    setTimeout(() => {
        errorBanner.style.display = 'none';
        errorBanner.style.background = '#fee2e2';
        errorBanner.style.borderColor = '#fecaca';
        errorBanner.style.color = '#991b1b';
    }, 3000);
}

function resetConfiguration() {
    if (confirm('Reset to default configuration?')) {
        config = { ...DEFAULT_CONFIG };
        saveConfig();
        openConfigModal();
        updateEndpointBadge();
    }
}

// ============================================================================
// Statistics
// ============================================================================

function updateStats() {
    const statsGrid = document.getElementById('statsGrid');

    if (currentStudies.length === 0) {
        statsGrid.style.display = 'none';
        return;
    }

    statsGrid.style.display = 'grid';

    const totalStudies = currentStudies.length;
    const uniquePatients = new Set(currentStudies.map(s => getTagValue(s, '00100020'))).size;

    let totalSeries = 0;
    let totalInstances = 0;

    currentStudies.forEach(study => {
        const series = parseInt(getTagValue(study, '00201206') || '0');
        const instances = parseInt(getTagValue(study, '00201208') || '0');
        totalSeries += series;
        totalInstances += instances;
    });

    document.getElementById('totalStudies').textContent = totalStudies;
    document.getElementById('totalSeries').textContent = totalSeries;
    document.getElementById('totalInstances').textContent = totalInstances;
    document.getElementById('patientCount').textContent = uniquePatients;
}

// ============================================================================
// Export Functions
// ============================================================================

function exportToCSV(data, type) {
    if (!data || data.length === 0) {
        alert('No data to export');
        return;
    }

    // Collect all unique tags
    const allTags = new Set();
    data.forEach(item => {
        Object.keys(item).forEach(tag => allTags.add(tag));
    });

    const tags = Array.from(allTags).sort();

    // Build CSV header
    const header = tags.map(tag => {
        const name = TAG_NAMES[tag.toUpperCase()] || tag;
        return `"${name} (${tag})"`;
    }).join(',');

    // Build CSV rows
    const rows = data.map(item => {
        return tags.map(tag => {
            const value = getTagValue(item, tag) || '';
            return `"${String(value).replace(/"/g, '""')}"`;
        }).join(',');
    });

    const csv = [header, ...rows].join('\n');

    // Download
    const blob = new Blob([csv], { type: 'text/csv' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `qido-${type}-${new Date().toISOString().split('T')[0]}.csv`;
    a.click();
    URL.revokeObjectURL(url);
}

// ============================================================================
// Filtering
// ============================================================================

function filterCards(type, searchText) {
    const lower = searchText.toLowerCase();
    const cards = document.querySelectorAll(`.${type}-card`);

    cards.forEach(card => {
        const text = card.textContent.toLowerCase();
        card.style.display = text.includes(lower) ? 'block' : 'none';
    });
}

// ============================================================================
// Utility Functions
// ============================================================================

function getTagValue(dicomObject, tag) {
    const tagData = dicomObject[tag];
    if (!tagData) return null;

    if (tagData.Value && tagData.Value.length > 0) {
        const value = tagData.Value[0];

        // Handle PersonName
        if (tagData.vr === 'PN' && typeof value === 'object') {
            return value.Alphabetic || JSON.stringify(value);
        }

        return value;
    }

    return null;
}

function formatDate(dateStr) {
    if (!dateStr) return '';
    const str = String(dateStr);
    if (str.length === 8) {
        return `${str.substring(0, 4)}-${str.substring(4, 6)}-${str.substring(6, 8)}`;
    }
    return str;
}

function formatTime(timeStr) {
    if (!timeStr) return '';
    const str = String(timeStr);
    if (str.length >= 6) {
        return `${str.substring(0, 2)}:${str.substring(2, 4)}:${str.substring(4, 6)}`;
    }
    return str;
}

function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

function getSOPClassName(sopClassUid) {
    const sopClassNames = {
        '1.2.840.10008.5.1.4.1.1.2': 'CT Image Storage',
        '1.2.840.10008.5.1.4.1.1.4': 'MR Image Storage',
        '1.2.840.10008.5.1.4.1.1.6.1': 'Ultrasound Image Storage',
        '1.2.840.10008.5.1.4.1.1.1': 'CR Image Storage',
        '1.2.840.10008.5.1.4.1.1.1.1': 'Digital X-Ray Image Storage',
        '1.2.840.10008.5.1.4.1.1.128': 'PET Image Storage',
        '1.2.840.10008.5.1.4.1.1.88.11': 'Basic Text SR',
        '1.2.840.10008.5.1.4.1.1.88.22': 'Enhanced SR',
        '1.2.840.10008.5.1.4.1.1.88.67': 'X-Ray Radiation Dose SR',
    };

    return sopClassNames[sopClassUid] || sopClassUid;
}

// Make functions globally accessible for onclick handlers
window.selectStudy = selectStudy;
window.selectSeries = selectSeries;
window.viewStudyTags = viewStudyTags;
window.viewSeriesTags = viewSeriesTags;
window.viewInstanceTags = viewInstanceTags;

