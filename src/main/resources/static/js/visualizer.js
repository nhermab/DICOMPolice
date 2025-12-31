/**
 * Visualizer Module
 * Handles DICOM file parsing and visualization
 */

const Visualizer = {
  // DOM Elements
  elements: {
    uploadArea: document.getElementById('viz-upload-area'),
    fileInput: document.getElementById('viz-file-input'),
    browseButton: document.getElementById('viz-browse-button'),
    uploadContent: document.getElementById('viz-upload-content'),
    fileInfo: document.getElementById('viz-file-info'),
    fileName: document.getElementById('viz-file-name'),
    fileSize: document.getElementById('viz-file-size'),
    removeFileBtn: document.getElementById('viz-remove-file-btn'),
    parseBtn: document.getElementById('parse-btn'),
    validateBtn: document.getElementById('validate-btn'),
    loadingIndicator: document.getElementById('loading-indicator'),
    fileInfoSection: document.getElementById('file-info-section'),
    validationSection: document.getElementById('validation-section'),
    contentSection: document.getElementById('content-section'),
    tagsSection: document.getElementById('tags-section')
  },

  /**
   * (Re)resolve DOM elements.
   *
   * We support two DOM id conventions:
   * - "viz-*" ids used by the main app page
   * - non-prefixed ids used by standalone `index.html`
   */
  resolveElements() {
    const byId = (primary, fallback) => document.getElementById(primary) || document.getElementById(fallback);

    this.elements.uploadArea = byId('viz-upload-area', 'upload-area');
    this.elements.fileInput = byId('viz-file-input', 'file-input');
    this.elements.browseButton = byId('viz-browse-button', 'browse-button');
    this.elements.uploadContent = byId('viz-upload-content', 'upload-content');
    this.elements.fileInfo = byId('viz-file-info', 'file-info');
    this.elements.fileName = byId('viz-file-name', 'file-name');
    this.elements.fileSize = byId('viz-file-size', 'file-size');
    this.elements.removeFileBtn = byId('viz-remove-file-btn', 'remove-file-btn');

    // shared ids (same in both pages)
    this.elements.parseBtn = document.getElementById('parse-btn');
    this.elements.loadingIndicator = document.getElementById('loading-indicator');
    this.elements.fileInfoSection = document.getElementById('file-info-section');
    this.elements.validationSection = document.getElementById('validation-section');
    this.elements.contentSection = document.getElementById('content-section');
    this.elements.tagsSection = document.getElementById('tags-section');

    // optional button on index.html (currently unused by this module)
    this.elements.validateBtn = document.getElementById('validate-btn');
  },

  /**
   * Initialize visualizer
   */
  init() {
    // Resolve after DOM is ready (this file is loaded before app.js triggers init)
    this.resolveElements();

    // If the page doesn't have the needed DOM, don't crash.
    if (!this.elements.uploadArea || !this.elements.fileInput) {
      console.warn('Visualizer: missing upload DOM elements; skipping init.');
      return;
    }

    this.setupEventListeners();
    UIHelpers.setupCollapsibleSections();
    UIHelpers.restoreCollapsedStates();
    this.setupTabSwitching();
  },

  /**
   * Setup event listeners
   */
  setupEventListeners() {
    if (this.elements.browseButton && this.elements.fileInput) {
      this.elements.browseButton.addEventListener('click', () =>
        this.elements.fileInput.click()
      );
    }

    if (this.elements.fileInput) {
      this.elements.fileInput.addEventListener('change', (e) => {
        const file = e.target.files[0];
        if (file) this.setFile(file);
      });
    }

    if (this.elements.removeFileBtn) {
      this.elements.removeFileBtn.addEventListener('click', () => this.clearFile());
    }

    if (this.elements.parseBtn) {
      this.elements.parseBtn.addEventListener('click', () => this.parseFile());
    }

    if (this.elements.validateBtn) {
      this.elements.validateBtn.addEventListener('click', () => this.validateOnly());
    }

    if (this.elements.uploadArea && this.elements.fileInput) {
      UIHelpers.setupDragDrop(
        this.elements.uploadArea,
        this.elements.fileInput,
        (file) => this.setFile(file)
      );
    }

    // Tag controls
    const tagSearch = document.getElementById('tag-search');
    if (tagSearch) {
      tagSearch.addEventListener('input', (e) => this.handleTagSearch(e));
    }

    const expandAllBtn = document.getElementById('expand-all-btn');
    if (expandAllBtn) {
      expandAllBtn.addEventListener('click', () => this.expandCollapseAll(true));
    }

    const collapseAllBtn = document.getElementById('collapse-all-btn');
    if (collapseAllBtn) {
      collapseAllBtn.addEventListener('click', () => this.expandCollapseAll(false));
    }

    const exportJsonBtn = document.getElementById('export-json-btn');
    if (exportJsonBtn) {
      exportJsonBtn.addEventListener('click', () => this.exportToJson());
    }
  },

  /**
   * Set selected file
   */
  setFile(file) {
    if (!FileUtils.isDicomFile(file)) {
      UIHelpers.showNotification('Please select a valid DICOM file (.dcm or .dicom)', 'error');
      return;
    }

    AppState.visualizer.selectedFile = file;

    this.elements.fileName.textContent = file.name;
    this.elements.fileSize.textContent = UIHelpers.formatFileSize(file.size);

    this.elements.uploadContent.style.display = 'none';
    this.elements.fileInfo.style.display = 'flex';
    this.elements.parseBtn.disabled = false;

    if (this.elements.validateBtn) {
      this.elements.validateBtn.disabled = false;
    }

    this.hideAllSections();
  },

  /**
   * Clear selected file
   */
  clearFile() {
    AppState.visualizer.selectedFile = null;
    AppState.visualizer.currentData = null;
    AppState.visualizer.allTags = [];

    FileUtils.clearFileInput(this.elements.fileInput);
    this.elements.uploadContent.style.display = 'block';
    this.elements.fileInfo.style.display = 'none';
    this.elements.parseBtn.disabled = true;

    if (this.elements.validateBtn) {
      this.elements.validateBtn.disabled = true;
    }

    this.hideAllSections();
  },

  /**
   * Hide all result sections
   */
  hideAllSections() {
    this.elements.fileInfoSection.style.display = 'none';
    this.elements.validationSection.style.display = 'none';
    this.elements.contentSection.style.display = 'none';
    this.elements.tagsSection.style.display = 'none';
  },

  /**
   * Parse and validate file
   */
  async parseFile() {
    const file = AppState.visualizer.selectedFile;
    if (!file) return;

    this.showLoading(true);
    this.hideAllSections();

    try {
      // 1) Parse for visualization
      const data = await API.parseFile(file);

      // 2) Validate (server auto-detects KOS vs MADO if profile omitted)
      let validation = null;
      try {
        validation = await API.validateVisualizerFile(file);
      } catch (validationError) {
        console.error('Error validating file:', validationError);
        // Keep parse results even if validation fails
        UIHelpers.showNotification('Validation failed: ' + validationError.message, 'error');
      }

      if (validation) {
        data.validation = validation;
      }

      AppState.visualizer.currentData = data;
      this.displayResults(data);

    } catch (error) {
      console.error('Error parsing file:', error);
      UIHelpers.showNotification('Error parsing file: ' + error.message, 'error');
    } finally {
      this.showLoading(false);
    }
  },

  /**
   * Validate only (no parse)
   */
  async validateOnly() {
    const file = AppState.visualizer.selectedFile;
    if (!file) return;

    this.showLoading(true);
    // In validate-only mode, keep existing parse/content hidden
    this.elements.validationSection.style.display = 'none';

    try {
      const validation = await API.validateVisualizerFile(file);
      this.displayValidationResults(validation);
    } catch (error) {
      console.error('Error validating file:', error);
      UIHelpers.showNotification('Error validating file: ' + error.message, 'error');
    } finally {
      this.showLoading(false);
    }
  },

  /**
   * Show/hide loading indicator
   */
  showLoading(show) {
    this.elements.loadingIndicator.style.display = show ? 'block' : 'none';
    this.elements.parseBtn.disabled = show;
  },

  /**
   * Display all results
   */
  displayResults(data) {
    this.displayFileInfo(data);
    this.displayContent(data);
    this.displayTags(data.tags);

    // Auto-validate if validation data is present
    if (data.validation) {
      this.displayValidationResults(data.validation);
    }
  },

  /**
   * Display file info
   */
  displayFileInfo(data) {
    const grid = document.getElementById('file-info-grid');
    grid.innerHTML = '';

    const info = [
      { label: 'File Name', value: data.fileName },
      { label: 'File Size', value: UIHelpers.formatFileSize(data.fileSize) },
      { label: 'File Type', value: data.fileType },
      { label: 'SOP Class UID', value: data.sopClassUID },
      { label: 'SOP Class Name', value: data.sopClassUIDName }
    ];

    info.forEach(item => {
      const div = document.createElement('div');
      div.className = 'info-item';
      div.innerHTML = `
        <div class="info-label">${item.label}</div>
        <div class="info-value">${item.value || 'N/A'}</div>
      `;
      grid.appendChild(div);
    });

    this.elements.fileInfoSection.style.display = 'block';
  },

  /**
   * Display content structure
   */
  displayContent(data) {
    if (!data.content) return;

    const content = data.content;

    // Display evidence
    if (content.evidence && content.evidence.length > 0) {
      this.displayEvidence(content.evidence);
    }

    // Display content tree
    if (content.contentTree && content.contentTree.length > 0) {
      this.displayContentTree(content.contentTree);
    }

    // Display MADO descriptors
    if (content.isMADO && content.descriptors) {
      this.displayDescriptors(content.descriptors);
      document.querySelector('[data-tab="descriptors"]').style.display = 'block';
    }

    this.elements.contentSection.style.display = 'block';
  },

  /**
   * Display evidence
   */
  displayEvidence(evidence) {
    const container = document.getElementById('tab-evidence');
    DicomRenderer.renderEvidence(evidence, container);
  },

  /**
   * Display content tree
   */
  displayContentTree(tree) {
    const container = document.getElementById('tab-content-tree');
    DicomRenderer.renderContentTree(tree, container);
  },

  /**
   * Display descriptors
   */
  displayDescriptors(descriptors) {
    const container = document.getElementById('tab-descriptors');
    DicomRenderer.renderDescriptors(descriptors, container);
  },

  /**
   * Display DICOM tags
   */
  displayTags(tags) {
    AppState.visualizer.allTags = tags || [];
    const container = document.getElementById('tags-container');
    DicomRenderer.renderTags(AppState.visualizer.allTags, container);
    this.elements.tagsSection.style.display = 'block';
  },

  /**
   * Display validation results
   */
  displayValidationResults(data) {
    ValidationRenderer.renderValidationResults(data, this.elements);
  },

  /**
   * Setup tab switching
   */
  setupTabSwitching() {
    FileUtils.setupTabSwitching('#content-tabs .tab-btn', '.tab-content');
  },

  /**
   * Handle tag search
   */
  handleTagSearch(e) {
    const query = e.target.value;
    const tagItems = document.querySelectorAll('.tag-item');
    FileUtils.filterTags(query, tagItems);
  },

  /**
   * Expand/collapse all tags
   */
  expandCollapseAll(expand) {
    const tagItems = document.querySelectorAll('.tag-item');
    FileUtils.expandCollapseAll(tagItems, expand);
  },

  /**
   * Export to JSON
   */
  exportToJson() {
    FileUtils.exportToJson(AppState.visualizer.currentData, `dicom-export-${Date.now()}.json`);
  }
};

// Export for global use
if (typeof window !== 'undefined') {
  window.VisualizerModule = Visualizer;
}
