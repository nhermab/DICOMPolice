/**
 * Validator Module
 * Handles all validation-specific functionality
 */

const Validator = {
  // DOM Elements
  elements: {
    profileSelect: document.getElementById('profile-select'),
    profileDescription: document.getElementById('profile-description'),
    uploadArea: document.getElementById('upload-area'),
    fileInput: document.getElementById('file-input'),
    browseButton: document.getElementById('browse-button'),
    fileInfo: document.getElementById('file-info'),
    fileName: document.getElementById('file-name'),
    fileSize: document.getElementById('file-size'),
    removeFileBtn: document.getElementById('remove-file-btn'),
    validateBtn: document.getElementById('validate-btn'),
    resultsSection: document.getElementById('results-section'),
    errorSection: document.getElementById('error-section'),
    summaryGrid: document.getElementById('summary-grid'),
    reportsContainer: document.getElementById('reports-container'),
    metadataSection: document.getElementById('metadata-section'),
    resultStatus: document.getElementById('result-status'),
    errorMessage: document.getElementById('error-message')
  },

  /**
   * Initialize validator
   */
  async init() {
    // Check if required DOM elements exist
    if (!this.elements.profileSelect) {
      console.warn('Validator: Required DOM elements not found, skipping initialization');
      return;
    }

    await this.loadProfiles();
    this.setupEventListeners();
    this.syncSelectedProfile();
    this.updateValidateButton();
  },

  /**
   * Get profile identifier
   */
  getProfileIdentifier(profile) {
    return profile && (profile.profileID || profile.profileId || profile.id);
  },

  /**
   * Sync selected profile from dropdown
   */
  syncSelectedProfile() {
    if (!this.elements.profileSelect || !this.elements.profileDescription) {
      return;
    }

    const selectedId = this.elements.profileSelect?.value;
    if (!selectedId) {
      AppState.validator.selectedProfile = null;
      this.elements.profileDescription.textContent = 'Select a validation profile to see details';
      return;
    }

    const profile = AppState.validator.profiles.find(
      p => String(this.getProfileIdentifier(p)) === String(selectedId)
    );
    AppState.validator.selectedProfile = profile || null;

    if (!profile) {
      this.elements.profileDescription.textContent = 'Select a validation profile to see details';
      return;
    }

    if (profile.profileName) {
      this.elements.profileDescription.textContent = profile.profileName;
    } else if (profile.description) {
      this.elements.profileDescription.textContent = profile.description;
    } else {
      this.elements.profileDescription.textContent = `Profile: ${profile.profileName || profile.name || this.getProfileIdentifier(profile)}`;
    }
  },

  /**
   * Load validation profiles
   */
  async loadProfiles() {
    if (!this.elements.profileSelect) {
      return;
    }

    try {
      AppState.validator.profiles = await API.loadProfiles();
      this.populateProfileSelect();
    } catch (error) {
      console.error('Error loading profiles:', error);
      if (this.elements.profileSelect) {
        this.elements.profileSelect.innerHTML = '<option value="">Error loading profiles</option>';
      }
      UIHelpers.showNotification('Failed to load validation profiles', 'error');
    }
  },

  /**
   * Populate profile select dropdown
   */
  populateProfileSelect() {
    if (!this.elements.profileSelect) {
      return;
    }

    const profiles = AppState.validator.profiles;
    if (!profiles || profiles.length === 0) {
      this.elements.profileSelect.innerHTML = '<option value="">No profiles available</option>';
      return;
    }

    this.elements.profileSelect.innerHTML = '<option value="">Select a validation profile</option>';

    profiles.forEach(profile => {
      const option = document.createElement('option');
      const profileId = this.getProfileIdentifier(profile);
      option.value = profileId;
      option.textContent = profile.profileName || profile.name || profileId;
      this.elements.profileSelect.appendChild(option);
    });

    // Auto-select MADO profile if available
    const madoProfile = profiles.find(p => {
      const name = (p.profileName || p.name || '').toUpperCase();
      const id = (this.getProfileIdentifier(p) || '').toUpperCase();
      return name.includes('MADO') || id.includes('MADO');
    });

    if (madoProfile) {
      this.elements.profileSelect.value = this.getProfileIdentifier(madoProfile);
    }

    this.syncSelectedProfile();
    this.updateValidateButton();
  },

  /**
   * Setup event listeners
   */
  setupEventListeners() {
    this.elements.profileSelect.addEventListener('change', () => {
      this.syncSelectedProfile();
      this.updateValidateButton();
    });

    this.elements.browseButton.addEventListener('click', () =>
      this.elements.fileInput.click()
    );

    this.elements.fileInput.addEventListener('change', (e) => {
      const file = e.target.files[0];
      if (file) this.setFile(file);
    });

    this.elements.removeFileBtn.addEventListener('click', () => this.clearFile());
    this.elements.validateBtn.addEventListener('click', () => this.handleValidation());

    UIHelpers.setupDragDrop(
      this.elements.uploadArea,
      this.elements.fileInput,
      (file) => this.setFile(file)
    );
  },

  /**
   * Set selected file
   */
  setFile(file) {
    if (!FileUtils.isDicomFile(file)) {
      UIHelpers.showNotification('Please select a valid DICOM file (.dcm or .dicom)', 'error');
      return;
    }

    AppState.validator.selectedFile = file;

    this.elements.fileName.textContent = file.name;
    this.elements.fileSize.textContent = UIHelpers.formatFileSize(file.size);

    this.elements.uploadArea.querySelector('.upload-content').style.display = 'none';
    this.elements.fileInfo.style.display = 'flex';

    this.updateValidateButton();
    this.hideResults();
  },

  /**
   * Clear selected file
   */
  clearFile() {
    AppState.validator.selectedFile = null;
    FileUtils.clearFileInput(this.elements.fileInput);

    this.elements.uploadArea.querySelector('.upload-content').style.display = 'block';
    this.elements.fileInfo.style.display = 'none';

    this.updateValidateButton();
    this.hideResults();
  },

  /**
   * Update validate button state
   */
  updateValidateButton() {
    this.elements.validateBtn.disabled = !(
      AppState.validator.selectedProfile &&
      AppState.validator.selectedFile
    );
  },

  /**
   * Handle validation
   */
  async handleValidation() {
    const { selectedProfile, selectedFile } = AppState.validator;
    if (!selectedProfile || !selectedFile) return;

    // Show loading state
    this.elements.validateBtn.disabled = true;
    this.elements.validateBtn.querySelector('.btn-text').textContent = 'Validating...';
    this.elements.validateBtn.querySelector('.btn-loader').style.display = 'block';

    this.hideResults();

    try {
      const result = await API.validateFile(selectedFile, this.getProfileIdentifier(selectedProfile));
      this.displayResults(result);
    } catch (error) {
      console.error('Validation error:', error);
      this.displayError(error.message);
    } finally {
      this.elements.validateBtn.disabled = false;
      this.elements.validateBtn.querySelector('.btn-text').textContent = 'Validate File';
      this.elements.validateBtn.querySelector('.btn-loader').style.display = 'none';
      this.updateValidateButton();
    }
  },

  /**
   * Display validation results
   */
  displayResults(result) {
    this.hideError();
    AppState.validator.currentResult = result;
    AppState.validator.currentFilter = null;
    this.elements.resultsSection.style.display = 'block';

    const status = ValidationRenderer.determineOverallStatus(result);
    ValidationRenderer.renderResultStatus(status, this.elements.resultStatus);
    this.displaySummary(result);
    this.displayReports(result);
    ValidationRenderer.renderMetadata(result, this.elements.metadataSection);

    this.elements.resultsSection.scrollIntoView({ behavior: 'smooth', block: 'start' });
  },

  /**
   * Display summary cards
   */
  displaySummary(result) {
    ValidationRenderer.renderSummaryCards(
      result,
      this.elements.summaryGrid,
      (filter) => this.handleFilterClick(filter),
      AppState.validator.currentFilter
    );
  },

  /**
   * Display detailed reports
   */
  displayReports(result) {
    this.elements.reportsContainer.innerHTML = '';

    const reports = Array.isArray(result?.reports) ? result.reports : [];
    if (reports.length === 0) {
      this.elements.reportsContainer.innerHTML = '<p style="text-align: center; color: var(--text-secondary);">No detailed reports available.</p>';
      AppState.validator.allAssertions = [];
      return;
    }

    // Store all assertions
    AppState.validator.allAssertions = [];
    reports.forEach(report => {
      const assertions = Array.isArray(report?.assertionReports) ? report.assertionReports : [];
      assertions.forEach(assertion => {
        AppState.validator.allAssertions.push({
          ...assertion,
          reportName: report?.name || report?.validation || 'Report'
        });
      });
    });

    this.renderFilteredResults();
  },

  /**
   * Render reports with filter
   */
  renderFilteredResults() {
    const result = AppState.validator.currentResult;
    const filter = AppState.validator.currentFilter;

    this.elements.reportsContainer.innerHTML = '';

    const reports = Array.isArray(result?.reports) ? result.reports : [];
    let hasVisibleReports = false;

    reports.forEach((report, index) => {
      const reportElement = this.createReportElement(report, index, filter);
      if (reportElement) {
        this.elements.reportsContainer.appendChild(reportElement);
        hasVisibleReports = true;
      }
    });

    if (!hasVisibleReports) {
      this.elements.reportsContainer.innerHTML = `<p style="text-align: center; color: var(--text-secondary);">No ${filter || ''} items found.</p>`;
    }

    this.updateFilterIndicator();
  },

  /**
   * Create report element
   */
  createReportElement(report, index, filter = null) {
    return ValidationRenderer.createReportElement(report, index, filter);
  },

  /**
   * Toggle report expansion
   */
  toggleReport(reportId) {
    const body = document.getElementById(`${reportId}-body`);
    const toggle = document.getElementById(`${reportId}-toggle`);

    if (body.classList.contains('expanded')) {
      body.classList.remove('expanded');
      toggle.classList.remove('expanded');
    } else {
      body.classList.add('expanded');
      toggle.classList.add('expanded');
    }
  },


  /**
   * Handle filter click
   */
  handleFilterClick(filter) {
    if (!AppState.validator.currentResult) return;

    // Toggle filter
    if (AppState.validator.currentFilter === filter) {
      AppState.validator.currentFilter = null;
    } else {
      AppState.validator.currentFilter = filter;
    }

    this.displaySummary(AppState.validator.currentResult);
    this.renderFilteredResults();
  },

  /**
   * Update filter indicator
   */
  updateFilterIndicator() {
    const indicator = document.getElementById('filter-indicator');
    const filterType = document.getElementById('filter-type');

    if (!indicator || !filterType) return;

    if (AppState.validator.currentFilter) {
      indicator.style.display = 'flex';
      filterType.textContent = AppState.validator.currentFilter.charAt(0).toUpperCase() +
        AppState.validator.currentFilter.slice(1);
    } else {
      indicator.style.display = 'none';
    }
  },

  /**
   * Display error
   */
  displayError(message) {
    this.hideResults();
    this.elements.errorSection.style.display = 'block';
    this.elements.errorMessage.textContent = message;
    this.elements.errorSection.scrollIntoView({ behavior: 'smooth', block: 'start' });
  },

  /**
   * Hide results
   */
  hideResults() {
    this.elements.resultsSection.style.display = 'none';
    this.elements.summaryGrid.innerHTML = '';
    this.elements.reportsContainer.innerHTML = '';
    this.elements.metadataSection.innerHTML = '';
    AppState.validator.currentFilter = null;
    AppState.validator.currentResult = null;
    AppState.validator.paginationData = {};
    const indicator = document.getElementById('filter-indicator');
    if (indicator) indicator.style.display = 'none';
  },

  /**
  /**
   * Hide error
   */
  hideError() {
    this.elements.errorSection.style.display = 'none';
    this.elements.errorMessage.textContent = '';
  }
};

// Export for global use
if (typeof window !== 'undefined') {
  window.ValidatorModule = Validator;
}
