/**
 * The DICOM Police - KOS and MADO Gazelle Validator
 * UZ Leuven - Universitaire Ziekenhuizen Leuven
 */

// Configuration
// Extract context path from current location (e.g., /DICOMPolice or empty for root)
const contextPath = window.location.pathname.substring(0, window.location.pathname.indexOf('/', 1)) || '';
const API_BASE_URL = window.location.origin + contextPath;
const API_PROFILES_ENDPOINT = `${API_BASE_URL}/validation/v2/profiles`;
const API_VALIDATE_ENDPOINT = `${API_BASE_URL}/validation/v2/validate`;

// State
let profiles = [];
let selectedProfile = null;
let selectedFile = null;
let currentFilter = null; // 'error', 'warning', 'info', 'notes', or null for all
let allAssertions = []; // Store all assertions for filtering
let currentResult = null; // Store current validation result for re-rendering
let paginationData = {}; // Store assertions data for pagination

// DOM Elements
const profileSelect = document.getElementById('profile-select');
const profileDescription = document.getElementById('profile-description');
const uploadArea = document.getElementById('upload-area');
const fileInput = document.getElementById('file-input');
const browseButton = document.getElementById('browse-button');
const fileInfo = document.getElementById('file-info');
const fileName = document.getElementById('file-name');
const fileSize = document.getElementById('file-size');
const removeFileBtn = document.getElementById('remove-file-btn');
const validateBtn = document.getElementById('validate-btn');
const resultsSection = document.getElementById('results-section');
const errorSection = document.getElementById('error-section');
const summaryGrid = document.getElementById('summary-grid');
const reportsContainer = document.getElementById('reports-container');
const metadataSection = document.getElementById('metadata-section');
const resultStatus = document.getElementById('result-status');
const errorMessage = document.getElementById('error-message');

/**
 * Normalize profile identifier across backend shapes.
 */
function getProfileIdentifier(profile) {
  return profile && (profile.profileID || profile.profileId || profile.id);
}

/**
 * Sync `selectedProfile` + description from the current <select> value.
 * This avoids relying on a 'change' event (which may fire before listeners are attached).
 */
function syncSelectedProfileFromSelect() {
  const selectedId = profileSelect?.value;
  if (!selectedId) {
    selectedProfile = null;
    profileDescription.textContent = 'Select a validation profile to see details';
    return;
  }

  selectedProfile = profiles.find(p => String(getProfileIdentifier(p)) === String(selectedId)) || null;

  if (!selectedProfile) {
    profileDescription.textContent = 'Select a validation profile to see details';
    return;
  }

  if (selectedProfile.profileName) {
    profileDescription.textContent = selectedProfile.profileName;
  } else if (selectedProfile.description) {
    profileDescription.textContent = selectedProfile.description;
  } else {
    profileDescription.textContent = `Profile: ${selectedProfile.profileName || selectedProfile.name || selectedProfile.profileID || selectedProfile.profileId}`;
  }
}

// Initialize Application
document.addEventListener('DOMContentLoaded', async () => {
  await loadProfiles();
  setupEventListeners();
  // Ensure we have a selectedProfile even if the default selection happened before listeners were attached.
  syncSelectedProfileFromSelect();
  // Ensure the validate button state matches any default-selected profile.
  updateValidateButton();
});

/**
 * Load available validation profiles from API
 */
async function loadProfiles() {
  try {
    const response = await fetch(API_PROFILES_ENDPOINT);
    if (!response.ok) {
      throw new Error(`Failed to load profiles: ${response.statusText}`);
    }

    profiles = await response.json();
    populateProfileSelect();
  } catch (error) {
    console.error('Error loading profiles:', error);
    profileSelect.innerHTML = '<option value="">Error loading profiles</option>';
    showNotification('Failed to load validation profiles', 'error');
  }
}

/**
 * Populate profile select dropdown
 */
function populateProfileSelect() {
  if (!profiles || profiles.length === 0) {
    profileSelect.innerHTML = '<option value="">No profiles available</option>';
    return;
  }

  profileSelect.innerHTML = '<option value="">Select a validation profile</option>';

  profiles.forEach(profile => {
    const option = document.createElement('option');
    const profileId = getProfileIdentifier(profile);
    option.value = profileId;
    option.textContent = profile.profileName || profile.name || profileId;
    profileSelect.appendChild(option);
  });

  const madoProfile = profiles.find(p => {
    const name = (p.profileName || p.name || '').toUpperCase();
    const id = (p.profileID || p.profileId || p.id || '').toUpperCase();
    return name.includes('MADO') || id.includes('MADO');
  });

  if (madoProfile) {
    const madoId = getProfileIdentifier(madoProfile);
    profileSelect.value = madoId;
  }

  // Make sure state reflects whatever is currently selected, even if listeners aren't attached yet.
  syncSelectedProfileFromSelect();
  updateValidateButton();
}

/**
 * Setup event listeners
 */
function setupEventListeners() {
  // Profile selection
  profileSelect.addEventListener('change', handleProfileChange);

  // File upload
  browseButton.addEventListener('click', () => fileInput.click());
  fileInput.addEventListener('change', handleFileSelect);
  removeFileBtn.addEventListener('click', clearFile);

  // Drag and drop
  uploadArea.addEventListener('click', (e) => {
    if (e.target === uploadArea || e.target.closest('.upload-content')) {
      fileInput.click();
    }
  });

  uploadArea.addEventListener('dragover', handleDragOver);
  uploadArea.addEventListener('dragleave', handleDragLeave);
  uploadArea.addEventListener('drop', handleDrop);

  // Validate button
  validateBtn.addEventListener('click', handleValidation);
}

/**
 * Handle profile selection change
 */
function handleProfileChange(e) {
  // Single source of truth: current select value.
  syncSelectedProfileFromSelect();
  updateValidateButton();
}

/**
 * Handle file selection
 */
function handleFileSelect(e) {
  const file = e.target.files[0];
  if (file) {
    setFile(file);
  }
}

/**
 * Handle drag over event
 */
function handleDragOver(e) {
  e.preventDefault();
  e.stopPropagation();
  uploadArea.classList.add('drag-over');
}

/**
 * Handle drag leave event
 */
function handleDragLeave(e) {
  e.preventDefault();
  e.stopPropagation();
  uploadArea.classList.remove('drag-over');
}

/**
 * Handle file drop
 */
function handleDrop(e) {
  e.preventDefault();
  e.stopPropagation();
  uploadArea.classList.remove('drag-over');

  const files = e.dataTransfer.files;
  if (files.length > 0) {
    setFile(files[0]);
  }
}

/**
 * Set selected file
 */
function setFile(file) {
  // Validate file type
  const validExtensions = ['.dcm', '.dicom'];
  const fileExtension = file.name.substring(file.name.lastIndexOf('.')).toLowerCase();

  if (!validExtensions.includes(fileExtension) && !file.name.toLowerCase().includes('dicom')) {
    showNotification('Please select a valid DICOM file (.dcm or .dicom)', 'error');
    return;
  }

  selectedFile = file;

  // Update UI
  fileName.textContent = file.name;
  fileSize.textContent = formatFileSize(file.size);

  uploadArea.querySelector('.upload-content').style.display = 'none';
  fileInfo.style.display = 'flex';

  updateValidateButton();
  hideResults();
}

/**
 * Clear selected file
 */
function clearFile() {
  selectedFile = null;
  fileInput.value = '';

  uploadArea.querySelector('.upload-content').style.display = 'block';
  fileInfo.style.display = 'none';

  updateValidateButton();
  hideResults();
}

/**
 * Update validate button state
 */
function updateValidateButton() {
  // Enable only when both a profile and a file are selected.
  validateBtn.disabled = !(selectedProfile && selectedFile);
}

/**
 * Handle validation
 */
async function handleValidation() {
  if (!selectedProfile || !selectedFile) return;

  // Show loading state
  validateBtn.disabled = true;
  validateBtn.querySelector('.btn-text').textContent = 'Validating...';
  validateBtn.querySelector('.btn-loader').style.display = 'block';

  hideResults();

  try {
    // Read file as base64
    const base64Content = await readFileAsBase64(selectedFile);

    // Prepare validation request matching the backend model (ValidationRequest)
    const validationRequest = {
      // backend expects `validationProfileId` and an `inputs` array of Input objects
      validationProfileId: selectedProfile.profileID || selectedProfile.profileId || selectedProfile.id,
      inputs: [
        {
          id: 'uploaded',
          itemId: 'uploaded',
          content: base64Content
        }
      ]
    };

    // Send validation request
    const response = await fetch(API_VALIDATE_ENDPOINT, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify(validationRequest)
    });

    if (!response.ok) {
      const errorText = await response.text();
      throw new Error(`Validation failed: ${response.statusText}\n${errorText}`);
    }

    const validationResult = await response.json();
    displayResults(validationResult);

  } catch (error) {
    console.error('Validation error:', error);
    displayError(error.message);
  } finally {
    // Reset button state
    validateBtn.disabled = false;
    validateBtn.querySelector('.btn-text').textContent = 'Validate File';
    validateBtn.querySelector('.btn-loader').style.display = 'none';
    updateValidateButton();
  }
}

/**
 * Read file as base64
 */
function readFileAsBase64(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => {
      // Remove data URL prefix to get just base64
      const base64 = reader.result.split(',')[1];
      resolve(base64);
    };
    reader.onerror = reject;
    reader.readAsDataURL(file);
  });
}

/**
 * Display validation results
 */
function displayResults(result) {
  hideError();
  currentResult = result;
  currentFilter = null; // Reset filter on new results
  resultsSection.style.display = 'block';

  // Determine overall status
  const status = determineOverallStatus(result);
  displayResultStatus(status);

  // Display summary
  displaySummary(result);

  // Display reports
  displayReports(result);

  // Display metadata
  displayMetadata(result);

  // Scroll to results
  resultsSection.scrollIntoView({ behavior: 'smooth', block: 'start' });
}

/**
 * Determine overall validation status
 */
function determineOverallStatus(result) {
  const counts = getSeverityCounts(result);
  const errors = Number(counts.error || 0);
  const warnings = Number(counts.warning || 0);
  const infos = Number(counts.info || 0);

  // If the backend didn't return any reports, still show info.
  const hasAnyReports = Array.isArray(result?.reports) && result.reports.length > 0;
  if (!hasAnyReports) return 'info';

  if (errors > 0) return 'error';
  if (warnings > 0) return 'warning';
  if (infos > 0) return 'info';

  return 'success';
}

/**
 * Compute severity counts from result reports (normalizes many backend shapes)
 */
function getSeverityCounts(result) {
  const counts = { error: 0, warning: 0, info: 0, note: 0 };
  if (!result || !Array.isArray(result.reports)) return counts;

  result.reports.forEach(report => {
    const assertions = Array.isArray(report.assertionReports) ? report.assertionReports : [];
    assertions.forEach(a => {
      const norm = normalizeSeverity(a);
      if (norm === 'error') counts.error++;
      else if (norm === 'warning') counts.warning++;
      else if (norm === 'note') counts.note++;
      else counts.info++;
    });
  });

  return counts;
}

/**
 * Display summary cards
 */
function displaySummary(result) {
  summaryGrid.innerHTML = '';

  // Use computed severity counts instead of relying on backend counters which may be incomplete
  const counts = getSeverityCounts(result);
  const summaryCounters = {
    errors: Number(counts.error || 0),
    warnings: Number(counts.warning || 0),
    infos: Number(counts.info || 0),
    notes: Number(counts.note || 0)
  };

  // Sort order: ERROR, WARNING, INFO, NOTES
  const summaryData = [
    { label: 'Errors', value: summaryCounters.errors, type: 'error', filter: 'error' },
    { label: 'Warnings', value: summaryCounters.warnings, type: 'warning', filter: 'warning' },
    { label: 'Info', value: summaryCounters.infos, type: 'info', filter: 'info' },
    { label: 'Notes', value: summaryCounters.notes, type: 'info', filter: 'notes' }
  ];

  summaryData.forEach(item => {
    const card = document.createElement('div');
    card.className = `summary-card ${item.type} ${currentFilter === item.filter ? 'active' : ''}`;
    card.style.cursor = 'pointer';
    card.setAttribute('data-filter', item.filter);
    card.setAttribute('title', `Click to filter by ${item.label.toLowerCase()}`);
    card.innerHTML = `
      <div class="summary-value">${item.value}</div>
      <div class="summary-label">${item.label}</div>
    `;
    card.addEventListener('click', () => handleFilterClick(item.filter));
    summaryGrid.appendChild(card);
  });
}

/**
 * Display result status badge
 */
function displayResultStatus(status) {
  const statusConfig = {
    success: { icon: '✓', text: 'Validation Passed' },
    warning: { icon: '⚠', text: 'Validation Passed with Warnings' },
    error: { icon: '✕', text: 'Validation Failed' },
    info: { icon: 'ℹ', text: 'Validation Complete' }
  };

  const config = statusConfig[status] || statusConfig.info;

  resultStatus.className = `result-status ${status}`;
  resultStatus.innerHTML = `<span>${config.icon}</span> ${config.text}`;
}

/**
 * Display detailed reports
 */
function displayReports(result) {
  reportsContainer.innerHTML = '';

  const reports = Array.isArray(result?.reports) ? result.reports : [];
  if (reports.length === 0) {
    reportsContainer.innerHTML = '<p style="text-align: center; color: var(--text-secondary);">No detailed reports available.</p>';
    allAssertions = [];
    return;
  }

  // Store all assertions for filtering
  allAssertions = [];
  reports.forEach(report => {
    const assertions = Array.isArray(report?.assertionReports) ? report.assertionReports : [];
    assertions.forEach(assertion => {
      allAssertions.push({
        ...assertion,
        reportName: report?.name || report?.validation || 'Report'
      });
    });
  });

  // Apply current filter
  renderReports(result, currentFilter);
}

/**
 * Render reports with optional filter
 */
function renderReports(result, filter) {
  reportsContainer.innerHTML = '';

  const reports = Array.isArray(result?.reports) ? result.reports : [];
  if (reports.length === 0) {
    reportsContainer.innerHTML = '<p style="text-align: center; color: var(--text-secondary);">No detailed reports available.</p>';
    return;
  }

  let hasVisibleReports = false;

  reports.forEach((report, index) => {
    const reportElement = createReportElement(report, index, filter);
    if (reportElement) {
      reportsContainer.appendChild(reportElement);
      hasVisibleReports = true;
    }
  });

  if (!hasVisibleReports) {
    reportsContainer.innerHTML = `<p style="text-align: center; color: var(--text-secondary);">No ${filter || ''} items found.</p>`;
  }
}

/**
 * Create report element
 */
function createReportElement(report, index, filter = null) {
  const reportDiv = document.createElement('div');
  reportDiv.className = 'report-item';

  const reportId = `report-${index}`;
  let assertions = Array.isArray(report?.assertionReports) ? report.assertionReports : [];

  // Apply filter if specified
  if (filter) {
    assertions = assertions.filter(a => {
      const severityNorm = normalizeSeverity(a);
      return severityNorm === filter.toLowerCase() || (filter === 'notes' && severityNorm === 'note');
    });
  }

  // If no assertions match filter, don't display this report
  if (assertions.length === 0 && filter) {
    return null;
  }

  // Sort assertions by severity: ERROR > WARNING > INFO > NOTES
  const severityOrder = { 'error': 0, 'warning': 1, 'info': 2, 'note': 3, 'notes': 3 };
  assertions.sort((a, b) => {
    const aSeverity = normalizeSeverity(a);
    const bSeverity = normalizeSeverity(b);
    return (severityOrder[aSeverity] ?? 2) - (severityOrder[bSeverity] ?? 2);
  });

  const assertionCount = assertions.length;
  const reportTitle = report?.name || report?.validation || `Report ${index + 1}`;

  reportDiv.innerHTML = `
    <div class="report-header" onclick="toggleReport('${reportId}')">
      <div class="report-title">
        <span>${getReportIcon(assertions)}</span>
        <span>${escapeHtml(reportTitle)}</span>
        <span style="color: var(--text-light); font-weight: 400; font-size: 13px;">(${assertionCount} items)</span>
      </div>
      <span class="report-toggle expanded" id="${reportId}-toggle">▼</span>
    </div>
    <div class="report-body expanded" id="${reportId}-body">
      ${createAssertionsList(assertions)}
    </div>
  `;

  return reportDiv;
}

/**
 * Get report icon based on assertions
 */
function getReportIcon(assertions) {
  if (!Array.isArray(assertions) || assertions.length === 0) {
    return '📋';
  }

  const hasError = assertions.some(a => normalizeSeverity(a) === 'error');
  const hasWarning = assertions.some(a => normalizeSeverity(a) === 'warning');

  if (hasError) return '🔴';
  if (hasWarning) return '🟡';
  return '🟢';
}

/**
 * Create assertions list
 */
function createAssertionsList(assertions) {
  if (!assertions || assertions.length === 0) {
    return '<p style="color: var(--text-secondary); text-align: center;">No assertions found.</p>';
  }

  // Show more results by default to reduce pagination.
  const ITEMS_PER_PAGE = 300;
  const totalPages = Math.ceil(assertions.length / ITEMS_PER_PAGE);
  const listId = 'list-' + Math.random().toString(36).substr(2, 9);

  // Store assertions data for this list
  paginationData[listId] = {
    assertions: assertions,
    currentPage: 0,
    itemsPerPage: ITEMS_PER_PAGE
  };

  // Create initial page
  let html = `<div class="assertions-container" id="${listId}">`;
  html += renderAssertionsPage(assertions, 0, ITEMS_PER_PAGE);
  html += `</div>`;

  // Add pagination if needed
  if (totalPages > 1) {
    html += `
      <div class="pagination-controls">
        <div class="pagination-info">
          Showing <span id="${listId}-showing">1-${Math.min(ITEMS_PER_PAGE, assertions.length)}</span> of ${assertions.length} items
        </div>
        <div class="pagination-buttons">
          <button class="pagination-btn" onclick="loadAssertionsPage('${listId}', 'first')" disabled>
            ‹‹ First
          </button>
          <button class="pagination-btn" onclick="loadAssertionsPage('${listId}', 'prev')" disabled>
            ‹ Prev
          </button>
          <span id="${listId}-page-info">Page 1 of ${totalPages}</span>
          <button class="pagination-btn" onclick="loadAssertionsPage('${listId}', 'next')" ${totalPages === 1 ? 'disabled' : ''}>
            Next ›
          </button>
          <button class="pagination-btn" onclick="loadAssertionsPage('${listId}', 'last')" ${totalPages === 1 ? 'disabled' : ''}>
            Last ››
          </button>
        </div>
      </div>
    `;
  }

  return html;
}

/**
 * Render a page of assertions
 */
function renderAssertionsPage(assertions, page, itemsPerPage) {
  const start = page * itemsPerPage;
  const end = Math.min(start + itemsPerPage, assertions.length);
  const pageAssertions = assertions.slice(start, end);

  const listItems = pageAssertions.map(assertion => {
    const norm = normalizeSeverity(assertion);
    const level = norm;
    const displayLabel = escapeHtml(assertion.severity || assertion.level || norm.toUpperCase());
    const message = assertion.description || assertion.message || 'No message';

    return `
      <li class="assertion-item ${level}">
        <div>
          <span class="assertion-type">${displayLabel}</span>
          ${escapeHtml(message)}
        </div>
      </li>
    `;
  }).join('');

  return `<ul class="assertion-list" data-current-page="${page}">${listItems}</ul>`;
}

/**
 * Load a different page of assertions
 */
window.loadAssertionsPage = function(listId, action) {
  const data = paginationData[listId];
  if (!data) return;

  const container = document.getElementById(listId);
  if (!container) return;

  const { assertions, currentPage, itemsPerPage } = data;
  const totalPages = Math.ceil(assertions.length / itemsPerPage);

  let newPage = currentPage;
  switch (action) {
    case 'first':
      newPage = 0;
      break;
    case 'prev':
      newPage = Math.max(0, currentPage - 1);
      break;
    case 'next':
      newPage = Math.min(totalPages - 1, currentPage + 1);
      break;
    case 'last':
      newPage = totalPages - 1;
      break;
  }

  if (newPage === currentPage) return;

  // Update stored page
  paginationData[listId].currentPage = newPage;

  // Update content
  container.innerHTML = renderAssertionsPage(assertions, newPage, itemsPerPage);

  // Update info
  const start = newPage * itemsPerPage + 1;
  const end = Math.min((newPage + 1) * itemsPerPage, assertions.length);
  const showingEl = document.getElementById(`${listId}-showing`);
  if (showingEl) {
    showingEl.textContent = `${start}-${end}`;
  }

  const pageInfoEl = document.getElementById(`${listId}-page-info`);
  if (pageInfoEl) {
    pageInfoEl.textContent = `Page ${newPage + 1} of ${totalPages}`;
  }

  // Update button states
  const controls = container.parentElement.querySelector('.pagination-controls');
  if (controls) {
    const buttons = controls.querySelectorAll('.pagination-btn');
    buttons[0].disabled = newPage === 0; // First
    buttons[1].disabled = newPage === 0; // Prev
    buttons[2].disabled = newPage === totalPages - 1; // Next
    buttons[3].disabled = newPage === totalPages - 1; // Last
  }

  // Scroll to top of list
  container.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
};

/**
 * Toggle report expansion
 */
window.toggleReport = function(reportId) {
  const body = document.getElementById(`${reportId}-body`);
  const toggle = document.getElementById(`${reportId}-toggle`);

  if (body.classList.contains('expanded')) {
    body.classList.remove('expanded');
    toggle.classList.remove('expanded');
  } else {
    body.classList.add('expanded');
    toggle.classList.add('expanded');
  }
};

/**
 * Display metadata
 */
function displayMetadata(result) {
  // Backend response exposes most metadata as top-level fields + validationMethod.
  if (!result) {
    metadataSection.innerHTML = '';
    return;
  }

  const method = result.validationMethod || {};
  const items = [];

  if (result.dateTime) {
    items.push({ key: 'Validation Date', value: new Date(result.dateTime).toLocaleString() });
  }
  if (method.validationServiceVersion) {
    items.push({ key: 'Validator Version', value: method.validationServiceVersion });
  }
  if (method.validationProfileID) {
    items.push({ key: 'Profile ID', value: method.validationProfileID });
  }
  if (method.validationProfileName) {
    items.push({ key: 'Profile Name', value: method.validationProfileName });
  }
  if (result.uuid) {
    items.push({ key: 'Report UUID', value: result.uuid });
  }

  if (items.length === 0) {
    metadataSection.innerHTML = '';
    return;
  }

  const metadataHTML = `
    <h3 class="metadata-title">📊 Validation Metadata</h3>
    <div class="metadata-grid">
      ${items.map(item => `
        <div class="metadata-item">
          <div class="metadata-key">${item.key}</div>
          <div class="metadata-value">${escapeHtml(item.value)}</div>
        </div>
      `).join('')}
    </div>
  `;

  metadataSection.innerHTML = metadataHTML;
}

/**
 * Handle filter click on summary cards
 */
function handleFilterClick(filter) {
  if (!currentResult) return;

  // Toggle filter: if clicking the same filter, clear it
  if (currentFilter === filter) {
    currentFilter = null;
  } else {
    currentFilter = filter;
  }

  // Update filter indicator
  updateFilterIndicator();

  // Re-render with new filter
  displaySummary(currentResult);
  renderReports(currentResult, currentFilter);
}

/**
 * Clear the current filter
 */
window.clearFilter = function() {
  if (!currentResult) return;

  currentFilter = null;
  updateFilterIndicator();
  displaySummary(currentResult);
  renderReports(currentResult, null);
};

/**
 * Update filter indicator visibility and text
 */
function updateFilterIndicator() {
  const indicator = document.getElementById('filter-indicator');
  const filterType = document.getElementById('filter-type');

  if (!indicator || !filterType) return;

  if (currentFilter) {
    indicator.style.display = 'flex';
    filterType.textContent = currentFilter.charAt(0).toUpperCase() + currentFilter.slice(1);
  } else {
    indicator.style.display = 'none';
  }
}

/**
 * Display error message
 */
function displayError(message) {
  hideResults();
  errorSection.style.display = 'block';
  errorMessage.textContent = message;
  errorSection.scrollIntoView({ behavior: 'smooth', block: 'start' });
}

/**
 * Hide results section
 */
function hideResults() {
  resultsSection.style.display = 'none';
  summaryGrid.innerHTML = '';
  reportsContainer.innerHTML = '';
  metadataSection.innerHTML = '';
  currentFilter = null;
  currentResult = null;
  paginationData = {};
  const indicator = document.getElementById('filter-indicator');
  if (indicator) {
    indicator.style.display = 'none';
  }
}

/**
 * Hide error section
 */
function hideError() {
  errorSection.style.display = 'none';
  errorMessage.textContent = '';
}

/**
 * Format file size
 */
function formatFileSize(bytes) {
  if (bytes === 0) return '0 Bytes';

  const k = 1024;
  const sizes = ['Bytes', 'KB', 'MB', 'GB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));

  return Math.round((bytes / Math.pow(k, i)) * 100) / 100 + ' ' + sizes[i];
}

/**
 * Escape HTML to prevent XSS
 */
function escapeHtml(text) {
  const div = document.createElement('div');
  div.textContent = text;
  return div.innerHTML;
}

/**
 * Show notification (could be enhanced with a toast library)
 */
function showNotification(message, type = 'info') {
  console.log(`[${type.toUpperCase()}] ${message}`);
  // Could implement toast notifications here
  alert(message);
}

/**
 * Normalize severity string for consistent handling: 'error'|'warning'|'info'|'note'
 */
function normalizeSeverity(item) {
  const raw = String(item?.severity || item?.level || '').trim().toLowerCase();
  if (!raw) return 'info';
  if (raw.includes('error')) return 'error';
  if (raw.includes('warn')) return 'warning';
  if (raw === 'note' || raw.includes('note')) return 'note';
  if (raw.includes('info') || raw.includes('information')) return 'info';
  // default
  return 'info';
}
