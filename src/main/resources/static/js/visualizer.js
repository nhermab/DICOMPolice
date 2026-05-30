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

    // Check for URL parameters to auto-load files
    this.checkUrlParameters();
  },

  /**
   * Check URL parameters for auto-load functionality
   */
  async checkUrlParameters() {
    const urlParams = new URLSearchParams(window.location.search);
    const loadUrl = urlParams.get('loadUrl');

    if (loadUrl) {
      console.log('Visualizer: Auto-loading file from URL:', loadUrl);
      await this.loadFileFromUrl(loadUrl);
    }
  },

  /**
   * Load a file from a remote URL.
   * Handles both DICOM binaries and FHIR JSON (DocumentReference / Bundle).
   * If the content is FHIR JSON it is automatically converted to DICOM before parsing.
   */
  async loadFileFromUrl(url) {
    try {
      // Show loading state
      this.showUrlLoadingState('Loading file from URL...');

      const response = await fetch(url, {
        headers: {
          'Accept': 'application/dicom, application/fhir+json, application/json, application/octet-stream, */*'
        }
      });

      if (!response.ok) {
        throw new Error(`Failed to fetch file: HTTP ${response.status}`);
      }

      const contentType = (response.headers.get('content-type') || '').toLowerCase();
      const blob = await response.blob();

      // --- Detect whether the response is FHIR JSON ---
      let isFhirContent = contentType.includes('application/fhir+json') ||
                           contentType.includes('application/json');
      let fhirJson = null;

      if (isFhirContent) {
        fhirJson = await blob.text();
      } else {
        // Peek at the first 132 bytes to check for DICM magic
        const peekBytes = new Uint8Array(await blob.slice(0, 132).arrayBuffer());
        const isDicomMagic = peekBytes.length >= 132 &&
          peekBytes[128] === 0x44 && peekBytes[129] === 0x49 &&
          peekBytes[130] === 0x43 && peekBytes[131] === 0x4D;
        if (!isDicomMagic) {
          try {
            const text = await blob.text();
            const parsed = JSON.parse(text);
            if (parsed && parsed.resourceType) {
              isFhirContent = true;
              fhirJson = text;
            }
          } catch (_) {
            // Not JSON — treat as DICOM
          }
        }
      }

      // --- If it is a DocumentReference, follow the content attachment URL ---
      if (isFhirContent && fhirJson) {
        try {
          const json = JSON.parse(fhirJson);
          if (json && json.resourceType === 'DocumentReference' && json.content?.length > 0) {
            const contentUrl = json.content[0]?.attachment?.url;
            if (contentUrl && contentUrl !== url) {
              UIHelpers.showNotification('Following DocumentReference to manifest...', 'info');
              return await this.loadFileFromUrl(contentUrl);
            }
          }
        } catch (_) {
          // Ignore parse errors — continue with normal handling
        }
      }

      // --- If FHIR JSON, convert to DICOM via the converter API ---
      if (isFhirContent && fhirJson) {
        this.showUrlLoadingState('Converting FHIR JSON to DICOM...');
        UIHelpers.showNotification('Converting FHIR JSON to DICOM before parsing...', 'info');

        const convertedFile = await this.convertFhirJsonToDicom(fhirJson, url);
        this.hideUrlLoadingState();
        this.setFile(convertedFile, true);
        UIHelpers.showNotification(`Converted & loaded: ${convertedFile.name}`, 'success');

        setTimeout(() => { this.parseFile(); }, 300);
        return;
      }

      // --- Regular DICOM binary ---
      let filename = 'mado-manifest.dcm';
      try {
        const urlObj = new URL(url);
        const pathParts = urlObj.pathname.split('/');
        const lastPart = pathParts[pathParts.length - 1];
        if (lastPart && lastPart.length > 0) {
          filename = lastPart.includes('.') ? lastPart : lastPart + '.dcm';
        }
      } catch (e) {
        // Keep default filename
      }

      const file = new File([blob], filename, { type: 'application/dicom' });

      this.hideUrlLoadingState();
      this.setFile(file, true);
      UIHelpers.showNotification(`Loaded: ${filename}`, 'success');

      setTimeout(() => { this.parseFile(); }, 300);

    } catch (error) {
      console.error('Failed to load file from URL:', error);
      this.hideUrlLoadingState();
      UIHelpers.showNotification(`Failed to load file: ${error.message}`, 'error');
    }
  },

  /**
   * Convert FHIR JSON text to a DICOM File object via the server converter API.
   * @param {string} fhirJson  The raw FHIR JSON string
   * @param {string} sourceUrl Optional URL for deriving a filename
   * @returns {Promise<File>}  A File containing DICOM Part-10 bytes
   */
  async convertFhirJsonToDicom(fhirJson, sourceUrl) {
    const jsonBlob = new Blob([fhirJson], { type: 'application/fhir+json' });
    const jsonFile = new File([jsonBlob], 'manifest.json', { type: 'application/fhir+json' });

    const convertUrl = `${API.baseURL}/api/converter/convert`;
    const formData = new FormData();
    formData.append('file', jsonFile);
    formData.append('sourceType', 'fhir');

    const resp = await fetch(convertUrl, { method: 'POST', body: formData });
    if (!resp.ok) {
      const errorText = await resp.text();
      throw new Error(errorText || 'FHIR → DICOM conversion failed');
    }

    const result = await resp.json();
    if (!result.convertedBase64) {
      throw new Error('Converter did not return DICOM binary data');
    }

    const binaryString = atob(result.convertedBase64);
    const bytes = new Uint8Array(binaryString.length);
    for (let i = 0; i < binaryString.length; i++) {
      bytes[i] = binaryString.charCodeAt(i);
    }

    let filename = result.suggestedFilename || 'converted.dcm';
    try {
      if (sourceUrl) {
        const urlObj = new URL(sourceUrl);
        const lastPart = urlObj.pathname.split('/').pop();
        if (lastPart && lastPart.length > 0 && !lastPart.includes('.')) {
          filename = lastPart + '.dcm';
        }
      }
    } catch (_) { /* keep default */ }

    return new File([bytes], filename, { type: 'application/dicom' });
  },

  /**
   * Show loading state in upload area
   */
  showUrlLoadingState(message) {
    if (this.elements.uploadContent) {
      this.elements.uploadContent.innerHTML = `
        <div class="loading-indicator" style="text-align: center; padding: 40px;">
          <div class="spinner" style="width: 40px; height: 40px; border: 3px solid #e0e0e0; border-top-color: #0066cc; border-radius: 50%; animation: spin 0.8s linear infinite; margin: 0 auto 16px;"></div>
          <p style="color: #666; font-weight: 500;">${message}</p>
        </div>
      `;
    }
  },

  /**
   * Hide loading state and restore upload area
   */
  hideUrlLoadingState() {
    if (this.elements.uploadContent) {
      this.elements.uploadContent.innerHTML = `
        <div class="upload-icon">📋</div>
        <h3>Drop DICOM or FHIR file here</h3>
        <p>or</p>
        <button type="button" class="btn btn-secondary" id="browse-button">Browse Files</button>
        <p class="file-types">Supports: .dcm, .dicom, .json (FHIR)</p>
      `;
      // Re-attach browse button listener
      const newBrowseBtn = this.elements.uploadContent.querySelector('#browse-button') ||
                           this.elements.uploadContent.querySelector('.btn');
      if (newBrowseBtn && this.elements.fileInput) {
        newBrowseBtn.addEventListener('click', () => this.elements.fileInput.click());
      }
    }
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
   * @param {File} file - The file to set
   * @param {boolean} skipValidation - Skip file extension validation (for URL-loaded files)
   */
  setFile(file, skipValidation = false) {
    if (!skipValidation && !FileUtils.isSupportedFile(file)) {
      UIHelpers.showNotification('Please select a valid DICOM (.dcm, .dicom) or FHIR (.json) file', 'error');
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
   * Detect whether a File is JSON/FHIR based on extension or content type.
   */
  isJsonFile(file) {
    const ext = (file.name || '').split('.').pop().toLowerCase();
    const ct = (file.type || '').toLowerCase();
    return ext === 'json' || ct.includes('application/fhir+json') || ct.includes('application/json');
  },

  /**
   * Ensure the file is DICOM. If it is FHIR JSON, convert it first.
   * @param {File} file
   * @returns {Promise<File>} a DICOM File
   */
  async ensureDicomFile(file) {
    if (!this.isJsonFile(file)) return file;

    UIHelpers.showNotification('Converting FHIR JSON to DICOM...', 'info');
    const fhirJson = await file.text();
    return await this.convertFhirJsonToDicom(fhirJson);
  },

  /**
   * Parse and validate file
   */
  async parseFile() {
    let file = AppState.visualizer.selectedFile;
    if (!file) return;

    this.showLoading(true);
    this.hideAllSections();

    try {
      // Convert FHIR JSON to DICOM if needed
      file = await this.ensureDicomFile(file);

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
    let file = AppState.visualizer.selectedFile;
    if (!file) return;

    this.showLoading(true);
    // In validate-only mode, keep existing parse/content hidden
    this.elements.validationSection.style.display = 'none';

    try {
      // Convert FHIR JSON to DICOM if needed
      file = await this.ensureDicomFile(file);

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
