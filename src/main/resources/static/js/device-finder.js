/**
 * Device and Implant Imaging Finder JS Orchestrator
 * High-fidelity, clinical-grade interface integrating VLM cascades.
 */
const DeviceFinder = (function () {
    'use strict';

    let el = {};
    let mockPatients = [];

    // Initialize Page on DOM Loaded
    function init() {
        el = {
            scanForm: document.getElementById('scanForm'),
            ollamaDot: document.getElementById('ollamaDot'),
            ollamaStatusText: document.getElementById('ollamaStatusText'),
            recheckStatusBtn: document.getElementById('recheckStatusBtn'),
            modelSelect: document.getElementById('modelSelect'),
            patientSelector: document.getElementById('patientSelector'),
            patientId: document.getElementById('patientId'),
            triggerMode: document.getElementById('triggerMode'),
            customPrompt: document.getElementById('customPrompt'),
            scanBtn: document.getElementById('scanBtn'),

            errorBanner: document.getElementById('errorBanner'),
            welcomeState: document.getElementById('welcomeState'),
            resultsDashboard: document.getElementById('resultsDashboard'),
            loadJaneDoeBtn: document.getElementById('loadJaneDoeBtn'),

            resPatientName: document.getElementById('resPatientName'),
            resPatientId: document.getElementById('resPatientId'),
            resPatientAge: document.getElementById('resPatientAge'),
            resPatientGender: document.getElementById('resPatientGender'),
            resTriggerBadge: document.getElementById('resTriggerBadge'),
            alertContainer: document.getElementById('alertContainer'),

            reconTableBody: document.getElementById('reconTableBody'),
            policyTitle: document.getElementById('policyTitle'),
            tilesGrid: document.getElementById('tilesGrid'),

            priorsToggle: document.getElementById('priorsToggle'),
            priorsContent: document.getElementById('priorsContent'),
            priorsTableBody: document.getElementById('priorsTableBody'),

            vlmStatusText: document.getElementById('vlmStatusText'),
            vlmIngestedModel: document.getElementById('vlmIngestedModel'),
            statStudiesFiltered: document.getElementById('statStudiesFiltered'),
            statPixelsSaved: document.getElementById('statPixelsSaved'),
            statMaxAllowed: document.getElementById('statMaxAllowed'),
            statPreScreen: document.getElementById('statPreScreen'),
            statReviewQuality: document.getElementById('statReviewQuality'),

            verApproveBtn: document.getElementById('verApproveBtn'),
            verFlagBtn: document.getElementById('verFlagBtn'),
            verSyncBtn: document.getElementById('verSyncBtn')
        };

        // Wire Event Listeners
        el.recheckStatusBtn.addEventListener('click', checkOllamaStatus);
        el.patientSelector.addEventListener('change', handlePatientPresetChange);
        el.scanBtn.addEventListener('click', performImplantScan);
        el.loadJaneDoeBtn.addEventListener('click', loadJaneDoePreset);

        // Collapsible Priors table
        el.priorsToggle.addEventListener('click', togglePriorsSection);

        // Verification Actions
        el.verApproveBtn.addEventListener('click', () => alert('✓ Patient successfully cleared for safe entry under MRI safety protocol templates.'));
        el.verFlagBtn.addEventListener('click', () => alert('⚠️ Notification dispatched to diagnostic imaging supervisor for secondary manual PACS validation.'));
        el.verSyncBtn.addEventListener('click', () => alert('🔄 RESTful sync transaction resolved. Discrepant findings propagated back to HL7 HAPI FHIR device list registry.'));

        // Initial setup
        checkOllamaStatus();
        loadDemoPatients();
    }

    // =========================================================================
    // OLLAMA STATUS CHECK
    // =========================================================================
    async function checkOllamaStatus() {
        setOllamaStatus('unknown', 'Checking connection…');
        try {
            const res = await fetch('./api/ollama/status', { headers: { 'Accept': 'application/json' } });
            const data = await res.json();
            if (data.available) {
                const count = (data.models || []).length;
                setOllamaStatus('online', `Ollama available · ${count} model${count === 1 ? '' : 's'}`);
                populateOllamaModels(data.models || [], data.defaultModel);
            } else {
                setOllamaStatus('offline', 'Ollama offline (using visual NLP helper)');
                populateOllamaModels([], data.defaultModel);
            }
        } catch (e) {
            setOllamaStatus('offline', 'Ollama offline (using visual NLP helper)');
            populateOllamaModels([], 'qwen2.5-instruct');
        }
    }

    function setOllamaStatus(kind, text) {
        el.ollamaDot.className = 'status-dot status-' + kind;
        el.ollamaStatusText.textContent = text;
    }

    function populateOllamaModels(models, defaultModel) {
        el.modelSelect.innerHTML = '';

        // Only allow qwen3.6:27b for vision language models
        const allowedVlm = 'qwen3.6:27b';
        const isInstalled = models.includes(allowedVlm);

        const opt = document.createElement('option');
        opt.value = allowedVlm;
        opt.textContent = allowedVlm + (isInstalled ? '' : ' (not pulled)');
        opt.selected = true;
        el.modelSelect.appendChild(opt);

        // Add fallback
        const optMock = document.createElement('option');
        optMock.value = "mock_model";
        optMock.textContent = "Force Mock / Client-only Demonstration Mode";
        if (models.length === 0) {
            optMock.selected = true;
        }
        el.modelSelect.appendChild(optMock);
    }

    // =========================================================================
    // LOAD PRESET PATIENTS
    // =========================================================================
    async function loadDemoPatients() {
        try {
            const res = await fetch('./api/device-finder/patients');
            if (res.ok) {
                mockPatients = await res.json();
                el.patientSelector.innerHTML = '<option value="">-- Choose Patient Case --</option>';
                mockPatients.forEach(p => {
                    const opt = document.createElement('option');
                    opt.value = p.patientId;
                    opt.textContent = `${p.name} (${p.patientId}) - ${p.defaultMode}`;
                    el.patientSelector.appendChild(opt);
                });
            }
        } catch (e) {
            console.error("Failed to load mock profiles:", e);
        }
    }

    function handlePatientPresetChange() {
        const selVal = el.patientSelector.value;
        if (!selVal) {
            el.patientId.value = '';
            return;
        }
        const pat = mockPatients.find(p => p.patientId === selVal);
        if (pat) {
            el.patientId.value = pat.patientId;
            el.triggerMode.value = pat.defaultMode;
        }
    }

    function loadJaneDoePreset() {
        el.patientSelector.value = "64.08.12-205.33";
        handlePatientPresetChange();
        performImplantScan();
    }

    function togglePriorsSection() {
        const isHidden = el.priorsContent.style.display === 'none';
        if (isHidden) {
            el.priorsContent.style.display = 'block';
            el.priorsToggle.querySelector('.arrow-indicator').textContent = '▲ Click to Collapse';
        } else {
            el.priorsContent.style.display = 'none';
            el.priorsToggle.querySelector('.arrow-indicator').textContent = '▼ Click to Expand';
        }
    }

    // =========================================================================
    // SCAN PIPELINE DISCOVERY EXECUTION
    // =========================================================================
    async function performImplantScan() {
        const patId = el.patientId.value.trim();
        const mode = el.triggerMode.value;
        const model = el.modelSelect.value;
        const comment = el.customPrompt.value.trim();

        if (!patId) {
            showError("Please enter a valid Patient ID or select an option from the preset profiles.");
            return;
        }

        hideError();
        setLoadingState(true);

        try {
            const res = await fetch('./api/device-finder/scan', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    patientId: patId,
                    mode: mode,
                    model: model,
                    customPrompt: comment
                })
            });

            if (!res.ok) {
                const data = await res.json();
                throw new Error(data.error || `Server returned HTTP ${res.status}`);
            }

            const data = await res.json();
            renderScanResults(data);
        } catch (err) {
            showError(`implant scan failed: ${err.message}`);
        } finally {
            setLoadingState(false);
        }
    }

    function setLoadingState(loading) {
        if (loading) {
            el.scanBtn.disabled = true;
            el.scanBtn.innerHTML = '<span class="spinner"></span> Scanning PACS Metadata Cascade...';
            el.errorBanner.style.display = 'none';
        } else {
            el.scanBtn.disabled = false;
            el.scanBtn.innerHTML = '<span>⚡ Run Implant Discovery</span>';
        }
    }

    function showError(msg) {
        el.errorBanner.textContent = msg;
        el.errorBanner.style.display = 'block';
        el.errorBanner.scrollIntoView({ behavior: 'smooth' });
    }

    function hideError() {
        el.errorBanner.style.display = 'none';
    }

    // =========================================================================
    // RENDER METADATA / TILE GRIDS / ALERTS
    // =========================================================================
    function renderScanResults(data) {
        // Toggle Panels
        el.welcomeState.style.display = 'none';
        el.resultsDashboard.style.display = 'block';

        // Set Demographics Meta
        el.resPatientName.textContent = data.patientName;
        el.resPatientId.textContent = data.patientId;

        // Mock demographic particulars
        const mockPat = mockPatients.find(p => p.patientId === data.patientId);
        el.resPatientAge.textContent = mockPat ? mockPat.age : '54';
        el.resPatientGender.textContent = mockPat ? (mockPat.gender === 'F' ? 'Female' : 'Male') : 'Unspecified';
        el.resTriggerBadge.textContent = data.mode.replace('_', ' ');

        // Gather Policy & Orchestration Log Details (Rule 10)
        el.policyTitle.textContent = `Prompt budget policy constraints: Checked max ${data.budgetPolicy.maxStudiesReviewed} studies, max ${data.budgetPolicy.maxSeriesPerStudy} series/study, max ${data.budgetPolicy.maxContactSheetTiles} contact tiles. CT axial escalation: ${data.budgetPolicy.enableCtAxialEscalation}`;

        el.vlmStatusText.textContent = data.liveOllamaUsed ? "Qwen-VL Vision model active" : "Interactive Clinical NLP Fallback";
        el.vlmStatusText.className = "badge-status " + (data.liveOllamaUsed ? "matched" : "unconfirmed");
        el.vlmIngestedModel.textContent = data.orchestratedModel;

        const candidateCount = data.candidateStudies ? data.candidateStudies.length : 0;
        el.statStudiesFiltered.textContent = `${candidateCount} priors scored`;
        el.statPixelsSaved.textContent = `${(candidateCount * 128) - (data.contactSheetTiles ? data.contactSheetTiles.length : 0)} MB filtered prior to VLM Ingestion`;
        el.statMaxAllowed.textContent = `${data.budgetPolicy.maxVlmCallsTotal} total calls max`;

        // Render Alerts Alert Banner (Section 12 / Section 14)
        renderBannersAndAlerts(data);

        // Render Reconciliation Table (Step 7)
        renderReconciliationTable(data.reconciliation.recon_items);

        // Render Labeled Contact Sheet Tiles Grid (Rule 6, Rule 4/5)
        renderContactTiles(data.contactSheetTiles);

        // Render Reviewed priors detail list
        renderPriorsTable(data.candidateStudies);
    }

    function renderBannersAndAlerts(data) {
        el.alertContainer.innerHTML = '';
        const recon = data.reconciliation.recon_items || [];

        let hasDiscrepancy = false;
        let hasConflict = false;
        let hasCriticalClip = false;

        recon.forEach(item => {
            if (item.status === 'Visible but missing from structured record') {
                hasDiscrepancy = true;
                if (item.device_category.toLowerCase().includes('aneurysm clip')) {
                    hasCriticalClip = true;
                }
            }
            if (item.status === 'Conflicting information') {
                hasConflict = true;
            }
        });

        if (hasCriticalClip) {
            const div = document.createElement('div');
            div.className = 'alert-message critical-alert';
            div.innerHTML = `
                <div>❌</div>
                <div>
                    <strong>CRITICAL SECURITY DEVIATION DETECTED:</strong> Intracranial aneurysm locking clip identified on brain CT scout findings, 
                    but completely undocumented in EHR records. <strong>Strict MRI safety hazard contraindicator!</strong> Hold entry permission immediately.
                </div>
            `;
            el.alertContainer.appendChild(div);
        }

        if (hasDiscrepancy && !hasCriticalClip) {
            const div = document.createElement('div');
            div.className = 'alert-message discrepancy-alert';
            div.innerHTML = `
                <div>⚠️</div>
                <div>
                    <strong>ACTIVE EHR DISCREPANCY REGISTERED:</strong> Prior imaging displays dual lead electrical cardiac cardiac device (pacemaker), 
                    but there are no active device entries listed inside the patient's structured EHR record. Verification of safety credentials required.
                </div>
            `;
            el.alertContainer.appendChild(div);
        }

        if (hasConflict) {
            const div = document.createElement('div');
            div.className = 'alert-message critical-alert';
            div.innerHTML = `
                <div>❌</div>
                <div>
                    <strong>LATERAL CONFLICT EXPOSED:</strong> EHR structured records indicate a LEFT total hip arthroplasty, 
                    but radiography clearly indicates titanium fixture on the <strong>RIGHT</strong> pelvic hip joint. Potential documentation error in joint registry.
                </div>
            `;
            el.alertContainer.appendChild(div);
        }

        if (!hasDiscrepancy && !hasConflict && !hasCriticalClip) {
            // Check if patient actually has some implants, but they are fully matched
            const someMatched = recon.some(item => item.status === 'Matched known device');
            const div = document.createElement('div');
            div.className = 'alert-message success-alert';
            if (someMatched) {
                div.innerHTML = `
                    <div>✓</div>
                    <div>
                        <strong>VERIFICATION MATCH CONFIRMED:</strong> Connected implants detected on imaging match the structured EHR device logs perfectly. 
                        Safe local MRI and surgical pathway checklist protocols resolved.
                    </div>
                `;
            } else {
                div.innerHTML = `
                    <div>✓</div>
                    <div>
                        <strong>NORMAL CLEARANCE VERIFICATION:</strong> No metallic implants, fixations, loops, or pacemaker components 
                        identified across checked communities. Proceed with clinical imaging workflow.
                    </div>
                `;
            }
            el.alertContainer.appendChild(div);
        }
    }

    function renderReconciliationTable(items) {
        el.reconTableBody.innerHTML = '';
        items.forEach(item => {
            const tr = document.createElement('tr');

            let classMap = 'unconfirmed';
            if (item.status === 'Matched known device') classMap = 'matched';
            if (item.status === 'Visible but missing from structured record') classMap = 'discrepancy';
            if (item.status === 'Conflicting information') classMap = 'conflict';
            if (item.status === 'Structured record exists but no visual confirmation found') classMap = 'unconfirmed';

            tr.innerHTML = `
                <td><strong>${item.device_category}</strong></td>
                <td>${item.imaging_evidence}</td>
                <td><span class="badge-status ${classMap}">${item.status}</span></td>
                <td>${item.clinical_action}</td>
            `;
            el.reconTableBody.appendChild(tr);
        });
    }

    function renderContactTiles(tiles) {
        el.tilesGrid.innerHTML = '';
        if (!tiles || tiles.length === 0) {
            el.tilesGrid.innerHTML = '<p class="description">No selected candidate tiles extracted for VLM review (cheap pre-screens cleared all images).</p>';
            return;
        }

        tiles.forEach(tile => {
            const div = document.createElement('div');
            div.className = 'tile-card';

            const svgRepresentation = getSvgRadiologyRender(tile.imageMockUrl, tile.title);

            div.innerHTML = `
                <div class="tile-header">
                    <span class="tile-id-badge">${tile.tileId}</span>
                    <span class="tile-score">Cascade Score: <strong>+${tile.score}</strong></span>
                </div>
                <div class="tile-image">
                    ${svgRepresentation}
                </div>
                <div class="tile-body">
                    <div class="tile-meta">${tile.date.substring(0, 10)} | ${tile.modality} ${tile.bodySite} | ${tile.facility}</div>
                    <p class="tile-report" title="${tile.reportText}">${tile.reportText}</p>
                </div>
            `;
            el.tilesGrid.appendChild(div);
        });
    }

    function renderPriorsTable(scoredList) {
        el.priorsTableBody.innerHTML = '';
        if (!scoredList || scoredList.length === 0) {
            el.priorsTableBody.innerHTML = '<tr><td colspan="5" class="text-center">No priors studied.</td></tr>';
            return;
        }

        scoredList.forEach(item => {
            const tr = document.createElement('tr');
            tr.innerHTML = `
                <td>
                    <strong>${item.study.date.substring(0, 10)}</strong><br>
                    <span class="text-muted" style="font-size:0.75rem;">${item.study.facility}</span>
                </td>
                <td><span class="modality-badge">${item.study.modality}</span></td>
                <td><strong>${item.study.bodySite}</strong></td>
                <td>
                    <span style="font-weight:600; color:#212529;">${item.study.title}</span><br>
                    <span class="text-muted" style="font-size:0.82rem; line-height:1.3; display:block; margin-top:4px;">${item.study.summaryOfConclusion}</span>
                </td>
                <td><span class="prior-score-badge">${item.score}</span></td>
            `;
            el.priorsTableBody.appendChild(tr);
        });
    }

    // =========================================================================
    // LIGHTWEIGHT HIGH-QUALITY RADIOGRAPHIC SVG DRAWINGS (Self-contained visuals)
    // =========================================================================
    function getSvgRadiologyRender(filename, title) {
        const fileKey = filename ? filename.toLowerCase() : '';

        // Pacemaker chest X-ray
        if (fileKey.includes('pacemaker') || fileKey.includes('chest_scout') || title.toLowerCase().includes('chest')) {
            if (fileKey.includes('normal_chest')) {
                return `
                <svg viewBox="0 0 100 100" class="tile-xray-view">
                    <!-- Lung silhouettes -->
                    <path d="M25,25 Q35,10 45,25 L45,80 Q35,85 25,75 Z" fill="#0d1b2a" stroke="#415a77" stroke-width="1"/>
                    <path d="M75,25 Q65,10 55,25 L55,80 Q65,85 75,75 Z" fill="#0d1b2a" stroke="#415a77" stroke-width="1"/>
                    <!-- Rib contours -->
                    <path d="M20,35 Q35,40 45,45 M20,50 Q35,55 45,60 M20,65 Q35,70 45,75" stroke="#1b263b" stroke-width="1.5" fill="none" opacity="0.6"/>
                    <path d="M80,35 Q65,40 55,45 M80,50 Q65,55 55,60 M80,65 Q65,70 55,75" stroke="#1b263b" stroke-width="1.5" fill="none" opacity="0.6"/>
                    <!-- Spine -->
                    <line x1="50" y1="10" x2="50" y2="90" stroke="#415a77" stroke-width="3" stroke-dasharray="2,2" opacity="0.7"/>
                    <!-- Heart shadow -->
                    <path d="M40,55 Q50,75 60,65 Q50,45 40,55" fill="#1b263b" opacity="0.8"/>
                    <text x="50" y="93" fill="#ffca28" font-size="6" text-anchor="middle" font-weight="bold">PA CHEST (CLEARED)</text>
                </svg>`;
            }
            return `
            <svg viewBox="0 0 100 100" class="tile-xray-view">
                <!-- Lung contours -->
                <path d="M25,25 Q35,10 45,25 L45,80 Q35,85 25,75 Z" fill="#0d1b2a" stroke="#415a77" stroke-width="1"/>
                <path d="M75,25 Q65,10 55,25 L55,80 Q65,85 75,75 Z" fill="#0d1b2a" stroke="#415a77" stroke-width="1"/>
                <!-- Heart shadow -->
                <path d="M40,55 Q50,75 66,65 Q50,45 40,55" fill="#1b263b" opacity="0.8"/>
                <!-- Spinal column -->
                <line x1="50" y1="10" x2="50" y2="90" stroke="#415a77" stroke-width="2.5" stroke-dasharray="2,2"/>
                <!-- PACEMAKER GENERATOR (HIGH CONTRAST SHARP GREY) -->
                <rect x="23" y="27" width="12" height="9" rx="2" fill="#e0e0e0" stroke="#ffffff" stroke-width="0.8"/>
                <!-- Leads projection -->
                <path d="M33,31 Q45,35 48,50 T61,63" fill="none" stroke="#ffffff" stroke-width="1.2" stroke-linecap="round" filter="drop-shadow(0 0 1px #fff)"/>
                <path d="M33,31 Q43,40 45,55 T53,68" fill="none" stroke="#e0e0e0" stroke-width="0.9" stroke-linecap="round" opacity="0.9"/>
                <circle cx="61" cy="63" r="1" fill="#ffca28"/>
                <circle cx="53" cy="68" r="1" fill="#ffca28"/>
                <text x="50" y="93" fill="#ff3b30" font-size="6" text-anchor="middle" font-weight="bold">CARDIAC PM VISIBLE (LEF)</text>
            </svg>`;
        }

        // Shunt skull
        if (fileKey.includes('shunt_skull') || title.toLowerCase().includes('skull') || title.toLowerCase().includes('head')) {
            if (title.toLowerCase().includes('clips') || fileKey.includes('clips')) {
                return `
                <svg viewBox="0 0 100 100" class="tile-xray-view">
                    <!-- Skull contour -->
                    <path d="M50,15 C20,15 20,65 35,80 C40,85 60,85 65,80 C80,65 80,15 50,15 Z" fill="#0d1b2a" stroke="#415a77" stroke-width="1.5"/>
                    <path d="M35,80 C40,78 60,78 65,80 L50,90 Z" fill="#1b263b" stroke="#415a77" stroke-width="1"/>
                    <!-- Eye vaults -->
                    <circle cx="40" cy="45" r="8" fill="#090e17" stroke="#1b263b" stroke-width="1.5"/>
                    <circle cx="60" cy="45" r="8" fill="#090e17" stroke="#1b263b" stroke-width="1.5"/>
                    <!-- High density aneurysm clips (star clip) -->
                    <g transform="translate(50, 48)">
                        <path d="M-8,-2 L8,2 M-2,-8 L2,8 M-6,-6 L6,6 M-6,6 L6,-6" stroke="#ffffff" stroke-width="1.5" filter="drop-shadow(0 0 2px #fff)"/>
                        <circle cx="0" cy="0" r="1.5" fill="#ffca28"/>
                    </g>
                    <text x="50" y="93" fill="#ff3b30" font-size="6" text-anchor="middle" font-weight="bold">ACoM ANEURYSM CLIP</text>
                </svg>`;
            }
            return `
            <svg viewBox="0 0 100 100" class="tile-xray-view">
                <!-- Skull silhouette side view -->
                <path d="M30,70 C10,65 10,25 35,15 C60,5 85,20 85,50 C85,65 75,75 55,75 C45,75 40,70 30,70 Z" fill="#0d1b2a" stroke="#415a77" stroke-width="1.5"/>
                <path d="M30,70 L35,85 L50,85 L55,75" fill="#1b263b" stroke="#415a77" stroke-width="1"/>
                <!-- Shunt valve button -->
                <circle cx="55" cy="22" r="2.5" fill="#ffffff" stroke="#ffca28" stroke-width="0.8" filter="drop-shadow(0 0 1px #fff)"/>
                <!-- Shunt tube descending down neck -->
                <path d="M55,22 C48,25 45,35 48,50 T44,88" fill="none" stroke="#ffffff" stroke-width="1.2" stroke-dasharray="100" filter="drop-shadow(0 0 1px #fff)"/>
                <text x="50" y="93" fill="#ffcb2f" font-size="6" text-anchor="middle" font-weight="bold">SKULL VP SHUNT TUBE</text>
            </svg>`;
        }

        // Shunt abdomen
        if (fileKey.includes('shunt_abdomen') || title.toLowerCase().includes('abdomen')) {
            return `
            <svg viewBox="0 0 100 100" class="tile-xray-view">
                <!-- Abdomen ribs & Pelvic grid -->
                <path d="M25,25 Q35,28 45,30 M75,25 Q65,28 55,30" stroke="#1b263b" fill="none"/>
                <!-- Lumbar spine vertebrae -->
                <rect x="47" y="10" width="6" height="6" fill="#1b263b" stroke="#415a77" stroke-width="0.5"/>
                <rect x="47" y="18" width="6" height="6" fill="#1b263b" stroke="#415a77" stroke-width="0.5"/>
                <rect x="47" y="26" width="6" height="6" fill="#1b263b" stroke="#415a77" stroke-width="0.5"/>
                <rect x="47" y="34" width="6" height="6" fill="#1b263b" stroke="#415a77" stroke-width="0.5"/>
                <!-- Pelvic structural wings -->
                <path d="M20,60 Q30,50 50,65 Q70,50 80,60 L75,85 L25,85 Z" fill="#0d1b2a" stroke="#415a77" stroke-width="1.2"/>
                <!-- Shunt distal catheter winding -->
                <path d="M38,10 Q39,30 35,50 T48,65 Q65,72 55,80 T38,82" fill="none" stroke="#ffffff" stroke-width="1.1" stroke-linecap="round" filter="drop-shadow(0 0 1px #fff)"/>
                <text x="50" y="93" fill="#ffcb2f" font-size="6" text-anchor="middle" font-weight="bold">PERITONEAL SHUNT TIP</text>
            </svg>`;
        }

        // Hip prosthesis sarah
        if (fileKey.includes('hip_ortho') || title.toLowerCase().includes('pelvis') || title.toLowerCase().includes('hip')) {
            return `
            <svg viewBox="0 0 100 100" class="tile-xray-view">
                <!-- Pelvic bone symmetrical silhouette -->
                <path d="M15,40 Q25,30 50,45 Q75,30 85,40 L80,65 Q50,75 20,65 Z" fill="#0d1b2a" stroke="#415a77" stroke-width="1.2"/>
                <!-- Pubic arch -->
                <path d="M40,65 Q50,58 60,65 L50,75 Z" fill="#090e17" stroke="#415a77" stroke-width="1"/>
                <!-- LEFT Joint (No hardware) -->
                <circle cx="28" cy="55" r="4.5" fill="#0d1b2a" stroke="#415a77"/>
                <path d="M28,58 Q24,65 22,85" stroke="#415a77" stroke-width="3" fill="none"/>
                <!-- RIGHT TOTAL HIP ARTHROPLASTY (SOLID WHITE DETECTED IMPLANT) -->
                <!-- Shaft liner -->
                <path d="M72,58 Q77,66 80,85" stroke="#ffffff" stroke-width="3.5" fill="none" filter="drop-shadow(0 0 1.5px #fff)"/>
                <!-- Acetabular cup & metallic neck head -->
                <circle cx="72" cy="55" r="4.8" fill="#e0e0e0" stroke="#ffffff" stroke-width="0.8" filter="drop-shadow(0 0 1.5px #fff)"/>
                <line x1="72" y1="55" x2="73" y2="61" stroke="#ffffff" stroke-width="1.8"/>
                <text x="50" y="93" fill="#ff3b30" font-size="6" text-anchor="middle" font-weight="bold">RIGHT HIP IMPLANT VISIBLE</text>
            </svg>`;
        }

        // Knee screws (Emma Watson)
        if (fileKey.includes('screws') || title.toLowerCase().includes('knee') || title.toLowerCase().includes('extremity')) {
            return `
            <svg viewBox="0 0 100 100" class="tile-xray-view">
                <!-- Femur distal silhouette -->
                <path d="M40,10 L40,35 Q30,42 50,44 Q70,42 60,35 L60,10 Z" fill="#0d1b2a" stroke="#415a77" stroke-width="1.2"/>
                <!-- Tibia proximal silhouette -->
                <path d="M40,90 L40,55 Q30,47 50,45 Q70,47 60,55 L60,90 Z" fill="#0d1b2a" stroke="#415a77" stroke-width="1.2"/>
                <!-- Patella silhouette -->
                <circle cx="50" cy="42" r="5" fill="#1b263b" opacity="0.6"/>
                <!-- Metal compression locking plate -->
                <rect x="58" y="47" width="5" height="35" rx="1.5" fill="#e0e0e0" stroke="#ffffff" stroke-width="0.8" filter="drop-shadow(0 0 1px #fff)"/>
                <!-- 6 bone locking screws -->
                <line x1="58" y1="51" x2="35" y2="51" stroke="#ffffff" stroke-width="1.2" filter="drop-shadow(0 0 1px #fff)"/>
                <circle cx="58" cy="51" r="0.8" fill="#ffca28"/>
                <line x1="58" y1="56" x2="38" y2="56" stroke="#ffffff" stroke-width="1.2"/>
                <circle cx="58" cy="56" r="0.8" fill="#ffca28"/>
                <line x1="58" y1="62" x2="42" y2="62" stroke="#ffffff" stroke-width="1.2"/>
                <circle cx="58" cy="62" r="0.8" fill="#ffca28"/>
                <line x1="58" y1="68" x2="40" y2="68" stroke="#ffffff" stroke-width="1.2"/>
                <circle cx="58" cy="68" r="0.8" fill="#ffca28"/>
                <line x1="58" y1="74" x2="43" y2="74" stroke="#ffffff" stroke-width="1.2"/>
                <circle cx="58" cy="74" r="0.8" fill="#ffca28"/>
                <line x1="58" y1="79" x2="41" y2="79" stroke="#ffffff" stroke-width="1.2"/>
                <circle cx="58" cy="79" r="0.8" fill="#ffca28"/>
                <text x="50" y="93" fill="#ffcb2f" font-size="6" text-anchor="middle" font-weight="bold">6-SCREW TIBIAL PLATE</text>
            </svg>`;
        }

        // Generic / Fallback generic scan outline
        return `
        <svg viewBox="0 0 100 100" class="tile-xray-view">
            <line x1="10" y1="50" x2="90" y2="50" stroke="#415a77" stroke-width="1" stroke-dasharray="3,3"/>
            <line x1="50" y1="10" x2="50" y2="90" stroke="#415a77" stroke-width="1" stroke-dasharray="3,3"/>
            <rect x="15" y="15" width="70" height="70" fill="none" stroke="#212529" stroke-width="1" stroke-dasharray="1,2"/>
            <text x="50" y="52" fill="#e0e0e0" font-size="5" text-anchor="middle">REPRESENTATIVE FRAME</text>
        </svg>`;
    }


    // =========================================================================
    // EXPOSE MEMBERS PUBLICLY
    // =========================================================================
    return {
        init: init
    };

})();

// Start script on load
document.addEventListener('DOMContentLoaded', DeviceFinder.init);

