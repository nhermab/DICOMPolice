/**
 * DICOM↔FHIR Bridge Converter
 * Handles conversion between DICOM MADO and FHIR ImagingStudy formats
 */

(function() {
    'use strict';

    // =========================
    // Configuration & State
    // =========================
    const API = {
        get contextPath() {
            try {
                const scripts = document.getElementsByTagName('script');
                for (const script of scripts) {
                    if (script.src && script.src.includes('converter.js')) {
                        const scriptUrl = new URL(script.src, window.location.origin);
                        const path = scriptUrl.pathname || '';
                        const idx = path.indexOf('/js/');
                        if (idx > 0) {
                            const prefix = path.substring(0, idx);
                            return prefix === '/' ? '' : prefix;
                        }
                    }
                }
            } catch (_) {}
            return window.location.pathname.substring(0, window.location.pathname.indexOf('/', 1)) || '';
        },

        get baseURL() {
            return window.location.origin + this.contextPath;
        },

        get endpoints() {
            return {
                convert: `${this.baseURL}/api/converter/convert`,
                roundtrip: `${this.baseURL}/api/converter/roundtrip`,
                downloadDicom: `${this.baseURL}/api/converter/download/dicom`,
                downloadDcmdump: `${this.baseURL}/api/converter/download/dcmdump`
            };
        }
    };

    const state = {
        file: null,
        fileType: null, // 'dicom' or 'fhir'
        conversionResult: null,
        roundtripResult: null,
        viewMode: 'formatted'
    };

    // =========================
    // DOM Elements
    // =========================
    const elements = {
        uploadArea: document.getElementById('upload-area'),
        fileInput: document.getElementById('file-input'),
        browseButton: document.getElementById('browse-button'),
        uploadContent: document.getElementById('upload-content'),
        fileInfo: document.getElementById('file-info'),
        fileName: document.getElementById('file-name'),
        fileSize: document.getElementById('file-size'),
        fileTypeBadge: document.getElementById('file-type-badge'),
        removeFileBtn: document.getElementById('remove-file-btn'),
        convertBtn: document.getElementById('convert-btn'),
        roundtripBtn: document.getElementById('roundtrip-btn'),
        loadingIndicator: document.getElementById('loading-indicator'),
        loadingText: document.getElementById('loading-text'),
        resultSection: document.getElementById('result-section'),
        resultTitle: document.getElementById('result-title'),
        sourceFormat: document.getElementById('source-format'),
        targetFormat: document.getElementById('target-format'),
        downloadOptions: document.getElementById('download-options'),
        viewFormatted: document.getElementById('view-formatted'),
        viewRaw: document.getElementById('view-raw'),
        resultContent: document.getElementById('result-content'),
        comparisonSection: document.getElementById('comparison-section'),
        diffStats: document.getElementById('diff-stats'),
        showUnchanged: document.getElementById('show-unchanged'),
        syncScroll: document.getElementById('sync-scroll'),
        originalContent: document.getElementById('original-content'),
        convertedContent: document.getElementById('converted-content'),
        originalInfo: document.getElementById('original-info'),
        convertedInfo: document.getElementById('converted-info')
    };

    // =========================
    // Initialization
    // =========================
    function init() {
        setupDragAndDrop();
        setupFileInput();
        setupButtons();
        setupCollapsibles();
        setupViewToggle();
        setupComparisonOptions();

        // Check for URL parameters to auto-load files
        checkUrlParameters();
    }

    // =========================
    // URL Parameter Handling
    // =========================
    async function checkUrlParameters() {
        const urlParams = new URLSearchParams(window.location.search);
        const loadUrl = urlParams.get('loadUrl');

        if (loadUrl) {
            console.log('Auto-loading file from URL:', loadUrl);
            await loadFileFromUrl(loadUrl);
        }
    }

    async function loadFileFromUrl(url) {
        try {
            showUrlLoadingState('Loading DICOM file from URL...');

            const response = await fetch(url, {
                headers: {
                    'Accept': 'application/dicom, application/octet-stream, */*'
                }
            });

            if (!response.ok) {
                throw new Error(`Failed to fetch file: HTTP ${response.status}`);
            }

            const blob = await response.blob();

            // Extract filename from URL or use default
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

            // Create a File object from the blob
            const file = new File([blob], filename, { type: 'application/dicom' });

            // Handle the file
            hideUrlLoadingState();
            handleFile(file);

            // Show success notification
            showSuccess(`Loaded: ${filename}`);

            // Auto-convert after a short delay
            setTimeout(() => {
                handleConvert();
            }, 500);

        } catch (error) {
            console.error('Failed to load file from URL:', error);
            hideUrlLoadingState();
            showError('Failed to load file: ' + error.message);
        }
    }

    function showUrlLoadingState(message) {
        if (elements.uploadContent) {
            elements.uploadContent.innerHTML = `
                <div class="loading-indicator" style="text-align: center; padding: 40px;">
                    <div class="spinner" style="width: 40px; height: 40px; border: 3px solid #e0e0e0; border-top-color: #0066cc; border-radius: 50%; animation: spin 0.8s linear infinite; margin: 0 auto 16px;"></div>
                    <p style="color: #666; font-weight: 500;">${message}</p>
                </div>
            `;
        }
    }

    function hideUrlLoadingState() {
        if (elements.uploadContent) {
            elements.uploadContent.innerHTML = `
                <div class="upload-icon">
                    <svg width="64" height="64" viewBox="0 0 64 64" fill="none" xmlns="http://www.w3.org/2000/svg">
                        <rect x="8" y="8" width="48" height="48" rx="8" stroke="currentColor" stroke-width="2" stroke-dasharray="4 4"/>
                        <path d="M32 20V44M32 20L24 28M32 20L40 28" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
                    </svg>
                </div>
                <h3>Drag & Drop your file here</h3>
                <p>or</p>
                <button type="button" class="btn btn-secondary" id="browse-button">Browse Files</button>
                <p class="supported-formats">Supported: DICOM (.dcm) or FHIR (.json)</p>
            `;
            // Re-attach browse button listener
            const newBrowseBtn = document.getElementById('browse-button');
            if (newBrowseBtn) {
                newBrowseBtn.addEventListener('click', () => {
                    elements.fileInput.click();
                });
            }
        }
    }

    function showSuccess(message) {
        // Remove any existing success message
        const existing = document.querySelector('.converter-success-toast');
        if (existing) existing.remove();

        const toast = document.createElement('div');
        toast.className = 'converter-success-toast';
        toast.style.cssText = `
            position: fixed;
            bottom: 20px;
            right: 20px;
            background: white;
            padding: 12px 20px;
            border-radius: 8px;
            box-shadow: 0 4px 20px rgba(0,0,0,0.15);
            border-left: 4px solid #28a745;
            z-index: 1000;
            animation: slideInRight 0.3s ease-out;
        `;
        toast.innerHTML = `✅ ${message}`;
        document.body.appendChild(toast);

        setTimeout(() => {
            toast.style.animation = 'fadeOut 0.3s ease-out';
            setTimeout(() => toast.remove(), 300);
        }, 3000);
    }

    // =========================
    // Drag & Drop
    // =========================
    function setupDragAndDrop() {
        const uploadArea = elements.uploadArea;

        ['dragenter', 'dragover', 'dragleave', 'drop'].forEach(eventName => {
            uploadArea.addEventListener(eventName, preventDefaults, false);
        });

        ['dragenter', 'dragover'].forEach(eventName => {
            uploadArea.addEventListener(eventName, () => {
                uploadArea.classList.add('drag-over');
            }, false);
        });

        ['dragleave', 'drop'].forEach(eventName => {
            uploadArea.addEventListener(eventName, () => {
                uploadArea.classList.remove('drag-over');
            }, false);
        });

        uploadArea.addEventListener('drop', handleDrop, false);
    }

    function preventDefaults(e) {
        e.preventDefault();
        e.stopPropagation();
    }

    function handleDrop(e) {
        const files = e.dataTransfer.files;
        if (files.length > 0) {
            handleFile(files[0]);
        }
    }

    // =========================
    // File Input
    // =========================
    function setupFileInput() {
        elements.browseButton.addEventListener('click', () => {
            elements.fileInput.click();
        });

        elements.fileInput.addEventListener('change', (e) => {
            if (e.target.files.length > 0) {
                handleFile(e.target.files[0]);
            }
        });

        elements.removeFileBtn.addEventListener('click', clearFile);
    }

    function handleFile(file) {
        const extension = file.name.split('.').pop().toLowerCase();

        if (['dcm', 'dicom'].includes(extension)) {
            state.fileType = 'dicom';
        } else if (extension === 'json') {
            state.fileType = 'fhir';
        } else {
            showError('Unsupported file type. Please upload a .dcm, .dicom, or .json file.');
            return;
        }

        state.file = file;
        displayFileInfo(file);
        enableButtons();
        hideResults();
    }

    function displayFileInfo(file) {
        elements.uploadContent.style.display = 'none';
        elements.fileInfo.style.display = 'flex';
        elements.fileName.textContent = file.name;
        elements.fileSize.textContent = formatFileSize(file.size);

        elements.fileTypeBadge.textContent = state.fileType.toUpperCase();
        elements.fileTypeBadge.className = 'file-type-badge ' + state.fileType;
    }

    function clearFile() {
        state.file = null;
        state.fileType = null;
        elements.fileInput.value = '';
        elements.uploadContent.style.display = 'block';
        elements.fileInfo.style.display = 'none';
        disableButtons();
        hideResults();
    }

    function formatFileSize(bytes) {
        if (bytes === 0) return '0 Bytes';
        const k = 1024;
        const sizes = ['Bytes', 'KB', 'MB', 'GB'];
        const i = Math.floor(Math.log(bytes) / Math.log(k));
        return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
    }

    // =========================
    // Button Handlers
    // =========================
    function setupButtons() {
        elements.convertBtn.addEventListener('click', handleConvert);
        elements.roundtripBtn.addEventListener('click', handleRoundtrip);
    }

    function enableButtons() {
        elements.convertBtn.disabled = false;
        elements.roundtripBtn.disabled = false;
    }

    function disableButtons() {
        elements.convertBtn.disabled = true;
        elements.roundtripBtn.disabled = true;
    }

    async function handleConvert() {
        if (!state.file) return;

        showLoading('Converting file...');

        try {
            const formData = new FormData();
            formData.append('file', state.file);
            formData.append('sourceType', state.fileType);

            const response = await fetch(API.endpoints.convert, {
                method: 'POST',
                body: formData
            });

            if (!response.ok) {
                const errorText = await response.text();
                throw new Error(errorText || 'Conversion failed');
            }

            const result = await response.json();
            state.conversionResult = result;
            displayConversionResult(result);
        } catch (error) {
            showError('Conversion failed: ' + error.message);
        } finally {
            hideLoading();
        }
    }

    async function handleRoundtrip() {
        if (!state.file) return;

        showLoading('Performing round-trip conversion...');

        try {
            const formData = new FormData();
            formData.append('file', state.file);
            formData.append('sourceType', state.fileType);

            const response = await fetch(API.endpoints.roundtrip, {
                method: 'POST',
                body: formData
            });

            if (!response.ok) {
                const errorText = await response.text();
                throw new Error(errorText || 'Round-trip conversion failed');
            }

            const result = await response.json();
            state.roundtripResult = result;
            state.conversionResult = result;
            displayConversionResult(result);
            displayComparison(result);
        } catch (error) {
            showError('Round-trip conversion failed: ' + error.message);
        } finally {
            hideLoading();
        }
    }

    // =========================
    // Result Display
    // =========================
    function displayConversionResult(result) {
        elements.resultSection.style.display = 'block';

        // Update format badges
        const sourceFormat = state.fileType.toUpperCase();
        const targetFormat = state.fileType === 'dicom' ? 'FHIR' : 'DICOM';

        elements.sourceFormat.textContent = sourceFormat;
        elements.sourceFormat.className = 'conversion-badge source ' + state.fileType;
        elements.targetFormat.textContent = targetFormat;
        elements.targetFormat.className = 'conversion-badge target ' + (state.fileType === 'dicom' ? 'fhir' : 'dicom');

        // Update title
        elements.resultTitle.textContent = state.fileType === 'dicom'
            ? '📋 FHIR Output'
            : '📋 DICOM Output';

        // Display file size comparison
        displaySizeComparison(result, sourceFormat, targetFormat);

        // Setup download options
        setupDownloadOptions(result, targetFormat);

        // Display content
        renderResult(result);

        // Scroll to result
        elements.resultSection.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }

    function displaySizeComparison(result, sourceFormat, targetFormat) {
        // Calculate sizes
        const originalSize = state.file ? state.file.size : 0;
        let convertedSize = 0;

        if (targetFormat === 'FHIR') {
            // JSON size
            convertedSize = new Blob([JSON.stringify(result.converted)]).size;
        } else {
            // DICOM size from base64
            if (result.convertedBase64) {
                convertedSize = Math.ceil((result.convertedBase64.length * 3) / 4);
            }
        }

        // Calculate difference and percentage
        const difference = convertedSize - originalSize;
        const percentageChange = originalSize > 0 ? ((difference / originalSize) * 100) : 0;
        const isLarger = difference > 0;

        // Find or create size comparison container
        let sizeContainer = document.getElementById('size-comparison-container');
        if (!sizeContainer) {
            sizeContainer = document.createElement('div');
            sizeContainer.id = 'size-comparison-container';
            sizeContainer.className = 'size-comparison-container';

            // Insert at the beginning of card body
            const cardBody = elements.resultSection.querySelector('.card-body');
            if (cardBody) {
                cardBody.insertBefore(sizeContainer, cardBody.firstChild);
            }
        }

        // Determine message based on actual comparison
        let comparisonMessage = '';
        if (isLarger) {
            // Output is larger than input
            comparisonMessage = `<strong>${targetFormat} is ${Math.abs(percentageChange).toFixed(1)}% larger than ${sourceFormat}</strong>`;
        } else {
            // Output is smaller than input
            comparisonMessage = `<strong>${targetFormat} is ${Math.abs(percentageChange).toFixed(1)}% smaller than ${sourceFormat}</strong>`;
        }

        // Create the comparison HTML
        sizeContainer.innerHTML = `
            <div class="size-comparison-card ${isLarger ? 'size-increase' : 'size-decrease'}">
                <div class="size-comparison-title">
                    📊 File Size Comparison
                </div>
                <div class="size-comparison-content">
                    <div class="size-box original">
                        <div class="size-label">${sourceFormat} Original</div>
                        <div class="size-value">${formatBytes(originalSize)}</div>
                        <div class="size-bytes">(${originalSize.toLocaleString()} bytes)</div>
                    </div>
                    <div class="size-arrow">
                        ${isLarger ? '📈' : '📉'}
                    </div>
                    <div class="size-box converted">
                        <div class="size-label">${targetFormat} Output</div>
                        <div class="size-value">${formatBytes(convertedSize)}</div>
                        <div class="size-bytes">(${convertedSize.toLocaleString()} bytes)</div>
                    </div>
                </div>
                <div class="size-comparison-summary ${isLarger ? 'increase' : 'decrease'}">
                    <div class="size-difference">
                        ${isLarger ? '⚠️ ' : '✅ '}
                        <strong>${isLarger ? '+' : ''}${formatBytes(Math.abs(difference))}</strong>
                        <span class="percentage">(${isLarger ? '+' : ''}${Math.abs(percentageChange).toFixed(1)}%)</span>
                    </div>
                    <div class="size-message">
                        ${comparisonMessage}
                    </div>
                </div>
            </div>
        `;
    }

    function formatBytes(bytes) {
        if (bytes === 0) return '0 Bytes';
        const k = 1024;
        const sizes = ['Bytes', 'KB', 'MB', 'GB'];
        const i = Math.floor(Math.log(bytes) / Math.log(k));
        return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
    }

    function setupDownloadOptions(result, targetFormat) {
        elements.downloadOptions.innerHTML = '';

        if (targetFormat === 'FHIR') {
            // FHIR output - JSON download
            const jsonBtn = createDownloadButton('📥 Download JSON', () => {
                downloadJson(result.converted, 'fhir-output.json');
            });
            elements.downloadOptions.appendChild(jsonBtn);
        } else {
            // DICOM output - Binary and dcmdump downloads
            const binaryBtn = createDownloadButton('📥 Download DICOM', () => {
                downloadDicomBinary(result.convertedBase64, result.suggestedFilename || 'output.dcm');
            });
            elements.downloadOptions.appendChild(binaryBtn);

            const dcmdumpBtn = createDownloadButton('📄 Download dcmdump', () => {
                downloadText(result.dcmdump, 'dcmdump-output.txt');
            });
            elements.downloadOptions.appendChild(dcmdumpBtn);
        }
    }

    function createDownloadButton(label, onClick) {
        const btn = document.createElement('button');
        btn.className = 'download-btn';
        btn.innerHTML = `<span class="download-icon">📥</span><span>${label.replace('📥 ', '').replace('📄 ', '')}</span>`;
        btn.addEventListener('click', onClick);
        return btn;
    }

    function renderResult(result) {
        const targetFormat = state.fileType === 'dicom' ? 'fhir' : 'dicom';

        if (state.viewMode === 'formatted') {
            if (targetFormat === 'fhir') {
                elements.resultContent.innerHTML = `<pre>${highlightJson(result.converted)}</pre>`;
            } else {
                elements.resultContent.innerHTML = `<pre>${highlightDcmdump(result.dcmdump)}</pre>`;
            }
        } else {
            // Raw view
            if (targetFormat === 'fhir') {
                elements.resultContent.innerHTML = `<pre>${escapeHtml(JSON.stringify(result.converted, null, 2))}</pre>`;
            } else {
                elements.resultContent.innerHTML = `<pre>${escapeHtml(result.dcmdump)}</pre>`;
            }
        }
    }

    // =========================
    // Comparison / Diff View
    // =========================
    function displayComparison(result) {
        if (!result.original || !result.roundtrip) return;

        // Debug: Check what type of data we're receiving
        console.log('result.original type:', typeof result.original);
        console.log('result.original preview:', typeof result.original === 'string' ? result.original.substring(0, 200) : result.original);
        console.log('result.roundtrip type:', typeof result.roundtrip);

        elements.comparisonSection.style.display = 'block';

        const originalStr = formatForComparison(result.original, state.fileType);
        const roundtripStr = formatForComparison(result.roundtrip, state.fileType);

        const diff = computeLCSDiff(originalStr, roundtripStr);

        // Update stats
        elements.diffStats.innerHTML = `
            <span class="added">${diff.stats.added} added</span> · 
            <span class="removed">${diff.stats.removed} removed</span> · 
            <span class="unchanged">${diff.stats.unchanged} unchanged</span>
        `;

        // Update panel info
        elements.originalInfo.textContent = state.fileType === 'dicom' ? 'DICOM Source' : 'FHIR Source';
        elements.convertedInfo.textContent = 'After Round-Trip';

        // Render diff panels
        renderDiffPanel(elements.originalContent, diff.leftLines);
        renderDiffPanel(elements.convertedContent, diff.rightLines);

        // Setup scroll sync
        setupScrollSync();
        
        // Scroll comparison into view
        elements.comparisonSection.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }

    function formatForComparison(data, format) {
        if (format === 'dicom') {
            // For DICOM, use dcmdump format
            return typeof data === 'string' ? data : JSON.stringify(data, null, 2);
        } else {
            // For FHIR, handle both strings and objects
            if (typeof data === 'string') {
                // If it's a string that looks like JSON, it's already formatted
                return data;
            } else {
                // If it's an object, stringify it
                return JSON.stringify(data, null, 2);
            }
        }
    }

    /**
     * Compute LCS (Longest Common Subsequence) based diff
     * This properly handles insertions and deletions without getting confused
     */
    function computeLCSDiff(originalText, convertedText) {
        const originalLines = originalText.split('\n');
        const convertedLines = convertedText.split('\n');
        
        // Compute LCS matrix
        const lcs = computeLCS(originalLines, convertedLines);
        
        // Backtrack to get the diff
        const diff = backtrackLCS(lcs, originalLines, convertedLines);
        
        return diff;
    }

    /**
     * Compute LCS matrix using dynamic programming
     */
    function computeLCS(a, b) {
        const m = a.length;
        const n = b.length;
        
        // Create matrix
        const dp = Array(m + 1).fill(null).map(() => Array(n + 1).fill(0));
        
        // Fill the matrix
        for (let i = 1; i <= m; i++) {
            for (let j = 1; j <= n; j++) {
                if (a[i - 1] === b[j - 1]) {
                    dp[i][j] = dp[i - 1][j - 1] + 1;
                } else {
                    dp[i][j] = Math.max(dp[i - 1][j], dp[i][j - 1]);
                }
            }
        }
        
        return dp;
    }

    /**
     * Backtrack through LCS matrix to generate aligned diff
     */
    function backtrackLCS(dp, originalLines, convertedLines) {
        const leftLines = [];
        const rightLines = [];
        const stats = { added: 0, removed: 0, unchanged: 0 };
        
        let i = originalLines.length;
        let j = convertedLines.length;
        
        // Temporary storage for backtracking (we build in reverse)
        const operations = [];
        
        while (i > 0 || j > 0) {
            if (i > 0 && j > 0 && originalLines[i - 1] === convertedLines[j - 1]) {
                // Lines match
                operations.unshift({ type: 'unchanged', left: i - 1, right: j - 1 });
                i--;
                j--;
            } else if (j > 0 && (i === 0 || dp[i][j - 1] >= dp[i - 1][j])) {
                // Line added in converted
                operations.unshift({ type: 'added', right: j - 1 });
                j--;
            } else {
                // Line removed from original
                operations.unshift({ type: 'removed', left: i - 1 });
                i--;
            }
        }
        
        // Convert operations to line data
        let leftLineNum = 1;
        let rightLineNum = 1;
        
        for (const op of operations) {
            if (op.type === 'unchanged') {
                leftLines.push({
                    number: leftLineNum++,
                    content: originalLines[op.left],
                    status: 'unchanged'
                });
                rightLines.push({
                    number: rightLineNum++,
                    content: convertedLines[op.right],
                    status: 'unchanged'
                });
                stats.unchanged++;
            } else if (op.type === 'removed') {
                leftLines.push({
                    number: leftLineNum++,
                    content: originalLines[op.left],
                    status: 'removed'
                });
                rightLines.push({
                    number: '',
                    content: '',
                    status: 'empty'
                });
                stats.removed++;
            } else if (op.type === 'added') {
                leftLines.push({
                    number: '',
                    content: '',
                    status: 'empty'
                });
                rightLines.push({
                    number: rightLineNum++,
                    content: convertedLines[op.right],
                    status: 'added'
                });
                stats.added++;
            }
        }
        
        return { leftLines, rightLines, stats };
    }

    function renderDiffPanel(container, lines) {
        const showUnchanged = elements.showUnchanged.checked;

        container.innerHTML = lines.map(line => {
            const hiddenClass = !showUnchanged && line.status === 'unchanged' ? ' hidden' : '';
            const lineNum = line.number !== '' ? line.number : '&nbsp;';
            return `
                <div class="diff-line ${line.status}${hiddenClass}" data-line="${line.number}">
                    <span class="diff-line-number">${lineNum}</span>
                    <span class="diff-line-content">${escapeHtml(line.content)}</span>
                </div>
            `;
        }).join('');
    }

    function setupScrollSync() {
        const origPanel = elements.originalContent;
        const convPanel = elements.convertedContent;
        
        // Remove old listeners by cloning
        const newOrigPanel = origPanel.cloneNode(true);
        const newConvPanel = convPanel.cloneNode(true);
        origPanel.parentNode.replaceChild(newOrigPanel, origPanel);
        convPanel.parentNode.replaceChild(newConvPanel, convPanel);
        
        // Update references
        elements.originalContent = newOrigPanel;
        elements.convertedContent = newConvPanel;
        
        let isSyncing = false;

        function syncScroll(source, target) {
            if (!elements.syncScroll.checked || isSyncing) return;

            isSyncing = true;
            target.scrollTop = source.scrollTop;
            target.scrollLeft = source.scrollLeft;
            requestAnimationFrame(() => {
                isSyncing = false;
            });
        }

        newOrigPanel.addEventListener('scroll', () => syncScroll(newOrigPanel, newConvPanel));
        newConvPanel.addEventListener('scroll', () => syncScroll(newConvPanel, newOrigPanel));
    }

    // =========================
    // View Toggle
    // =========================
    function setupViewToggle() {
        elements.viewFormatted.addEventListener('click', () => {
            state.viewMode = 'formatted';
            elements.viewFormatted.classList.add('active');
            elements.viewRaw.classList.remove('active');
            if (state.conversionResult) {
                renderResult(state.conversionResult);
            }
        });

        elements.viewRaw.addEventListener('click', () => {
            state.viewMode = 'raw';
            elements.viewRaw.classList.add('active');
            elements.viewFormatted.classList.remove('active');
            if (state.conversionResult) {
                renderResult(state.conversionResult);
            }
        });
    }

    // =========================
    // Comparison Options
    // =========================
    function setupComparisonOptions() {
        elements.showUnchanged.addEventListener('change', () => {
            if (state.roundtripResult) {
                displayComparison(state.roundtripResult);
            }
        });
    }

    // =========================
    // Collapsible Sections
    // =========================
    function setupCollapsibles() {
        document.querySelectorAll('.collapsible-header').forEach(header => {
            header.addEventListener('click', (e) => {
                // Don't toggle if clicking the collapse button itself
                if (e.target.closest('.collapse-toggle')) {
                    header.classList.toggle('collapsed');
                } else if (!e.target.closest('button')) {
                    header.classList.toggle('collapsed');
                }
            });

            // Handle just the button click
            const toggleBtn = header.querySelector('.collapse-toggle');
            if (toggleBtn) {
                toggleBtn.addEventListener('click', (e) => {
                    e.stopPropagation();
                    header.classList.toggle('collapsed');
                });
            }
        });
    }

    // =========================
    // Download Functions
    // =========================
    function downloadJson(data, filename) {
        const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
        downloadBlob(blob, filename);
    }

    function downloadText(text, filename) {
        const blob = new Blob([text], { type: 'text/plain' });
        downloadBlob(blob, filename);
    }

    function downloadDicomBinary(base64Data, filename) {
        const binaryString = atob(base64Data);
        const bytes = new Uint8Array(binaryString.length);
        for (let i = 0; i < binaryString.length; i++) {
            bytes[i] = binaryString.charCodeAt(i);
        }
        const blob = new Blob([bytes], { type: 'application/dicom' });
        downloadBlob(blob, filename);
    }

    function downloadBlob(blob, filename) {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = filename;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(url);
    }

    // =========================
    // Syntax Highlighting
    // =========================
    function highlightJson(obj) {
        const json = JSON.stringify(obj, null, 2);
        return json
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/("(\\u[a-zA-Z0-9]{4}|\\[^u]|[^\\"])*"(\s*:)?|\b(true|false|null)\b|-?\d+(?:\.\d*)?(?:[eE][+\-]?\d+)?)/g,
                (match) => {
                    let cls = 'json-number';
                    if (/^"/.test(match)) {
                        if (/:$/.test(match)) {
                            cls = 'json-key';
                            match = match.replace(/:$/, '') + '<span class="json-bracket">:</span>';
                        } else {
                            cls = 'json-string';
                        }
                    } else if (/true|false/.test(match)) {
                        cls = 'json-boolean';
                    } else if (/null/.test(match)) {
                        cls = 'json-null';
                    }
                    return '<span class="' + cls + '">' + match + '</span>';
                })
            .replace(/([{}\[\]])/g, '<span class="json-bracket">$1</span>');
    }

    function highlightDcmdump(text) {
        if (!text) return '';

        return escapeHtml(text)
            .split('\n')
            .map(line => {
                // Match pattern like (0008,0005) CS [ISO_IR 192]   # 12, 1 SpecificCharacterSet
                const match = line.match(/^(\([0-9A-Fa-f]{4},[0-9A-Fa-f]{4}\))\s+([A-Z]{2})\s+(.+?)(\s+#\s*\d+,\s*\d+\s+)(.*)$/);
                if (match) {
                    return `<span class="dicom-tag">${match[1]}</span> <span class="dicom-vr">${match[2]}</span> <span class="dicom-value">${match[3]}</span>${match[4]}<span class="dicom-name">${match[5]}</span>`;
                }
                return line;
            })
            .join('\n');
    }

    // =========================
    // Utility Functions
    // =========================
    function escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    function showLoading(message) {
        elements.loadingText.textContent = message;
        elements.loadingIndicator.style.display = 'block';
        disableButtons();
    }

    function hideLoading() {
        elements.loadingIndicator.style.display = 'none';
        enableButtons();
    }

    function showError(message) {
        // Create or update error banner
        let errorBanner = document.getElementById('error-banner');
        if (!errorBanner) {
            errorBanner = document.createElement('div');
            errorBanner.id = 'error-banner';
            errorBanner.className = 'error-banner';
            errorBanner.style.cssText = 'background: var(--error-light); color: var(--error-red); padding: 16px; border-radius: var(--radius-md); margin-bottom: 16px; display: flex; justify-content: space-between; align-items: center;';

            const container = document.querySelector('.main-content .container');
            container.insertBefore(errorBanner, container.firstChild.nextSibling);
        }

        errorBanner.innerHTML = `
            <span>⚠️ ${message}</span>
            <button onclick="this.parentElement.style.display='none'" style="background:none;border:none;cursor:pointer;font-size:18px;">✕</button>
        `;
        errorBanner.style.display = 'flex';

        setTimeout(() => {
            if (errorBanner) errorBanner.style.display = 'none';
        }, 10000);
    }

    function hideResults() {
        elements.resultSection.style.display = 'none';
        elements.comparisonSection.style.display = 'none';
        state.conversionResult = null;
        state.roundtripResult = null;
    }

    // Initialize when DOM is ready
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }

})();

