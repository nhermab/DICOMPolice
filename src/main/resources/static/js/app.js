/**
 * The DICOM Police - Main Application Entry Point
 * UZ Leuven - Universitaire Ziekenhuizen Leuven
 */

// Initialize application when DOM is ready
document.addEventListener('DOMContentLoaded', async () => {
  console.log('🚓 The DICOM Police - Starting...');

  // Setup mode switching
  setupModeSwitch();

  // Initialize validator only if validator elements exist
  if (typeof ValidatorModule !== 'undefined' && document.getElementById('profile-select')) {
    await ValidatorModule.init();
  }

  // Initialize visualizer only if visualizer elements exist
  // Support both the newer viz-* ids and the standalone index.html ids.
  const hasVisualizerDom =
    document.getElementById('viz-upload-area') || document.getElementById('upload-area');

  if (typeof VisualizerModule !== 'undefined' && hasVisualizerDom) {
    VisualizerModule.init();
  }

  // Restore last mode only if mode elements exist
  if (document.getElementById('validate-mode') && document.getElementById('visualize-mode')) {
    AppState.restoreMode();
  }

  console.log('✅ The DICOM Police - Ready!');
});

/**
 * Setup mode switching between validate and visualize
 */
function setupModeSwitch() {
  const navLinks = document.querySelectorAll('.nav-link');

  navLinks.forEach(link => {
    link.addEventListener('click', () => {
      const mode = link.dataset.mode;
      if (mode) {
        AppState.setMode(mode);
      }
    });
  });
}
