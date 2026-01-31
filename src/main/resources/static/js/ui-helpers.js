/**
 * UI Helper Functions
 * Shared utility functions for UI operations
 */

const UIHelpers = {
  /**
   * Format file size
   */
  formatFileSize(bytes) {
    if (bytes === 0) return '0 Bytes';
    const k = 1024;
    const sizes = ['Bytes', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return Math.round((bytes / Math.pow(k, i)) * 100) / 100 + ' ' + sizes[i];
  },

  /**
   * Escape HTML to prevent XSS
   */
  escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
  },

  /**
   * Show notification
   */
  showNotification(message, type = 'info') {
    console.log(`[${type.toUpperCase()}] ${message}`);

    // Create toast container if it doesn't exist
    let container = document.getElementById('notification-container');
    if (!container) {
      container = document.createElement('div');
      container.id = 'notification-container';
      container.style.cssText = `
        position: fixed;
        bottom: 20px;
        right: 20px;
        z-index: 10000;
        display: flex;
        flex-direction: column;
        gap: 8px;
      `;
      document.body.appendChild(container);
    }

    // Create toast
    const toast = document.createElement('div');
    const colors = {
      success: '#28a745',
      error: '#dc3545',
      warning: '#ffc107',
      info: '#0066cc'
    };
    const icons = {
      success: '✅',
      error: '❌',
      warning: '⚠️',
      info: 'ℹ️'
    };

    toast.style.cssText = `
      background: white;
      padding: 12px 20px;
      border-radius: 8px;
      box-shadow: 0 4px 20px rgba(0,0,0,0.15);
      border-left: 4px solid ${colors[type] || colors.info};
      font-size: 14px;
      font-weight: 500;
      min-width: 280px;
      max-width: 400px;
      display: flex;
      align-items: center;
      gap: 10px;
      animation: slideInRight 0.3s ease-out;
    `;

    toast.innerHTML = `
      <span>${icons[type] || icons.info}</span>
      <span style="flex: 1;">${this.escapeHtml(message)}</span>
      <button onclick="this.parentElement.remove()" style="background: none; border: none; cursor: pointer; font-size: 18px; color: #999;">&times;</button>
    `;

    container.appendChild(toast);

    // Auto-remove after 5 seconds
    setTimeout(() => {
      if (toast.parentElement) {
        toast.style.animation = 'fadeOutRight 0.3s ease-out';
        setTimeout(() => toast.remove(), 300);
      }
    }, 5000);
  },

  /**
   * Normalize severity string: 'error'|'warning'|'info'|'note'
   */
  normalizeSeverity(item) {
    const raw = String(item?.severity || item?.level || '').trim().toLowerCase();
    if (!raw) return 'info';
    if (raw.includes('error')) return 'error';
    if (raw.includes('warn')) return 'warning';
    if (raw === 'note' || raw.includes('note')) return 'note';
    if (raw.includes('info') || raw.includes('information')) return 'info';
    return 'info';
  },

  /**
   * Setup drag and drop for an upload area
   */
  setupDragDrop(uploadArea, fileInput, onFileSelected) {
    uploadArea.addEventListener('click', (e) => {
      // Don't trigger file input if clicking on browse button or link-button
      // (those have their own handlers)
      if (e.target.closest('.link-button, button[id*="browse"]')) {
        return;
      }
      if (e.target === uploadArea || e.target.closest('.upload-content')) {
        fileInput.click();
      }
    });

    uploadArea.addEventListener('dragover', (e) => {
      e.preventDefault();
      e.stopPropagation();
      uploadArea.classList.add('drag-over');
    });

    uploadArea.addEventListener('dragleave', (e) => {
      e.preventDefault();
      e.stopPropagation();
      uploadArea.classList.remove('drag-over');
    });

    uploadArea.addEventListener('drop', (e) => {
      e.preventDefault();
      e.stopPropagation();
      uploadArea.classList.remove('drag-over');
      const files = e.dataTransfer.files;
      if (files.length > 0) {
        onFileSelected(files[0]);
      }
    });
  },

  /**
   * Setup collapsible sections
   */
  setupCollapsibleSections() {
    const collapsibleHeaders = document.querySelectorAll('.collapsible-header');

    // Apply default collapsed state (before wiring events)
    collapsibleHeaders.forEach(header => {
      const defaultCollapsed = header.dataset.defaultCollapsed === 'true';
      if (defaultCollapsed) {
        header.classList.add('collapsed');
      }
    });

    collapsibleHeaders.forEach(header => {
      // Avoid double-binding if sections are re-rendered dynamically
      if (header.dataset.collapsibleInitialized === 'true') {
        return;
      }
      header.dataset.collapsibleInitialized = 'true';

      header.addEventListener('click', (e) => {
        if (e.target.closest('.section-tabs, .tag-controls, input, button:not(.collapse-toggle)')) {
          return;
        }
        this.toggleSection(header);
      });

      const collapseToggle = header.querySelector('.collapse-toggle');
      if (collapseToggle) {
        collapseToggle.addEventListener('click', (e) => {
          e.stopPropagation();
          this.toggleSection(header);
        });
      }
    });
  },

  /**
   * Toggle section collapsed state
   */
  toggleSection(header) {
    header.classList.toggle('collapsed');

    // Persist when the section is named
    const section = header.dataset.section;
    if (section) {
      const isCollapsed = header.classList.contains('collapsed');
      localStorage.setItem(`section-${section}-collapsed`, isCollapsed);
    }
  },

  /**
   * Restore collapsed states from localStorage
   */
  restoreCollapsedStates() {
    const collapsibleHeaders = document.querySelectorAll('.collapsible-header');
    collapsibleHeaders.forEach(header => {
      const section = header.dataset.section;
      if (section) {
        const isCollapsed = localStorage.getItem(`section-${section}-collapsed`) === 'true';
        if (isCollapsed) {
          header.classList.add('collapsed');
        }
      }
    });
  },

  /**
   * Setup tab switching
   */
  setupTabs(tabsContainer, contentPrefix) {
    const tabs = tabsContainer.querySelectorAll('.tab-btn');
    tabs.forEach(tab => {
      tab.addEventListener('click', () => {
        const targetTab = tab.dataset.tab;

        // Update tab buttons
        tabs.forEach(t => t.classList.remove('active'));
        tab.classList.add('active');

        // Update content
        const allContent = document.querySelectorAll(`#${contentPrefix} .tab-content`);
        allContent.forEach(c => c.classList.remove('active'));

        const targetContent = document.getElementById(`tab-${targetTab}`);
        if (targetContent) {
          targetContent.classList.add('active');
        }
      });
    });
  },

  /**
   * Read file as base64
   */
  readFileAsBase64(file) {
    return new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = () => {
        const base64 = reader.result.split(',')[1];
        resolve(base64);
      };
      reader.onerror = reject;
      reader.readAsDataURL(file);
    });
  }
};
