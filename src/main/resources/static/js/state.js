/**
 * Application State Management
 * Central state store for both validator and visualizer
 */

const AppState = {
  // Current mode: 'validate' or 'visualize'
  currentMode: 'validate',

  // Validator state
  validator: {
    profiles: [],
    selectedProfile: null,
    selectedFile: null,
    currentFilter: null,
    allAssertions: [],
    currentResult: null,
    paginationData: {}
  },

  // Visualizer state
  visualizer: {
    currentData: null,
    allTags: [],
    selectedFile: null
  },

  // Switch between modes
  setMode(mode) {
    this.currentMode = mode;
    const validateMode = document.getElementById('validate-mode');
    const visualizeMode = document.getElementById('visualize-mode');
    const navLinks = document.querySelectorAll('.nav-link');

    // Only update UI if mode elements exist
    if (!validateMode || !visualizeMode) {
      // Just store the preference and return
      localStorage.setItem('dicomPoliceMode', mode);
      return;
    }

    if (mode === 'validate') {
      validateMode.style.display = 'block';
      visualizeMode.style.display = 'none';
      navLinks.forEach(link => {
        link.classList.toggle('active', link.dataset.mode === 'validate');
      });
    } else {
      validateMode.style.display = 'none';
      visualizeMode.style.display = 'block';
      navLinks.forEach(link => {
        link.classList.toggle('active', link.dataset.mode === 'visualize');
      });
    }

    // Store preference
    localStorage.setItem('dicomPoliceMode', mode);
  },

  // Restore last mode
  restoreMode() {
    const savedMode = localStorage.getItem('dicomPoliceMode') || 'validate';
    this.setMode(savedMode);
  },

  // Validator helpers
  setFilter(filter) {
    this.validator.currentFilter = filter;
  },

  clearFilter() {
    this.validator.currentFilter = null;
    if (window.ValidatorModule) {
      window.ValidatorModule.renderFilteredResults();
    }
  },

  // Get current file based on mode
  getCurrentFile() {
    return this.currentMode === 'validate'
      ? this.validator.selectedFile
      : this.visualizer.selectedFile;
  }
};

