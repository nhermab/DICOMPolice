/**
 * DICOM Renderer Module
 * Handles rendering of DICOM-specific content (tags, sequences, content trees, evidence)
 */

const DicomRenderer = {
  /**
   * Create tag element
   */
  createTagElement(tag) {
    const tagDiv = document.createElement('div');
    tagDiv.className = 'tag-item';
    tagDiv.dataset.tag = tag.tag;
    tagDiv.dataset.name = tag.name.toLowerCase();

    const header = document.createElement('div');
    header.className = 'tag-header';

    const valueStr = this.formatTagValue(tag);

    header.innerHTML = `
      <div class="tag-id">${tag.tag}</div>
      <div class="tag-vr">${tag.vr}</div>
      <div class="tag-name">${tag.name}</div>
      <div class="tag-value">${valueStr}</div>
      <div class="tag-expand-icon">▶</div>
    `;

    tagDiv.appendChild(header);

    // Create body with details
    const body = this.createTagBody(tag);
    tagDiv.appendChild(body);

    // Toggle functionality
    header.addEventListener('click', (e) => {
      e.stopPropagation();
      tagDiv.classList.toggle('expanded');
    });

    return tagDiv;
  },

  /**
   * Format tag value for display
   */
  formatTagValue(tag) {
    if (tag.vr === 'SQ') {
      return `[Sequence: ${tag.sequenceLength || 0} items]`;
    }

    if (tag.value !== null && tag.value !== undefined) {
      let valueStr = Array.isArray(tag.value)
        ? tag.value.join(', ')
        : String(tag.value);

      if (valueStr.length > 50) {
        valueStr = valueStr.substring(0, 50) + '...';
      }
      return valueStr;
    }

    return '[Empty]';
  },

  /**
   * Create tag body with details
   */
  createTagBody(tag) {
    const body = document.createElement('div');
    body.className = 'tag-body';

    const details = [
      { label: 'Tag', value: tag.tag },
      { label: 'Hex', value: tag.tagHex },
      { label: 'Name', value: tag.name },
      { label: 'VR', value: tag.vr },
      { label: 'Path', value: tag.path || '(root)' }
    ];

    if (tag.vr !== 'SQ') {
      const fullValue = this.getFullTagValue(tag);
      details.push({ label: 'Value', value: fullValue });
    }

    details.forEach(detail => {
      const row = document.createElement('div');
      row.className = 'tag-detail-row';
      row.innerHTML = `
        <div class="tag-detail-label">${detail.label}:</div>
        <div class="tag-detail-value">${detail.value}</div>
      `;
      body.appendChild(row);
    });

    // Handle sequences
    if (tag.vr === 'SQ' && tag.sequenceItems && tag.sequenceItems.length > 0) {
      const seqContainer = this.createSequenceContainer(tag.sequenceItems);
      body.appendChild(seqContainer);
    }

    return body;
  },

  /**
   * Get full tag value
   */
  getFullTagValue(tag) {
    if (tag.value !== null && tag.value !== undefined) {
      return Array.isArray(tag.value)
        ? tag.value.join(', ')
        : String(tag.value);
    }
    return '[Empty]';
  },

  /**
   * Create sequence container
   */
  createSequenceContainer(sequenceItems) {
    const seqContainer = document.createElement('div');
    seqContainer.className = 'sequence-container';

    sequenceItems.forEach((item, idx) => {
      const seqItem = document.createElement('div');
      seqItem.className = 'sequence-item';

      const seqHeader = document.createElement('div');
      seqHeader.className = 'sequence-item-header';
      seqHeader.textContent = `Item #${idx + 1} (${item.length} tags)`;
      seqItem.appendChild(seqHeader);

      item.forEach(childTag => {
        seqItem.appendChild(this.createTagElement(childTag));
      });

      seqContainer.appendChild(seqItem);
    });

    return seqContainer;
  },

  /**
   * Create tree node element
   */
  createTreeNode(node, level = 0) {
    const nodeDiv = document.createElement('div');
    nodeDiv.className = 'tree-node expanded';
    nodeDiv.style.marginLeft = (level * 20) + 'px';

    const header = document.createElement('div');
    header.className = 'tree-node-header';

    let headerContent = `
      <span class="tree-node-icon">▶</span>
      <span class="tree-node-type">${node.valueType || 'N/A'}</span>
    `;

    if (node.relationshipType) {
      headerContent += `<span class="tree-node-type">${node.relationshipType}</span>`;
    }

    if (node.conceptName) {
      headerContent += `<span class="tree-node-content"><strong>${node.conceptName.codeMeaning || node.conceptName.codeValue}</strong></span>`;
    } else {
      headerContent += `<span class="tree-node-content">Unknown Concept</span>`;
    }

    header.innerHTML = headerContent;
    nodeDiv.appendChild(header);

    const body = this.createTreeNodeBody(node, level);
    nodeDiv.appendChild(body);

    // Toggle functionality
    header.addEventListener('click', (e) => {
      e.stopPropagation();
      nodeDiv.classList.toggle('expanded');
    });

    return nodeDiv;
  },

  /**
   * Create tree node body
   */
  createTreeNodeBody(node, level) {
    const body = document.createElement('div');
    body.className = 'tree-node-body';

    // Add value based on type
    if (node.code) {
      const detail = document.createElement('div');
      detail.className = 'tree-node-detail';
      detail.innerHTML = `
        <strong>Code:</strong> <span class="code-value">${node.code.codeValue}</span> 
        (${node.code.codingSchemeDesignator}) - ${node.code.codeMeaning}
      `;
      body.appendChild(detail);
    } else if (node.textValue) {
      const detail = document.createElement('div');
      detail.className = 'tree-node-detail';
      detail.innerHTML = `<strong>Text:</strong> ${node.textValue}`;
      body.appendChild(detail);
    }

    // Add children
    if (node.children && node.children.length > 0) {
      node.children.forEach(child => {
        body.appendChild(this.createTreeNode(child, level + 1));
      });
    }

    return body;
  },

  /**
   * Render evidence
   */
  renderEvidence(evidence, container) {
    container.innerHTML = '';

    if (!evidence || evidence.length === 0) {
      container.innerHTML = '<div class="empty-state"><div class="empty-state-icon">📭</div><div class="empty-state-text">No evidence found</div></div>';
      return;
    }

    const evidenceContainer = document.createElement('div');
    evidenceContainer.className = 'evidence-container';

    evidence.forEach((study, studyIdx) => {
      const studyDiv = this.createEvidenceStudy(study, studyIdx);
      evidenceContainer.appendChild(studyDiv);
    });

    container.appendChild(evidenceContainer);
  },

  /**
   * Create evidence study element
   */
  createEvidenceStudy(study, studyIdx) {
    const studyDiv = document.createElement('div');
    studyDiv.className = 'evidence-study';

    // Study header (collapsible)
    const studyHeader = document.createElement('div');
    studyHeader.className = 'evidence-study-header collapsible-header';
    studyHeader.dataset.section = `evidence-study-${studyIdx}`;

    studyHeader.innerHTML = `
      <div class="evidence-title-row">
        <div class="evidence-title">📚 Study ${studyIdx + 1}: ${study.studyInstanceUID}</div>
        <button class="collapse-toggle" aria-label="Toggle collapse">
          <svg class="collapse-icon" width="20" height="20" viewBox="0 0 20 20" fill="none" xmlns="http://www.w3.org/2000/svg">
            <path d="M5 7.5L10 12.5L15 7.5" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
        </button>
      </div>
    `;

    const studyBody = document.createElement('div');
    studyBody.className = 'evidence-study-body collapsible-body';

    if (study.series && study.series.length > 0) {
      study.series.forEach((series, seriesIdx) => {
        const seriesDiv = document.createElement('div');
        seriesDiv.className = 'evidence-series';

        const instanceCount = Array.isArray(series.sopInstances) ? series.sopInstances.length : (series.count || 0);

        const seriesHeader = document.createElement('div');
        seriesHeader.className = 'evidence-series-header collapsible-header';
        seriesHeader.dataset.section = `evidence-study-${studyIdx}-series-${seriesIdx}`;

        // Default-collapse series if it contains more than 4 instances
        if (instanceCount > 4) {
          seriesHeader.classList.add('collapsed');
        }

        seriesHeader.innerHTML = `
          <div class="evidence-title-row">
            <div class="evidence-title">
              📁 Series ${seriesIdx + 1}: ${series.seriesInstanceUID}
              <span class="evidence-count">(${instanceCount} instances)</span>
            </div>
            <button class="collapse-toggle" aria-label="Toggle collapse">
              <svg class="collapse-icon" width="20" height="20" viewBox="0 0 20 20" fill="none" xmlns="http://www.w3.org/2000/svg">
                <path d="M5 7.5L10 12.5L15 7.5" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
              </svg>
            </button>
          </div>
        `;

        const seriesBody = document.createElement('div');
        seriesBody.className = 'evidence-series-body collapsible-body';

        if (series.sopInstances && series.sopInstances.length > 0) {
          series.sopInstances.forEach(instance => {
            const instDiv = document.createElement('div');
            instDiv.className = 'evidence-instance';
            instDiv.textContent = `🔹 ${instance}`;
            seriesBody.appendChild(instDiv);
          });
        }

        seriesDiv.appendChild(seriesHeader);
        seriesDiv.appendChild(seriesBody);
        studyBody.appendChild(seriesDiv);
      });
    }

    studyDiv.appendChild(studyHeader);
    studyDiv.appendChild(studyBody);

    // Wire collapsible behavior for dynamically rendered evidence
    UIHelpers.setupCollapsibleSections();
    UIHelpers.restoreCollapsedStates();

    return studyDiv;
  },

  /**
   * Render content tree
   */
  renderContentTree(tree, container) {
    container.innerHTML = '';

    if (!tree || tree.length === 0) {
      container.innerHTML = '<div class="empty-state"><div class="empty-state-icon">🌳</div><div class="empty-state-text">No content tree found</div></div>';
      return;
    }

    tree.forEach(node => {
      container.appendChild(this.createTreeNode(node));
    });
  },

  /**
   * Render descriptors
   */
  renderDescriptors(descriptors, container) {
    container.innerHTML = '';

    if (!descriptors || !descriptors.descriptorTypes || descriptors.descriptorTypes.length === 0) {
      container.innerHTML = '<div class="empty-state"><div class="empty-state-icon">📝</div><div class="empty-state-text">No descriptors found</div></div>';
      return;
    }

    const info = document.createElement('div');
    info.className = 'info-grid';
    info.innerHTML = `
      <div class="info-item">
        <div class="info-label">Total Descriptors</div>
        <div class="info-value">${descriptors.count}</div>
      </div>
    `;
    container.appendChild(info);

    const list = document.createElement('div');
    list.style.marginTop = '16px';

    descriptors.descriptorTypes.forEach((desc, idx) => {
      const item = document.createElement('div');
      item.className = 'tree-node-detail';
      item.innerHTML = `<strong>${idx + 1}.</strong> ${desc}`;
      list.appendChild(item);
    });

    container.appendChild(list);
  },

  /**
   * Render all tags
   */
  renderTags(tags, container) {
    container.innerHTML = '';

    if (!tags || tags.length === 0) {
      container.innerHTML = '<div class="empty-state"><div class="empty-state-icon">🏷️</div><div class="empty-state-text">No tags found</div></div>';
      return;
    }

    tags.forEach(tag => {
      container.appendChild(this.createTagElement(tag));
    });
  }
};

// Export for global use
(function() {
  if (typeof window !== 'undefined') {
    window['DicomRenderer'] = DicomRenderer;
  }
})();
