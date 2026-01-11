/**
 * DICOM Downloader Module
 * Downloads DICOM images from MADO manifests using WADO-RS endpoints
 */

const DicomDownloader = (function() {
    // State
    const state = {
        manifest: null,
        studyData: null,
        selectedItems: new Set(),
        isDownloading: false,
        downloadController: null,
        downloadStats: {
            total: 0,
            completed: 0,
            failed: 0,
            totalBytes: 0,
            startTime: null
        }
    };

    // DOM Elements
    let elements = {};

    function init() {
        elements = {
            // Input elements
            madoUrl: document.getElementById('madoUrl'),
            loadUrlBtn: document.getElementById('loadUrlBtn'),
            fileInput: document.getElementById('fileInput'),
            fileDropZone: document.getElementById('fileDropZone'),
            pasteArea: document.getElementById('pasteArea'),
            loadPasteBtn: document.getElementById('loadPasteBtn'),
            // Panels
            manifestInfoPanel: document.getElementById('manifestInfoPanel'),
            manifestInfoGrid: document.getElementById('manifestInfoGrid'),
            downloadSettingsPanel: document.getElementById('downloadSettingsPanel'),
            studyTreePanel: document.getElementById('studyTreePanel'),
            progressPanel: document.getElementById('progressPanel'),
            downloadActions: document.getElementById('downloadActions'),
            // Tree
            treeContainer: document.getElementById('treeContainer'),
            selectionBadge: document.getElementById('selectionBadge'),
            downloadSummary: document.getElementById('downloadSummary'),
            // Buttons
            selectAllBtn: document.getElementById('selectAllBtn'),
            deselectAllBtn: document.getElementById('deselectAllBtn'),
            expandAllBtn: document.getElementById('expandAllBtn'),
            collapseAllBtn: document.getElementById('collapseAllBtn'),
            downloadBtn: document.getElementById('downloadBtn'),
            cancelDownloadBtn: document.getElementById('cancelDownloadBtn'),
            clearLogBtn: document.getElementById('clearLogBtn'),
            // Progress elements
            progressPercent: document.getElementById('progressPercent'),
            progressCount: document.getElementById('progressCount'),
            progressSize: document.getElementById('progressSize'),
            progressSpeed: document.getElementById('progressSpeed'),
            progressBar: document.getElementById('progressBar'),
            progressCurrent: document.getElementById('progressCurrent'),
            progressLog: document.getElementById('progressLog'),
            // Banners
            errorBanner: document.getElementById('errorBanner'),
            successBanner: document.getElementById('successBanner'),
            // Settings
            downloadFolder: document.getElementById('downloadFolder'),
            fileNaming: document.getElementById('fileNaming'),
            downloadKeyImages: document.getElementById('downloadKeyImages'),
            createManifest: document.getElementById('createManifest')
        };

        setupEventListeners();
        checkUrlParams();
    }

    function setupEventListeners() {
        // Tab switching
        document.querySelectorAll('.input-tab').forEach(tab => {
            tab.addEventListener('click', () => switchTab(tab.dataset.tab));
        });

        // Load buttons
        elements.loadUrlBtn?.addEventListener('click', loadFromUrl);
        elements.loadPasteBtn?.addEventListener('click', loadFromPaste);

        // File input
        elements.fileDropZone?.addEventListener('click', () => elements.fileInput.click());
        elements.fileInput?.addEventListener('change', handleFileSelect);

        // Drag and drop
        elements.fileDropZone?.addEventListener('dragover', handleDragOver);
        elements.fileDropZone?.addEventListener('dragleave', handleDragLeave);
        elements.fileDropZone?.addEventListener('drop', handleDrop);

        // Tree controls
        elements.selectAllBtn?.addEventListener('click', selectAll);
        elements.deselectAllBtn?.addEventListener('click', deselectAll);
        elements.expandAllBtn?.addEventListener('click', expandAll);
        elements.collapseAllBtn?.addEventListener('click', collapseAll);

        // Download
        elements.downloadBtn?.addEventListener('click', startDownload);
        elements.cancelDownloadBtn?.addEventListener('click', cancelDownload);
        elements.clearLogBtn?.addEventListener('click', clearLog);

        // Enter key in URL field
        elements.madoUrl?.addEventListener('keypress', (e) => {
            if (e.key === 'Enter') loadFromUrl();
        });
    }

    function checkUrlParams() {
        const urlParams = new URLSearchParams(window.location.search);
        const manifestUrl = urlParams.get('manifestUrl') || urlParams.get('url');

        if (manifestUrl) {
            elements.madoUrl.value = manifestUrl;
            loadFromUrl();
        }
    }

    // ==============================
    // Tab Management
    // ==============================

    function switchTab(tabId) {
        document.querySelectorAll('.input-tab').forEach(t => t.classList.remove('active'));
        document.querySelectorAll('.tab-content').forEach(c => c.classList.remove('active'));

        document.querySelector(`.input-tab[data-tab="${tabId}"]`)?.classList.add('active');
        document.getElementById(`${tabId}Tab`)?.classList.add('active');
    }

    // ==============================
    // File Handling
    // ==============================

    function handleDragOver(e) {
        e.preventDefault();
        elements.fileDropZone.classList.add('drag-over');
    }

    function handleDragLeave(e) {
        e.preventDefault();
        elements.fileDropZone.classList.remove('drag-over');
    }

    function handleDrop(e) {
        e.preventDefault();
        elements.fileDropZone.classList.remove('drag-over');

        const files = e.dataTransfer.files;
        if (files.length > 0) {
            processFile(files[0]);
        }
    }

    function handleFileSelect(e) {
        const files = e.target.files;
        if (files.length > 0) {
            processFile(files[0]);
        }
    }

    async function processFile(file) {
        try {
            // Read file as text first to check its content
            const text = await file.text();

            // Try to parse as JSON to check if it's a FHIR Binary resource
            let jsonData = null;
            try {
                jsonData = JSON.parse(text);
            } catch (e) {
                // Not JSON, might be raw DICOM
            }

            // Check if it's a FHIR Binary resource with base64 DICOM data
            if (jsonData && jsonData.resourceType === 'Binary' && jsonData.data) {
                showToast('Detected FHIR Binary resource, extracting DICOM...', 'info');

                // Decode base64 data to blob
                const blob = base64ToBlob(jsonData.data, jsonData.contentType || 'application/dicom');

                // Extract filename
                let filename = jsonData.id || file.name || 'manifest';
                if (!filename.endsWith('.dcm')) {
                    filename += '.dcm';
                }

                // Convert DICOM to FHIR
                showToast(`Converting DICOM file: ${filename}`, 'info');
                const fhirJson = await convertDicomToFhir(blob, filename);
                parseAndLoadManifest(fhirJson, file.name);

            } else {
                // Check if it's a DICOM file
                const isDicom = file.name.toLowerCase().endsWith('.dcm') ||
                               file.type === 'application/dicom';

                if (isDicom) {
                    // It's a raw DICOM file
                    showToast(`Converting DICOM file: ${file.name}`, 'info');
                    const blob = new Blob([text], { type: 'application/dicom' });
                    const fhirJson = await convertDicomToFhir(blob, file.name);
                    parseAndLoadManifest(fhirJson, file.name);
                } else if (jsonData) {
                    // It's a FHIR JSON manifest (already parsed)
                    parseAndLoadManifest(text, file.name);
                } else {
                    // Assume it's plain text JSON
                    parseAndLoadManifest(text, file.name);
                }
            }
        } catch (error) {
            showError(`Failed to read file: ${error.message}`);
        }
    }

    /**
     * Convert a DICOM MADO file to FHIR using the backend converter API
     */
    async function convertDicomToFhir(fileOrBlob, filename) {
        try {
            const formData = new FormData();
            formData.append('file', fileOrBlob, filename);
            formData.append('sourceType', 'dicom');

            showToast('Converting DICOM to FHIR...', 'info');

            // Determine the API endpoint with proper context path handling
            const contextPath = getContextPath();
            const apiUrl = `${window.location.origin}${contextPath}/api/converter/convert`;

            const response = await fetch(apiUrl, {
                method: 'POST',
                body: formData
            });

            if (!response.ok) {
                const errorText = await response.text();
                throw new Error(`Conversion failed: ${response.status} ${errorText}`);
            }

            const result = await response.json();

            // The converter returns { converted: {...}, fhirJson: "...", sourceType, targetType }
            if (!result.converted) {
                throw new Error('Invalid conversion response - missing converted data');
            }

            showToast('✓ Successfully converted DICOM to FHIR', 'success');

            // Return the FHIR Bundle as JSON string
            // Use fhirJson if available (formatted), otherwise stringify converted
            return result.fhirJson || JSON.stringify(result.converted);

        } catch (error) {
            throw new Error(`DICOM to FHIR conversion failed: ${error.message}`);
        }
    }

    /**
     * Get the context path for proper API endpoint resolution
     */
    function getContextPath() {
        try {
            const scripts = document.getElementsByTagName('script');
            for (const script of scripts) {
                if (script.src && script.src.includes('dicom-downloader.js')) {
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
    }

    // ==============================
    // Loading Functions
    // ==============================

    async function loadFromUrl() {
        const url = elements.madoUrl.value.trim();
        if (!url) {
            showError('Please enter a MADO manifest URL');
            return;
        }

        hideError();
        showLoading();

        try {
            // Handle relative URLs
            let fetchUrl = url;
            if (!url.startsWith('http')) {
                // Assume it's a relative path like Binary/xxx or fhir/Binary/xxx
                fetchUrl = `./${url.replace(/^\.\//, '')}`;
            }

            // Fetch the file
            const response = await fetch(fetchUrl, {
                headers: {
                    'Accept': 'application/dicom, application/fhir+json, application/json, */*'
                }
            });

            if (!response.ok) {
                throw new Error(`HTTP ${response.status}: ${response.statusText}`);
            }

            const contentType = response.headers.get('content-type') || '';

            // Try to parse as JSON first to check if it's a FHIR Binary resource
            let jsonData = null;
            const text = await response.text();
            try {
                jsonData = JSON.parse(text);
            } catch (e) {
                // Not JSON, might be raw DICOM
            }

            // Check if it's a FHIR Binary resource with base64 DICOM data
            if (jsonData && jsonData.resourceType === 'Binary' && jsonData.data) {
                showToast('Detected FHIR Binary resource, extracting DICOM...', 'info');

                // Decode base64 data to blob
                const blob = base64ToBlob(jsonData.data, jsonData.contentType || 'application/dicom');

                // Extract filename
                let filename = jsonData.id || 'manifest';
                if (!filename.endsWith('.dcm')) {
                    filename += '.dcm';
                }

                // Convert DICOM to FHIR
                const fhirJson = await convertDicomToFhir(blob, filename);
                parseAndLoadManifest(fhirJson, url);

            } else if (contentType.includes('application/dicom') ||
                       contentType.includes('application/octet-stream') ||
                       url.endsWith('.dcm')) {

                // It's a raw DICOM file - convert to FHIR using the backend converter
                showToast('Detected DICOM file, converting to FHIR...', 'info');
                const blob = new Blob([text], { type: 'application/dicom' });

                // Extract a proper filename from the URL or use default
                let filename = 'manifest.dcm';
                try {
                    const urlObj = new URL(url, window.location.origin);
                    const pathname = urlObj.pathname;
                    const parts = pathname.split('/');
                    const lastPart = parts[parts.length - 1];
                    if (lastPart && lastPart.length > 0) {
                        filename = lastPart.endsWith('.dcm') ? lastPart : lastPart + '.dcm';
                    }
                } catch (e) {
                    // If URL parsing fails, use the default filename
                }

                const fhirJson = await convertDicomToFhir(blob, filename);
                parseAndLoadManifest(fhirJson, url);

            } else {
                // It's already FHIR JSON
                parseAndLoadManifest(text, url);
            }

        } catch (error) {
            showError(`Failed to load manifest: ${error.message}`);
            hideLoading();
        }
    }

    /**
     * Convert base64 string to Blob
     */
    function base64ToBlob(base64, contentType = '') {
        const byteCharacters = atob(base64);
        const byteArrays = [];

        const sliceSize = 512;
        for (let offset = 0; offset < byteCharacters.length; offset += sliceSize) {
            const slice = byteCharacters.slice(offset, offset + sliceSize);
            const byteNumbers = new Array(slice.length);
            for (let i = 0; i < slice.length; i++) {
                byteNumbers[i] = slice.charCodeAt(i);
            }
            const byteArray = new Uint8Array(byteNumbers);
            byteArrays.push(byteArray);
        }

        return new Blob(byteArrays, { type: contentType });
    }

    async function loadFromPaste() {
        const text = elements.pasteArea.value.trim();
        if (!text) {
            showError('Please paste a MADO manifest');
            return;
        }

        parseAndLoadManifest(text, 'pasted-manifest');
    }

    async function extractJsonFromMultipart(response) {
        const text = await response.text();

        // Find JSON content in multipart response
        const jsonMatch = text.match(/\{[\s\S]*"resourceType"[\s\S]*}/);
        if (jsonMatch) {
            return jsonMatch[0];
        }

        // Try to find content after Content-Type: application/json or application/fhir+json
        const parts = text.split(/--[^\r\n]+/);
        for (const part of parts) {
            if (part.includes('application/json') || part.includes('application/fhir+json')) {
                const jsonStart = part.indexOf('{');
                const jsonEnd = part.lastIndexOf('}');
                if (jsonStart !== -1 && jsonEnd !== -1) {
                    return part.substring(jsonStart, jsonEnd + 1);
                }
            }
        }

        throw new Error('Could not extract JSON from multipart response');
    }

    function parseAndLoadManifest(text, source) {
        try {
            const manifest = JSON.parse(text);

            if (manifest.resourceType !== 'Bundle') {
                throw new Error('Not a valid FHIR Bundle');
            }

            state.manifest = manifest;
            state.studyData = extractStudyData(manifest);

            displayManifestInfo();
            buildStudyTree();
            showSuccess(`Manifest loaded successfully from ${source}`);

            // Show panels
            elements.manifestInfoPanel.style.display = 'block';
            elements.downloadSettingsPanel.style.display = 'block';
            elements.downloadActions.style.display = 'flex';

        } catch (error) {
            showError(`Failed to parse manifest: ${error.message}`);
        }

        hideLoading();
    }

    // ==============================
    // Data Extraction
    // ==============================

    function extractStudyData(bundle) {
        const data = {
            patient: null,
            composition: null,
            imagingStudy: null,
            endpoints: [],
            keyImages: [],
            series: [],
            instances: []
        };

        if (!bundle.entry) return data;

        // Extract resources by type
        for (const entry of bundle.entry) {
            const resource = entry.resource;
            if (!resource) continue;

            switch (resource.resourceType) {
                case 'Patient':
                    data.patient = resource;
                    break;
                case 'Composition':
                    data.composition = resource;
                    break;
                case 'ImagingStudy':
                    data.imagingStudy = resource;
                    break;
                case 'Endpoint':
                    data.endpoints.push(resource);
                    break;
                case 'ImagingSelection':
                    // These might be key images
                    data.keyImages.push(resource);
                    break;
            }
        }

        // Extract series and instances from ImagingStudy
        if (data.imagingStudy && data.imagingStudy.series) {
            for (const series of data.imagingStudy.series) {
                const seriesData = {
                    uid: series.uid,
                    number: series.number,
                    modality: series.modality?.coding?.[0]?.code || 'OT',
                    description: series.description || 'Unnamed Series',
                    bodyPart: series.bodySite?.concept?.coding?.[0]?.display || '',
                    instances: []
                };

                if (series.instance) {
                    for (const instance of series.instance) {
                        const instanceData = {
                            uid: instance.uid,
                            number: instance.number,
                            sopClass: instance.sopClass?.code || '',
                            description: instance.extension?.find(
                                e => e.url?.includes('instance-description')
                            )?.valueString || `Instance ${instance.number || ''}`,
                            isKeyImage: false
                        };

                        // Check if this instance is a key image
                        for (const ki of data.keyImages) {
                            if (ki.instance) {
                                for (const kiInstance of ki.instance) {
                                    if (kiInstance.uid === instance.uid) {
                                        instanceData.isKeyImage = true;
                                        instanceData.keyImageTitle = ki.extension?.find(
                                            e => e.url?.includes('description')
                                        )?.valueString || 'Key Image';
                                        break;
                                    }
                                }
                            }
                        }

                        seriesData.instances.push(instanceData);
                    }
                }

                data.series.push(seriesData);
            }
        }

        return data;
    }

    function getWadoRsEndpoint() {
        // Look for WADO-RS endpoint
        for (const endpoint of state.studyData.endpoints) {
            const connectionType = endpoint.connectionType?.[0]?.coding?.[0]?.code;
            if (connectionType === 'dicom-wado-rs' ||
                endpoint.address?.includes('wado')) {
                return endpoint.address;
            }
        }

        // Fallback: try to find any endpoint
        if (state.studyData.endpoints.length > 0) {
            return state.studyData.endpoints[0].address;
        }

        return null;
    }

    function getStudyInstanceUid() {
        const imagingStudy = state.studyData.imagingStudy;
        if (!imagingStudy) return null;

        // Try to get from identifier
        const uidIdentifier = imagingStudy.identifier?.find(
            id => id.system === 'urn:dicom:uid'
        );

        if (uidIdentifier) {
            return uidIdentifier.value.replace('ihe:urn:oid:', '').replace('urn:oid:', '');
        }

        return null;
    }

    // ==============================
    // Display Functions
    // ==============================

    function displayManifestInfo() {
        const data = state.studyData;
        const imagingStudy = data.imagingStudy;

        let infoHtml = '';

        // Patient
        if (data.patient) {
            const patientName = data.patient.name?.[0];
            const fullName = patientName ?
                `${patientName.family || ''}, ${(patientName.given || []).join(' ')}` : 'Unknown';
            const patientId = data.patient.identifier?.[0]?.value || 'Unknown';

            infoHtml += `
                <div class="info-item">
                    <span class="info-label">Patient Name</span>
                    <span class="info-value">${escapeHtml(fullName)}</span>
                </div>
                <div class="info-item">
                    <span class="info-label">Patient ID</span>
                    <span class="info-value">${escapeHtml(patientId)}</span>
                </div>
            `;
        }

        // Study
        if (imagingStudy) {
            const studyUid = getStudyInstanceUid() || 'Unknown';
            const description = imagingStudy.description || 'No Description';
            const started = imagingStudy.started ?
                new Date(imagingStudy.started).toLocaleString() : 'Unknown';

            infoHtml += `
                <div class="info-item">
                    <span class="info-label">Study Description</span>
                    <span class="info-value highlight">${escapeHtml(description)}</span>
                </div>
                <div class="info-item">
                    <span class="info-label">Study Date</span>
                    <span class="info-value">${escapeHtml(started)}</span>
                </div>
                <div class="info-item">
                    <span class="info-label">Study UID</span>
                    <span class="info-value" style="font-size: 10px;">${escapeHtml(studyUid)}</span>
                </div>
            `;
        }

        // Statistics
        let totalInstances = 0;
        let totalKeyImages = 0;

        for (const series of data.series) {
            totalInstances += series.instances.length;
            totalKeyImages += series.instances.filter(i => i.isKeyImage).length;
        }

        infoHtml += `
            <div class="info-item">
                <span class="info-label">Series / Instances</span>
                <span class="info-value">${data.series.length} series / ${totalInstances} instances</span>
            </div>
        `;

        if (totalKeyImages > 0) {
            infoHtml += `
                <div class="info-item">
                    <span class="info-label">Key Images</span>
                    <span class="info-value highlight">⭐ ${totalKeyImages} key images</span>
                </div>
            `;
        }

        // Endpoint
        const wadoEndpoint = getWadoRsEndpoint();
        if (wadoEndpoint) {
            infoHtml += `
                <div class="info-item">
                    <span class="info-label">WADO-RS Endpoint</span>
                    <span class="info-value" style="font-size: 10px;">${escapeHtml(wadoEndpoint)}</span>
                </div>
            `;
        }

        elements.manifestInfoGrid.innerHTML = infoHtml;
    }

    function buildStudyTree() {
        const data = state.studyData;
        state.selectedItems.clear();

        let treeHtml = '';

        // Key Images Section (if any)
        let keyImages = [];
        for (const series of data.series) {
            for (const instance of series.instances) {
                if (instance.isKeyImage) {
                    keyImages.push({ series, instance });
                }
            }
        }

        if (keyImages.length > 0) {
            treeHtml += `
                <div class="key-images-section">
                    <div class="key-images-header">
                        <span class="key-images-icon">⭐</span>
                        <span class="key-images-title">Key Images</span>
                        <span class="key-images-count">${keyImages.length}</span>
                    </div>
                    <div class="key-images-content">
            `;

            for (const { series, instance } of keyImages) {
                const itemId = `ki_${series.uid}_${instance.uid}`;
                treeHtml += buildInstanceNode(instance, series, itemId, true);
            }

            treeHtml += `
                    </div>
                </div>
            `;
        }

        // Study Node
        const studyDescription = data.imagingStudy?.description || 'Imaging Study';
        const studyUid = getStudyInstanceUid() || '';

        treeHtml += `
            <div class="tree-node study" data-uid="${escapeHtml(studyUid)}">
                <div class="tree-node-header" onclick="DicomDownloader.toggleNode(this.parentElement)">
                    <button class="tree-expand-btn expanded" onclick="event.stopPropagation()">▶</button>
                    <input type="checkbox" class="tree-checkbox" 
                           onchange="DicomDownloader.toggleStudySelection(this, '${escapeHtml(studyUid)}')" 
                           onclick="event.stopPropagation()">
                    <span class="tree-icon">🏥</span>
                    <div class="tree-label">
                        <div class="tree-title">${escapeHtml(studyDescription)}</div>
                        <div class="tree-subtitle">${data.series.length} series, ${getTotalInstanceCount()} instances</div>
                    </div>
                </div>
                <div class="tree-children expanded">
        `;

        // Series Nodes
        for (const series of data.series) {
            const keyImagesInSeries = series.instances.filter(i => i.isKeyImage).length;

            treeHtml += `
                <div class="tree-node series" data-uid="${escapeHtml(series.uid)}">
                    <div class="tree-node-header" onclick="DicomDownloader.toggleNode(this.parentElement)">
                        <button class="tree-expand-btn ${series.instances.length > 0 ? '' : 'no-children'}" onclick="event.stopPropagation()">▶</button>
                        <input type="checkbox" class="tree-checkbox" 
                               onchange="DicomDownloader.toggleSeriesSelection(this, '${escapeHtml(series.uid)}')" 
                               onclick="event.stopPropagation()">
                        <span class="tree-icon">📁</span>
                        <div class="tree-label">
                            <div class="tree-title">${escapeHtml(series.description)}</div>
                            <div class="tree-subtitle">${series.instances.length} instances${series.bodyPart ? ` • ${series.bodyPart}` : ''}</div>
                        </div>
                        <span class="tree-badge series">${series.modality}</span>
                        ${keyImagesInSeries > 0 ? `<span class="tree-badge key-image">⭐ ${keyImagesInSeries}</span>` : ''}
                    </div>
                    <div class="tree-children">
            `;

            // Instance Nodes
            for (const instance of series.instances) {
                const itemId = `${series.uid}_${instance.uid}`;
                treeHtml += buildInstanceNode(instance, series, itemId, false);
            }

            treeHtml += `
                    </div>
                </div>
            `;
        }

        treeHtml += `
                </div>
            </div>
        `;

        elements.treeContainer.innerHTML = treeHtml;
        updateSelectionInfo();
    }

    function buildInstanceNode(instance, series, itemId, isKeyImageSection) {
        const sopClassName = getSopClassName(instance.sopClass);

        return `
            <div class="tree-node instance ${instance.isKeyImage ? 'key-image' : ''}" 
                 data-uid="${escapeHtml(instance.uid)}" 
                 data-series-uid="${escapeHtml(series.uid)}"
                 data-item-id="${escapeHtml(itemId)}">
                <div class="tree-node-header ${instance.isKeyImage ? 'key-image' : ''}" 
                     onclick="DicomDownloader.toggleInstanceSelection('${escapeHtml(itemId)}', '${escapeHtml(series.uid)}', '${escapeHtml(instance.uid)}')">
                    <button class="tree-expand-btn no-children">▶</button>
                    <input type="checkbox" class="tree-checkbox" 
                           id="cb_${escapeHtml(itemId)}"
                           onchange="DicomDownloader.toggleInstanceSelection('${escapeHtml(itemId)}', '${escapeHtml(series.uid)}', '${escapeHtml(instance.uid)}')" 
                           onclick="event.stopPropagation()">
                    <span class="tree-icon">${instance.isKeyImage ? '⭐' : '🖼️'}</span>
                    <div class="tree-label">
                        <div class="tree-title">${escapeHtml(instance.description || `Instance ${instance.number || ''}`)}</div>
                        <div class="tree-subtitle">${sopClassName}</div>
                    </div>
                    ${instance.isKeyImage && !isKeyImageSection ? '<span class="tree-badge key-image">Key</span>' : ''}
                </div>
            </div>
        `;
    }

    function getSopClassName(sopClass) {
        const sopClassMap = {
            'ihe:urn:oid:1.2.840.10008.5.1.4.1.1.1': 'CR Image',
            'ihe:urn:oid:1.2.840.10008.5.1.4.1.1.1.1': 'Digital X-Ray',
            'ihe:urn:oid:1.2.840.10008.5.1.4.1.1.2': 'CT Image',
            'ihe:urn:oid:1.2.840.10008.5.1.4.1.1.4': 'MR Image',
            'ihe:urn:oid:1.2.840.10008.5.1.4.1.1.4.3': 'Enhanced MR Image',
            'ihe:urn:oid:1.2.840.10008.5.1.4.1.1.6.1': 'US Image',
            'ihe:urn:oid:1.2.840.10008.5.1.4.1.1.7': 'Secondary Capture',
            'ihe:urn:oid:1.2.840.10008.5.1.4.1.1.88.59': 'Key Object Selection',
            'ihe:urn:oid:1.2.840.10008.5.1.4.1.1.88.11': 'Basic Text SR',
            'ihe:urn:oid:1.2.840.10008.5.1.4.1.1.88.22': 'Enhanced SR'
        };

        // Clean up the SOP class string
        const cleanSop = (sopClass || '').replace('urn:ietf:rfc:3986', '').trim();

        return sopClassMap[cleanSop] || 'DICOM Object';
    }

    function getTotalInstanceCount() {
        let count = 0;
        for (const series of state.studyData.series) {
            count += series.instances.length;
        }
        return count;
    }

    // ==============================
    // Selection Functions
    // ==============================

    function toggleNode(nodeElement) {
        const children = nodeElement.querySelector('.tree-children');
        const expandBtn = nodeElement.querySelector('.tree-expand-btn');

        if (children && expandBtn) {
            children.classList.toggle('expanded');
            expandBtn.classList.toggle('expanded');
        }
    }

    function toggleStudySelection(checkbox, studyUid) {
        const isChecked = checkbox.checked;

        // Select/deselect all series and instances
        for (const series of state.studyData.series) {
            for (const instance of series.instances) {
                const itemId = `${series.uid}_${instance.uid}`;
                if (isChecked) {
                    state.selectedItems.add(itemId);
                } else {
                    state.selectedItems.delete(itemId);
                }
            }
        }

        // Update all checkboxes
        document.querySelectorAll('.tree-node.series .tree-checkbox, .tree-node.instance .tree-checkbox').forEach(cb => {
            cb.checked = isChecked;
        });

        updateSelectionInfo();
    }

    function toggleSeriesSelection(checkbox, seriesUid) {
        const isChecked = checkbox.checked;
        const series = state.studyData.series.find(s => s.uid === seriesUid);

        if (series) {
            for (const instance of series.instances) {
                const itemId = `${seriesUid}_${instance.uid}`;
                if (isChecked) {
                    state.selectedItems.add(itemId);
                } else {
                    state.selectedItems.delete(itemId);
                }

                // Update instance checkbox
                const cb = document.getElementById(`cb_${itemId}`);
                if (cb) cb.checked = isChecked;
            }
        }

        updateParentCheckboxes();
        updateSelectionInfo();
    }

    function toggleInstanceSelection(itemId, seriesUid, instanceUid) {
        const cb = document.getElementById(`cb_${itemId}`);

        if (cb) {
            // If called from header click, toggle the checkbox
            if (!event || event.target !== cb) {
                cb.checked = !cb.checked;
            }

            if (cb.checked) {
                state.selectedItems.add(itemId);
            } else {
                state.selectedItems.delete(itemId);
            }
        }

        updateParentCheckboxes();
        updateSelectionInfo();
    }

    function updateParentCheckboxes() {
        // Update series checkboxes
        for (const series of state.studyData.series) {
            const seriesNode = document.querySelector(`.tree-node.series[data-uid="${series.uid}"]`);
            if (seriesNode) {
                const cb = seriesNode.querySelector(':scope > .tree-node-header > .tree-checkbox');
                if (cb) {
                    const selectedInSeries = series.instances.filter(i =>
                        state.selectedItems.has(`${series.uid}_${i.uid}`)
                    ).length;

                    cb.checked = selectedInSeries === series.instances.length && series.instances.length > 0;
                    cb.indeterminate = selectedInSeries > 0 && selectedInSeries < series.instances.length;
                }
            }
        }

        // Update study checkbox
        const studyNode = document.querySelector('.tree-node.study');
        if (studyNode) {
            const cb = studyNode.querySelector(':scope > .tree-node-header > .tree-checkbox');
            if (cb) {
                const totalInstances = getTotalInstanceCount();
                const selectedCount = state.selectedItems.size;

                cb.checked = selectedCount === totalInstances && totalInstances > 0;
                cb.indeterminate = selectedCount > 0 && selectedCount < totalInstances;
            }
        }
    }

    function selectAll() {
        for (const series of state.studyData.series) {
            for (const instance of series.instances) {
                const itemId = `${series.uid}_${instance.uid}`;
                state.selectedItems.add(itemId);
            }
        }

        document.querySelectorAll('.tree-checkbox').forEach(cb => {
            cb.checked = true;
            cb.indeterminate = false;
        });

        updateSelectionInfo();
    }

    function deselectAll() {
        state.selectedItems.clear();

        document.querySelectorAll('.tree-checkbox').forEach(cb => {
            cb.checked = false;
            cb.indeterminate = false;
        });

        updateSelectionInfo();
    }

    function expandAll() {
        document.querySelectorAll('.tree-children').forEach(c => c.classList.add('expanded'));
        document.querySelectorAll('.tree-expand-btn').forEach(b => b.classList.add('expanded'));
    }

    function collapseAll() {
        document.querySelectorAll('.tree-children').forEach(c => c.classList.remove('expanded'));
        document.querySelectorAll('.tree-expand-btn').forEach(b => b.classList.remove('expanded'));
    }

    function updateSelectionInfo() {
        const count = state.selectedItems.size;
        elements.selectionBadge.textContent = `${count} item${count !== 1 ? 's' : ''} selected`;

        // Estimate size (rough estimate: 500KB per instance)
        const estimatedMB = (count * 0.5).toFixed(1);
        elements.downloadSummary.textContent = `${count} images selected (≈ ${estimatedMB} MB estimated)`;
    }

    // ==============================
    // Download Functions
    // ==============================

    async function startDownload() {
        if (state.selectedItems.size === 0) {
            showError('Please select at least one image to download');
            return;
        }

        const wadoEndpoint = getWadoRsEndpoint();
        if (!wadoEndpoint) {
            showError('No WADO-RS endpoint found in the manifest');
            return;
        }

        const studyUid = getStudyInstanceUid();
        if (!studyUid) {
            showError('Could not determine Study Instance UID');
            return;
        }

        // Prepare download list
        const downloadList = [];

        for (const itemId of state.selectedItems) {
            // itemId format: seriesUid_instanceUid
            const parts = itemId.split('_');
            if (parts.length >= 2) {
                const seriesUid = parts[0];
                const instanceUid = parts.slice(1).join('_'); // In case UID contains underscore

                const series = state.studyData.series.find(s => s.uid === seriesUid);
                const instance = series?.instances.find(i => i.uid === instanceUid);

                if (series && instance) {
                    downloadList.push({
                        seriesUid,
                        instanceUid,
                        seriesDescription: series.description,
                        instanceNumber: instance.number,
                        isKeyImage: instance.isKeyImage
                    });
                }
            }
        }

        if (downloadList.length === 0) {
            showError('No valid items to download');
            return;
        }

        // Sort: key images first if option is enabled
        if (elements.downloadKeyImages.checked) {
            downloadList.sort((a, b) => {
                if (a.isKeyImage && !b.isKeyImage) return -1;
                if (!a.isKeyImage && b.isKeyImage) return 1;
                return 0;
            });
        }

        // Initialize download state
        state.isDownloading = true;
        state.downloadController = new AbortController();
        state.downloadStats = {
            total: downloadList.length,
            completed: 0,
            failed: 0,
            totalBytes: 0,
            startTime: Date.now()
        };

        // Show progress panel
        elements.progressPanel.style.display = 'block';
        elements.downloadActions.style.display = 'none';
        clearLog();
        updateProgressUI();

        try {
            // Create a zip file using JSZip (we'll need to use a different approach for client-side)
            // Instead, we'll use the File System Access API where available, or download individual files

            const folderName = elements.downloadFolder.value.trim() || 'DICOM_Export';
            const fileNaming = elements.fileNaming.value;

            // Check if File System Access API is available
            if ('showDirectoryPicker' in window) {
                await downloadToFolder(wadoEndpoint, studyUid, downloadList, folderName, fileNaming);
            } else {
                // Fallback: download as individual files or zip
                await downloadAsZip(wadoEndpoint, studyUid, downloadList, folderName, fileNaming);
            }

        } catch (error) {
            if (error.name !== 'AbortError') {
                logEntry(`Download failed: ${error.message}`, 'error');
                showError(`Download failed: ${error.message}`);
            }
        } finally {
            state.isDownloading = false;
            state.downloadController = null;
            elements.downloadActions.style.display = 'flex';
        }
    }

    async function downloadToFolder(wadoEndpoint, studyUid, downloadList, folderName, fileNaming) {
        let dirHandle;

        try {
            // Request folder access
            logEntry('Requesting folder access...', 'info');
            dirHandle = await window.showDirectoryPicker({
                mode: 'readwrite',
                startIn: 'downloads'
            });
        } catch (error) {
            if (error.name === 'AbortError') {
                logEntry('Folder selection cancelled', 'warning');
                return;
            }
            throw error;
        }

        // Create export folder
        let exportDir;
        try {
            exportDir = await dirHandle.getDirectoryHandle(folderName, { create: true });
        } catch (error) {
            exportDir = dirHandle;
        }

        logEntry(`Downloading to folder: ${folderName}`, 'info');

        // Group by series for folder structure
        const seriesGroups = {};
        for (const item of downloadList) {
            if (!seriesGroups[item.seriesUid]) {
                seriesGroups[item.seriesUid] = {
                    description: item.seriesDescription,
                    items: []
                };
            }
            seriesGroups[item.seriesUid].items.push(item);
        }

        // Download each series
        for (const [seriesUid, seriesData] of Object.entries(seriesGroups)) {
            // Create series folder
            const seriesFolderName = sanitizeFolderName(seriesData.description || seriesUid.slice(-8));
            let seriesDir;

            try {
                seriesDir = await exportDir.getDirectoryHandle(seriesFolderName, { create: true });
            } catch (error) {
                seriesDir = exportDir;
            }

            // Download instances
            for (let i = 0; i < seriesData.items.length; i++) {
                if (state.downloadController?.signal.aborted) {
                    throw new DOMException('Download cancelled', 'AbortError');
                }

                const item = seriesData.items[i];
                const filename = generateFilename(item, i + 1, fileNaming);

                try {
                    await downloadInstance(
                        wadoEndpoint,
                        studyUid,
                        item.seriesUid,
                        item.instanceUid,
                        seriesDir,
                        filename
                    );

                    state.downloadStats.completed++;
                    logEntry(`✓ Downloaded: ${filename}`, 'success');
                } catch (error) {
                    state.downloadStats.failed++;
                    logEntry(`✗ Failed: ${filename} - ${error.message}`, 'error');
                }

                updateProgressUI();
            }
        }

        // Include manifest if requested
        if (elements.createManifest.checked && state.manifest) {
            try {
                const manifestFile = await exportDir.getFileHandle('manifest.json', { create: true });
                const writable = await manifestFile.createWritable();
                await writable.write(JSON.stringify(state.manifest, null, 2));
                await writable.close();
                logEntry('✓ Saved manifest.json', 'success');
            } catch (error) {
                logEntry(`✗ Failed to save manifest: ${error.message}`, 'error');
            }
        }

        // Complete
        const duration = ((Date.now() - state.downloadStats.startTime) / 1000).toFixed(1);
        logEntry(`Download complete! ${state.downloadStats.completed}/${state.downloadStats.total} files in ${duration}s`, 'success');

        if (state.downloadStats.failed === 0) {
            showSuccess(`Successfully downloaded ${state.downloadStats.completed} files to ${folderName}`);
        } else {
            showError(`Downloaded ${state.downloadStats.completed} files, ${state.downloadStats.failed} failed`);
        }
    }

    async function downloadAsZip(wadoEndpoint, studyUid, downloadList, folderName, fileNaming) {
        // For browsers without File System Access API, we'll download files individually
        logEntry('File System Access API not available. Downloading files individually...', 'warning');

        // Group by series
        const seriesGroups = {};
        for (const item of downloadList) {
            if (!seriesGroups[item.seriesUid]) {
                seriesGroups[item.seriesUid] = {
                    description: item.seriesDescription,
                    items: []
                };
            }
            seriesGroups[item.seriesUid].items.push(item);
        }

        // Download each file
        for (const [seriesUid, seriesData] of Object.entries(seriesGroups)) {
            for (let i = 0; i < seriesData.items.length; i++) {
                if (state.downloadController?.signal.aborted) {
                    throw new DOMException('Download cancelled', 'AbortError');
                }

                const item = seriesData.items[i];
                const filename = `${sanitizeFolderName(seriesData.description)}_${generateFilename(item, i + 1, fileNaming)}`;

                try {
                    const data = await fetchInstance(
                        wadoEndpoint,
                        studyUid,
                        item.seriesUid,
                        item.instanceUid
                    );

                    // Trigger download
                    downloadBlob(data, filename);

                    state.downloadStats.completed++;
                    state.downloadStats.totalBytes += data.size;
                    logEntry(`✓ Downloaded: ${filename}`, 'success');
                } catch (error) {
                    state.downloadStats.failed++;
                    logEntry(`✗ Failed: ${filename} - ${error.message}`, 'error');
                }

                updateProgressUI();

                // Small delay to prevent overwhelming the browser
                await new Promise(resolve => setTimeout(resolve, 100));
            }
        }

        // Complete
        const duration = ((Date.now() - state.downloadStats.startTime) / 1000).toFixed(1);
        logEntry(`Download complete! ${state.downloadStats.completed}/${state.downloadStats.total} files in ${duration}s`, 'success');
    }

    async function downloadInstance(wadoEndpoint, studyUid, seriesUid, instanceUid, dirHandle, filename) {
        const data = await fetchInstance(wadoEndpoint, studyUid, seriesUid, instanceUid);

        // Write to file
        const fileHandle = await dirHandle.getFileHandle(filename, { create: true });
        const writable = await fileHandle.createWritable();
        await writable.write(data);
        await writable.close();

        state.downloadStats.totalBytes += data.size;
    }

    async function fetchInstance(wadoEndpoint, studyUid, seriesUid, instanceUid) {
        // Build WADO-RS URL for instance retrieval
        const url = `${wadoEndpoint}/studies/${studyUid}/series/${seriesUid}/instances/${instanceUid}`;

        elements.progressCurrent.textContent = `Fetching: ${instanceUid.slice(-12)}...`;

        const response = await fetch(url, {
            headers: {
                'Accept': 'multipart/related; type="application/dicom"'
            },
            signal: state.downloadController?.signal
        });

        if (!response.ok) {
            throw new Error(`HTTP ${response.status}`);
        }

        // Handle multipart response
        const contentType = response.headers.get('content-type');
        const arrayBuffer = await response.arrayBuffer();

        if (contentType && contentType.includes('multipart')) {
            // Extract DICOM data from multipart response
            return extractDicomFromMultipart(arrayBuffer, contentType);
        }

        return new Blob([arrayBuffer], { type: 'application/dicom' });
    }

    function extractDicomFromMultipart(arrayBuffer, contentType) {
        // Extract boundary from content type
        const boundaryMatch = contentType.match(/boundary=([^;]+)/);
        if (!boundaryMatch) {
            // No boundary, assume single part
            return new Blob([arrayBuffer], { type: 'application/dicom' });
        }

        const boundary = boundaryMatch[1].replace(/"/g, '');
        const uint8Array = new Uint8Array(arrayBuffer);

        // Convert to string to find boundaries (this is a simplified approach)
        // For binary data, we need to find the actual content after headers

        // Find the DICOM magic number "DICM" at offset 128
        // Or find content after the multipart headers

        // Look for double CRLF (end of headers) followed by content
        const headerEnd = findHeaderEnd(uint8Array);

        if (headerEnd > 0) {
            // Find the next boundary
            const boundaryBytes = new TextEncoder().encode('--' + boundary);
            let endOfContent = arrayBuffer.byteLength;

            for (let i = headerEnd; i < uint8Array.length - boundaryBytes.length; i++) {
                let match = true;
                for (let j = 0; j < boundaryBytes.length; j++) {
                    if (uint8Array[i + j] !== boundaryBytes[j]) {
                        match = false;
                        break;
                    }
                }
                if (match) {
                    endOfContent = i - 2; // Exclude trailing CRLF
                    break;
                }
            }

            const dicomData = arrayBuffer.slice(headerEnd, endOfContent);
            return new Blob([dicomData], { type: 'application/dicom' });
        }

        // Fallback: return entire buffer
        return new Blob([arrayBuffer], { type: 'application/dicom' });
    }

    function findHeaderEnd(uint8Array) {
        // Look for \r\n\r\n (CRLF CRLF)
        for (let i = 0; i < uint8Array.length - 4; i++) {
            if (uint8Array[i] === 0x0D &&
                uint8Array[i + 1] === 0x0A &&
                uint8Array[i + 2] === 0x0D &&
                uint8Array[i + 3] === 0x0A) {
                return i + 4;
            }
        }
        // Also try \n\n
        for (let i = 0; i < uint8Array.length - 2; i++) {
            if (uint8Array[i] === 0x0A && uint8Array[i + 1] === 0x0A) {
                return i + 2;
            }
        }
        return -1;
    }

    function downloadBlob(blob, filename) {
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = filename;
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        URL.revokeObjectURL(url);
    }

    function generateFilename(item, index, naming) {
        switch (naming) {
            case 'numbered':
                return `${String(index).padStart(4, '0')}.dcm`;
            case 'combined':
                const seriesNum = item.seriesDescription?.match(/\d+/)?.[0] || '01';
                return `S${seriesNum}_I${String(index).padStart(4, '0')}.dcm`;
            case 'sopuid':
            default:
                return `${item.instanceUid}.dcm`;
        }
    }

    function sanitizeFolderName(name) {
        return (name || 'unnamed')
            .replace(/[<>:"/\\|?*]/g, '_')
            .replace(/\s+/g, '_')
            .substring(0, 50);
    }

    function cancelDownload() {
        if (state.downloadController) {
            state.downloadController.abort();
            logEntry('Download cancelled by user', 'warning');
        }
    }

    function updateProgressUI() {
        const stats = state.downloadStats;
        const percent = stats.total > 0 ? Math.round((stats.completed / stats.total) * 100) : 0;

        elements.progressPercent.textContent = `${percent}%`;
        elements.progressCount.textContent = `${stats.completed} / ${stats.total}`;
        elements.progressSize.textContent = formatBytes(stats.totalBytes);
        elements.progressBar.style.width = `${percent}%`;

        // Calculate speed
        if (stats.startTime && stats.totalBytes > 0) {
            const elapsedSeconds = (Date.now() - stats.startTime) / 1000;
            const bytesPerSecond = stats.totalBytes / elapsedSeconds;
            elements.progressSpeed.textContent = `${formatBytes(bytesPerSecond)}/s`;
        }
    }

    function formatBytes(bytes) {
        if (bytes === 0) return '0 B';
        const k = 1024;
        const sizes = ['B', 'KB', 'MB', 'GB'];
        const i = Math.floor(Math.log(bytes) / Math.log(k));
        return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
    }

    // ==============================
    // Logging Functions
    // ==============================

    function logEntry(message, type = 'info') {
        const timestamp = new Date().toLocaleTimeString();
        const entry = document.createElement('div');
        entry.className = `log-entry ${type}`;
        entry.innerHTML = `<span class="log-time">[${timestamp}]</span> ${escapeHtml(message)}`;
        elements.progressLog.appendChild(entry);
        elements.progressLog.scrollTop = elements.progressLog.scrollHeight;
    }

    function clearLog() {
        elements.progressLog.innerHTML = '';
    }

    // ==============================
    // UI Helper Functions
    // ==============================

    function showLoading() {
        elements.treeContainer.innerHTML = `
            <div class="loading-overlay">
                <div class="loading-spinner">
                    <div class="spinner"></div>
                    <p style="color: var(--text-secondary); font-weight: 500;">Loading manifest...</p>
                </div>
            </div>
        `;
    }

    function hideLoading() {
        // Tree will be rebuilt on success
    }

    function showError(message) {
        elements.errorBanner.textContent = `⚠️ ${message}`;
        elements.errorBanner.style.display = 'block';
        elements.successBanner.style.display = 'none';
    }

    function hideError() {
        elements.errorBanner.style.display = 'none';
    }

    function showSuccess(message) {
        elements.successBanner.innerHTML = `✅ ${escapeHtml(message)}`;
        elements.successBanner.style.display = 'flex';
        elements.errorBanner.style.display = 'none';
    }

    function showToast(message, type = 'info') {
        const container = document.getElementById('toastContainer');
        if (!container) return;

        const toast = document.createElement('div');
        toast.className = `toast ${type}`;

        const icons = {
            success: '✅',
            error: '❌',
            info: 'ℹ️',
            warning: '⚠️'
        };

        toast.innerHTML = `
            <span>${icons[type] || 'ℹ️'}</span>
            <span>${escapeHtml(message)}</span>
            <button class="toast-close" onclick="this.parentElement.remove()">&times;</button>
        `;

        container.appendChild(toast);

        setTimeout(() => {
            toast.style.animation = 'fadeOut 0.3s ease-out';
            setTimeout(() => toast.remove(), 300);
        }, 4000);
    }

    function escapeHtml(text) {
        if (text === null || text === undefined) return '';
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    // ==============================
    // Public API
    // ==============================

    return {
        init,
        toggleNode,
        toggleStudySelection,
        toggleSeriesSelection,
        toggleInstanceSelection
    };
})();

// Initialize on load
document.addEventListener('DOMContentLoaded', DicomDownloader.init);

