/**
 * Validation Renderer Module
 * Handles rendering of validation results, reports, and assertions
 */

const ValidationRenderer = {
  /**
   * Create validation message element
   */
  createValidationMessage(msg) {
    const msgDiv = document.createElement('div');
    msgDiv.className = `validation-message ${msg.severity.toLowerCase()}`;

    msgDiv.innerHTML = `
      <div class="validation-message-header">
        <span class="validation-message-severity">${msg.severity}</span>
      </div>
      <div class="validation-message-text">${msg.message}</div>
      ${msg.path ? `<div class="validation-message-path">${msg.path}</div>` : ''}
    `;

    return msgDiv;
  },

  /**
   * Render validation results for visualizer
   */
  renderValidationResults(data, elements) {
    elements.validationSection.style.display = 'block';

    const statusDiv = document.getElementById('validation-status');
    statusDiv.className = `validation-status ${data.valid ? 'valid' : 'invalid'}`;
    statusDiv.innerHTML = `
      <span>${data.valid ? '✓' : '✗'}</span>
      <span>${data.valid ? 'Valid' : 'Invalid'}</span>
      ${data.profile ? `<span class="profile-badge">${data.profile}</span>` : ''}
    `;

    const summaryDiv = document.getElementById('validation-summary');
    summaryDiv.innerHTML = `
      <div class="validation-stat errors">
        <div class="validation-stat-value">${data.summary.errors}</div>
        <div class="validation-stat-label">Errors</div>
      </div>
      <div class="validation-stat warnings">
        <div class="validation-stat-value">${data.summary.warnings}</div>
        <div class="validation-stat-label">Warnings</div>
      </div>
      <div class="validation-stat infos">
        <div class="validation-stat-value">${data.summary.infos}</div>
        <div class="validation-stat-label">Info</div>
      </div>
    `;

    const messagesDiv = document.getElementById('validation-messages');
    messagesDiv.innerHTML = '';

    if (data.messages && data.messages.length > 0) {
      this.renderValidationMessagesByType(data.messages, messagesDiv);
    }
  },

  /**
   * Render validation messages grouped by severity
   */
  renderValidationMessagesByType(messages, container) {
    const errors = messages.filter(m => m.severity === 'ERROR');
    const warnings = messages.filter(m => m.severity === 'WARNING');
    const infos = messages.filter(m => m.severity === 'INFO');

    const renderGroup = (key, title, items, defaultCollapsed = false) => {
      if (!items || items.length === 0) return;

      const header = document.createElement('div');
      header.className = 'validation-group-header collapsible-header';
      if (defaultCollapsed) {
        header.classList.add('collapsed');
      }
      header.dataset.section = `validation-${key}`;

      header.innerHTML = `
        <div class="validation-group-title">
          <button class="collapse-toggle" aria-label="Toggle collapse">
            <svg class="collapse-icon" width="20" height="20" viewBox="0 0 20 20" fill="none" xmlns="http://www.w3.org/2000/svg">
              <path d="M5 7.5L10 12.5L15 7.5" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
            </svg>
          </button>
          <h3 class="validation-section-header">${title} (${items.length})</h3>
        </div>
      `;

      const body = document.createElement('div');
      body.className = 'validation-group-body collapsible-body';
      items.forEach(msg => body.appendChild(this.createValidationMessage(msg)));

      container.appendChild(header);
      container.appendChild(body);
    };

    // Default expanded: Errors + Warnings. Default collapsed: Info.
    renderGroup('errors', 'Errors', errors, false);
    renderGroup('warnings', 'Warnings', warnings, false);
    renderGroup('infos', 'Info', infos, true);

    // Wire up collapse behavior for dynamically rendered groups (and restore saved state)
    UIHelpers.setupCollapsibleSections();
    UIHelpers.restoreCollapsedStates();
  },

  /**
   * Create assertion element
   */
  createAssertionElement(assertion) {
    const assertionDiv = document.createElement('div');
    const severityNorm = UIHelpers.normalizeSeverity(assertion);
    assertionDiv.className = `assertion-item ${severityNorm}`;

    const icon = this.getSeverityIcon(severityNorm);
    const severity = assertion.severity || assertion.type || 'INFO';

    let message = assertion.message || 'No message provided';
    if (typeof message !== 'string') {
      try {
        message = JSON.stringify(message);
      } catch (e) {
        message = String(message);
      }
    }

    assertionDiv.innerHTML = `
      <div class="assertion-header">
        <span class="assertion-icon">${icon}</span>
        <span class="assertion-severity">${severity}</span>
      </div>
      <div class="assertion-message">${UIHelpers.escapeHtml(message)}</div>
      ${assertion.location ? `<div class="assertion-location">📍 ${UIHelpers.escapeHtml(assertion.location)}</div>` : ''}
      ${assertion.path ? `<div class="assertion-path">📂 ${UIHelpers.escapeHtml(assertion.path)}</div>` : ''}
    `;

    return assertionDiv;
  },

  /**
   * Get severity icon
   */
  getSeverityIcon(severity) {
    const icons = {
      error: '❌',
      warning: '⚠️',
      info: 'ℹ️',
      note: '📝'
    };
    return icons[severity.toLowerCase()] || '📝';
  },

  /**
   * Create report element
   */
  createReportElement(report, index, filter = null) {
    const reportDiv = document.createElement('div');
    reportDiv.className = 'report-item';

    const reportId = `report-${index}`;
    let assertions = Array.isArray(report?.assertionReports) ? report.assertionReports : [];

    // Apply filter
    if (filter) {
      assertions = assertions.filter(a => {
        const severityNorm = UIHelpers.normalizeSeverity(a);
        return severityNorm === filter.toLowerCase();
      });
    }

    if (assertions.length === 0 && filter) return null;

    // Sort by severity
    assertions = this.sortAssertionsBySeverity(assertions);

    const reportName = report?.name || report?.validation || 'Validation Report';
    const counts = this.getAssertionCounts(assertions);

    const reportHeader = document.createElement('div');
    reportHeader.className = 'report-header';
    reportHeader.innerHTML = `
      <div class="report-title">
        <span class="report-icon">📋</span>
        <span>${UIHelpers.escapeHtml(reportName)}</span>
      </div>
      <div class="report-summary">
        ${counts.error > 0 ? `<span class="count-badge error">${counts.error} errors</span>` : ''}
        ${counts.warning > 0 ? `<span class="count-badge warning">${counts.warning} warnings</span>` : ''}
        ${counts.info > 0 ? `<span class="count-badge info">${counts.info} info</span>` : ''}
        ${counts.note > 0 ? `<span class="count-badge note">${counts.note} notes</span>` : ''}
      </div>
      <button class="toggle-btn" data-target="${reportId}">▼</button>
    `;

    const reportBody = document.createElement('div');
    reportBody.className = 'report-body';
    reportBody.id = reportId;

    assertions.forEach(assertion => {
      reportBody.appendChild(this.createAssertionElement(assertion));
    });

    reportDiv.appendChild(reportHeader);
    reportDiv.appendChild(reportBody);

    // Toggle functionality
    const toggleBtn = reportHeader.querySelector('.toggle-btn');
    toggleBtn.addEventListener('click', () => {
      reportBody.classList.toggle('collapsed');
      toggleBtn.textContent = reportBody.classList.contains('collapsed') ? '▶' : '▼';
    });

    return reportDiv;
  },

  /**
   * Sort assertions by severity
   */
  sortAssertionsBySeverity(assertions) {
    const severityOrder = { 'error': 0, 'warning': 1, 'info': 2, 'note': 3 };
    return [...assertions].sort((a, b) => {
      const aSev = UIHelpers.normalizeSeverity(a);
      const bSev = UIHelpers.normalizeSeverity(b);
      return (severityOrder[aSev] || 999) - (severityOrder[bSev] || 999);
    });
  },

  /**
   * Get assertion counts by severity
   */
  getAssertionCounts(assertions) {
    const counts = { error: 0, warning: 0, info: 0, note: 0 };
    assertions.forEach(a => {
      const norm = UIHelpers.normalizeSeverity(a);
      counts[norm] = (counts[norm] || 0) + 1;
    });
    return counts;
  },

  /**
   * Get severity counts from result
   */
  getSeverityCounts(result) {
    const counts = { error: 0, warning: 0, info: 0, note: 0 };
    if (!result || !Array.isArray(result.reports)) return counts;

    result.reports.forEach(report => {
      const assertions = Array.isArray(report.assertionReports) ? report.assertionReports : [];
      assertions.forEach(a => {
        const norm = UIHelpers.normalizeSeverity(a);
        counts[norm] = (counts[norm] || 0) + 1;
      });
    });

    return counts;
  },

  /**
   * Determine overall status
   */
  determineOverallStatus(result) {
    const counts = this.getSeverityCounts(result);
    const hasAnyReports = Array.isArray(result?.reports) && result.reports.length > 0;
    if (!hasAnyReports) return 'info';

    if (counts.error > 0) return 'error';
    if (counts.warning > 0) return 'warning';
    if (counts.info > 0) return 'info';
    return 'success';
  },

  /**
   * Render result status
   */
  renderResultStatus(status, container) {
    const statusConfig = {
      success: { icon: '✓', text: 'Validation Passed' },
      warning: { icon: '⚠', text: 'Validation Passed with Warnings' },
      error: { icon: '✕', text: 'Validation Failed' },
      info: { icon: 'ℹ', text: 'Validation Complete' }
    };

    const config = statusConfig[status] || statusConfig.info;
    container.className = `result-status ${status}`;
    container.innerHTML = `<span>${config.icon}</span> ${config.text}`;
  },

  /**
   * Render summary cards
   */
  renderSummaryCards(result, container, onFilterClick, currentFilter = null) {
    container.innerHTML = '';

    const counts = this.getSeverityCounts(result);
    const summaryData = [
      { label: 'Errors', value: counts.error, type: 'error', filter: 'error' },
      { label: 'Warnings', value: counts.warning, type: 'warning', filter: 'warning' },
      { label: 'Info', value: counts.info, type: 'info', filter: 'info' },
      { label: 'Notes', value: counts.note, type: 'info', filter: 'note' }
    ];

    summaryData.forEach(item => {
      const card = document.createElement('div');
      const isActive = currentFilter === item.filter;
      card.className = `summary-card ${item.type} ${isActive ? 'active' : ''}`;
      card.style.cursor = 'pointer';
      card.setAttribute('data-filter', item.filter);
      card.setAttribute('title', `Click to filter by ${item.label.toLowerCase()}`);
      card.innerHTML = `
        <div class="summary-value">${item.value}</div>
        <div class="summary-label">${item.label}</div>
      `;
      if (onFilterClick) {
        card.addEventListener('click', () => onFilterClick(item.filter));
      }
      container.appendChild(card);
    });
  },

  /**
   * Render metadata
   */
  renderMetadata(result, container) {
    container.innerHTML = '';

    const method = result?.method || {};
    const items = [];

    if (result.timestamp) {
      const date = new Date(result.timestamp);
      items.push({ key: 'Validated At', value: date.toLocaleString() });
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
      container.innerHTML = '';
      return;
    }

    container.innerHTML = `
      <h3 class="metadata-title">📊 Validation Metadata</h3>
      <div class="metadata-grid">
        ${items.map(item => `
          <div class="metadata-item">
            <div class="metadata-key">${item.key}</div>
            <div class="metadata-value">${UIHelpers.escapeHtml(item.value)}</div>
          </div>
        `).join('')}
      </div>
    `;
  }
};

// Export for global use
(function() {
  if (typeof window !== 'undefined') {
    window['ValidationRenderer'] = ValidationRenderer;
  }
})();
