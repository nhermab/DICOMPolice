/**
 * File Utilities Module
 * Handles file operations (upload, export, drag-drop)
 */

const FileUtils = {
  /**
   * Validate DICOM file
   */
  isDicomFile(file) {
    const name = file.name.toLowerCase();
    return name.endsWith('.dcm') || name.endsWith('.dicom');
  },

  /**
   * Export data to JSON
   */
  exportToJson(data, filename = null) {
    if (!data) {
      UIHelpers.showNotification('No data to export', 'error');
      return;
    }

    const dataStr = JSON.stringify(data, null, 2);
    const blob = new Blob([dataStr], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = filename || `dicom-export-${Date.now()}.json`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
  },

  /**
   * Setup file input change listener
   */
  setupFileInput(fileInput, onFileSelected) {
    fileInput.addEventListener('change', (e) => {
      const file = e.target.files[0];
      if (file) {
        onFileSelected(file);
      }
    });
  },

  /**
   * Setup browse button
   */
  setupBrowseButton(browseButton, fileInput) {
    browseButton.addEventListener('click', () => fileInput.click());
  },

  /**
   * Handle tag search/filter
   */
  filterTags(query, tagItems) {
    const lowerQuery = query.toLowerCase();

    tagItems.forEach(item => {
      const tag = (item.dataset.tag || '').toLowerCase();
      const name = (item.dataset.name || '').toLowerCase();

      if (tag.includes(lowerQuery) || name.includes(lowerQuery)) {
        item.style.display = 'block';
      } else {
        item.style.display = 'none';
      }
    });
  },

  /**
   * Expand/collapse all items
   */
  expandCollapseAll(items, expand) {
    items.forEach(item => {
      if (expand) {
        item.classList.add('expanded');
      } else {
        item.classList.remove('expanded');
      }
    });
  },

  /**
   * Setup tab switching
   */
  setupTabSwitching(tabSelector, contentSelector) {
    const tabs = document.querySelectorAll(tabSelector);
    tabs.forEach(tab => {
      tab.addEventListener('click', () => {
        const tabName = tab.dataset.tab;

        // Remove active from all tabs
        tabs.forEach(t => t.classList.remove('active'));
        tab.classList.add('active');

        // Hide all content
        document.querySelectorAll(contentSelector).forEach(content => {
          content.classList.remove('active');
        });

        // Show target content
        const targetContent = document.getElementById(`tab-${tabName}`);
        if (targetContent) {
          targetContent.classList.add('active');
        }
      });
    });
  },

  /**
   * Clear file input
   */
  clearFileInput(fileInput) {
    fileInput.value = '';
  }
};

// Export for global use
(function() {
  if (typeof window !== 'undefined') {
    window['FileUtils'] = FileUtils;
  }
})();

