package be.uzleuven.ihe.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.*;

/**
 * REST controller for the Device and Implant Imaging Finder app.
 * Highlights clinical findings, does study ranking, text/report keyword scoring,
 * cheap pre-screening, contact sheet layout generation, Ollama VLM orchestration,
 * and EHR device list reconciliation.
 */
@RestController
@RequestMapping("/api/device-finder")
@CrossOrigin(origins = "*")
public class DeviceFinderAPIController {

    private static final Logger LOGGER = LoggerFactory.getLogger(DeviceFinderAPIController.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Value("${ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    @Value("${ollama.default-model:qwen2.5:7b-instruct}")
    private String defaultModel;

    @Value("${ollama.timeout-seconds:120}")
    private long timeoutSeconds;

    // =========================================================================
    // KEYWORD GROUPS FOR TEXT ANALYSIS (Rule 2)
    // =========================================================================
    private static final List<String> HIGH_BOOST_KEYWORDS = Arrays.asList(
            "pacemaker", "icd", "defibrillator", "crt", "cardiac device", "loop recorder",
            "aneurysm clip", "cochlear implant", "hearing implant", "neurostimulator",
            "deep brain stimulator", "spinal stimulator", "spinal cord stimulator",
            "vp shunt", "ventriculoperitoneal", "ivc filter", "prosthesis", "arthroplasty",
            "fusion hardware", "joint replacement", "hip replacement", "knee replacement"
    );

    private static final List<String> MEDIUM_BOOST_KEYWORDS = Arrays.asList(
            "stent", "graft", "endograft", "clip", "coil", "embolization", "port",
            "catheter", "picc", "central venous catheter", "port-a-cath", "valve", "tavr",
            "sternotomy wires", "screws", "plates", "rod", "nails", "hardware", "implant"
    );

    private static final List<String> WEAK_BOOST_KEYWORDS = Arrays.asList(
            "postoperative", "surgical change", "metallic", "foreign body", "fixation", "retained"
    );

    // =========================================================================
    // MOCK CLINICAL DATA REPOSITORY (For high-fidelity clinical simulation)
    // =========================================================================
    private static final Map<String, MockPatientCase> MOCK_PATIENS = new LinkedHashMap<>();

    static {
        // Patient 1: Jane Doe (Pacemaker Discrepancy)
        MockPatientCase p1 = new MockPatientCase("64.08.12-205.33", "Jane Doe", "F", 62, "MRI_SAFETY");
        p1.addEhrDevice("None", "No implants or devices registered in active EHR Structured Device List.");

        p1.addPriorStudy("study-011", "2025-04-02", "CR", "Chest", "CHEST 2 VIEWS (PA & LATERAL)", "Hospital B",
                "Indication: Shortness of breath.\n" +
                "Findings: The lungs are clear. Minimal cardiomegaly. There is a left anterior chest wall cardiac implantable electronic device, " +
                "compatible with a dual-chamber pacemaker or ICD. Two radiopaque leads are visible extending through the superior vena cava into " +
                "the right atrium and right ventricle. No evidence of pneumothorax.\n" +
                "Conclusion: Left chest dual-lead pacemaker in appropriate position without complications.",
                "tile_A1_jane_doe_pacemaker.png");

        p1.addPriorStudy("study-012", "2024-11-10", "CT", "Chest", "CT CHEST WITHOUT CONTRAST (SCOUT)", "Hospital B",
                "Low-dose localizer scout confirms dual-lead cardiac pacing system in the left pectoral region.",
                "tile_A2_jane_doe_ct_scout.png");

        p1.addPriorStudy("study-013", "2021-07-09", "CR", "Pelvis", "PELVIS COCCYX 2 VIEWS", "Hospital C",
                "Mild age-related osteopenia, normal alignment. No orthopedic hardware visible.",
                "tile_A3_jane_doe_pelvis.png");
        MOCK_PATIENS.put(p1.patientId, p1);

        // Patient 2: Robert Johnson (VP Shunt Tubing)
        MockPatientCase p2 = new MockPatientCase("55.11.02-124.89", "Robert Johnson", "M", 71, "ED_QUICK_SCAN");
        p2.addEhrDevice("VP Shunt", "Ventriculoperitoneal Shunt (Codman Certas Plus, programmable), Implanted 2021.");

        p2.addPriorStudy("study-021", "2025-01-15", "CR", "Skull", "SKULL AP & LATERAL 2 VIEWS", "Community Hospital A",
                "Indication: Evaluation of shunt integrity and shunt setting.\n" +
                "Findings: Ventriculoperitoneal (VP) shunt catheter enters the right parietal skull. Shunt valve reservoir is seen " +
                "in the right post-auricular region. Distal tubing is traced as it exits the skull and descends along the right lateral neck.\n" +
                "Conclusion: Intact right ventriculoperitoneal shunt system.",
                "tile_B1_robert_shunt_skull.png");

        p2.addPriorStudy("study-022", "2025-01-15", "CR", "Abdomen", "ABDOMEN ACUTE FLAT & ERECT", "Community Hospital A",
                "Distal aspect of the VP shunt catheter is visualized in the peritoneal cavity with ample slack in the right pelvic gutter. " +
                "Slight fecal loading, no bowl obstruction.",
                "tile_B2_robert_shunt_abdomen.png");
        MOCK_PATIENS.put(p2.patientId, p2);

        // Patient 3: Sarah Connor (Hip Prosthesis Reconciliation Conflict)
        MockPatientCase p3 = new MockPatientCase("84.02.28-111.45", "Sarah Connor", "F", 42, "PRE_OP");
        p3.addEhrDevice("Left Hip Arthroplasty", "Structured record reports status post LEFT total hip arthroplasty (Zimmer Biomet).");

        p3.addPriorStudy("study-031", "2024-05-22", "CR", "Pelvis", "X-RAY PELVIS COMPLETE AP & INLET", "Regional Medical Center",
                "Indication: Persistent hip pain.\n" +
                "Findings: Solid fixation of a RIGHT total hip arthroplasty model with press-fit femoral stem and acetabular shell. " +
                "The LEFT hip joint demonstrates mild narrowing but possesses NO orthopedic hardware. Mild pelvic tilt.\n" +
                "Conclusion: Press-fit RIGHT total hip prosthesis with normal alignment. Structured record conflict noted.",
                "tile_C1_sarah_hip_ortho.png");
        MOCK_PATIENS.put(p3.patientId, p3);

        // Patient 4: David Miller (Aneurysm Clips - High Risk)
        MockPatientCase p4 = new MockPatientCase("73.09.11-447.12", "David Miller", "M", 52, "MRI_SAFETY");
        p4.addEhrDevice("None", "No implants or devices registered in active EHR Structured Device List.");

        p4.addPriorStudy("study-041", "2023-08-30", "CT", "Skull", "CT HEAD PROTOCOL PRE & POST CONTRAST", "University Hospital",
                "Indication: Severe acute headache, follow-up after surgical clipping.\n" +
                "Findings: Metallic clip artifact is visible at the anterior communicating artery territory. Significant beam hardening " +
                "is noted. No acute hemorrhage, stable ventricles.\n" +
                "Conclusion: Status post anterior communicating artery aneurysm metallic clipping. High-risk MRI safety finding.",
                "tile_D1_david_cerebral_clips.png");
        p4.addPriorStudy("study-042", "2023-08-31", "XA", "Skull", "CEREBRAL ANGIOGRAPHY MULTI-FRAME", "University Hospital",
                "Surgical metallic microclip successfully occludes the ACoM aneurysm. Complete exclusion with normal distal cerebral perfusion.",
                "tile_D2_david_angio.png");
        MOCK_PATIENS.put(p4.patientId, p4);

        // Patient 5: Emma Watson (Knee Plates & Screws - Matched)
        MockPatientCase p5 = new MockPatientCase("90.04.15-303.01", "Emma Watson", "F", 36, "GP_SUMMARY");
        p5.addEhrDevice("Right Tibial Plate & Screws", "Internal fixation of right tibia post fracture (Stryker LCP System).");

        p5.addPriorStudy("study-051", "2025-02-18", "CR", "Extremity", "X-RAY RIGHT KNEE AP & LAT", "Regional Medical Center",
                "Indication: 6-month post-op check.\n" +
                "Findings: Lateral locking compression plate and 6 transcortical screws are visible along the proximal tibia. " +
                "Fracture line is fully consolidated. No hardware loosening, loosening or periosteal reaction.\n" +
                "Conclusion: Stable locking plate fixation of right proximal tibial plateau fracture.",
                "tile_E1_emma_knee_screws.png");
        MOCK_PATIENS.put(p5.patientId, p5);

        // Patient 6: Normal (No Hardware)
        MockPatientCase p6 = new MockPatientCase("12.12.12-111.11", "Normal Patient", "M", 14, "GP_SUMMARY");
        p6.addEhrDevice("None", "No known devices in EHR.");
        p6.addPriorStudy("study-061", "2025-01-10", "CR", "Chest", "CHEST AP PORTABLE", "Hospital C",
                "The lungs are fully aerated, clear. Heart size normal. No pacemakers, clips, shunts, or lines visible.",
                "tile_F1_normal_chest.png");
        MOCK_PATIENS.put(p6.patientId, p6);
    }

    // =========================================================================
    // ENDPOINTS
    // =========================================================================

    /**
     * Get list of demo patients
     */
    @GetMapping(value = "/patients", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Map<String, Object>>> getPatients() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (MockPatientCase p : MOCK_PATIENS.values()) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("patientId", p.patientId);
            map.put("name", p.name);
            map.put("gender", p.gender);
            map.put("age", p.age);
            map.put("defaultMode", p.defaultMode);
            map.put("ehrDevices", p.ehrDevices);
            result.add(map);
        }
        return ResponseEntity.ok(result);
    }

    /**
     * Run the complete implant discovery pipeline.
     */
    @PostMapping(value = "/scan", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> scanPatient(@RequestBody JsonNode reqBody) {
        String patientId = reqBody.path("patientId").asText().trim();
        String mode = reqBody.path("mode").asText("MRI_SAFETY").toUpperCase();
        String model = reqBody.path("model").asText("").trim();
        String modelOverridePrompt = reqBody.path("customPrompt").asText("").trim();

        // For vision language models (VLM), only allow qwen3.6:27b
        if (!model.contains("mock")) {
            model = "qwen3.6:27b";
        }

        if (patientId.isEmpty()) {
            return errorResponse(HttpStatus.BAD_REQUEST, "patientId is empty");
        }

        // 1. Trigger Policy Config (Rule 10)
        BudgetPolicy policy = getBudgetPolicy(mode);

        // 2. Discover & Load Priors (Step 2 and 3)
        // Best practice: if mock patient contains this ID, load mock priors, otherwise search general repository
        MockPatientCase pc = MOCK_PATIENS.get(patientId);
        List<StudyMetadata> priors;
        List<String> ehrDevices;
        String patientName;

        if (pc != null) {
            priors = new ArrayList<>(pc.studies);
            ehrDevices = new ArrayList<>(pc.ehrDevices);
            patientName = pc.name;
        } else {
            // General fallback mode
            priors = generateGenericFallbackPriors();
            ehrDevices = Arrays.asList("None");
            patientName = "Unknown Patient (" + patientId + ")";
        }

        // 3. Metadata Filtering, Modality Prioritization, and Report Analysis Scoring (Rule 3)
        List<ScoredStudy> scoredStudies = new ArrayList<>();
        int currentYear = LocalDate.now().getYear();

        for (StudyMetadata s : priors) {
            int score = 0;

            // Modality score (Heuristics table in Section 5/7)
            String m = s.modality.toUpperCase();
            if (m.equals("CR") || m.equals("DX")) {
                score += 40;
            } else if (s.title.toLowerCase().contains("scout") || s.title.toLowerCase().contains("localizer")) {
                score += 35; // Scout/Localizer
            } else if (m.equals("CT")) {
                score += 25; // CT relevant region
            } else if (m.equals("RF") || m.equals("XA")) {
                score += 20; // Procedural hardware
            } else if (m.equals("MG")) {
                score += 15; // breast markers
            } else if (m.equals("US")) {
                score -= 30; // Unsupported
            } else if (m.equals("MR")) {
                score -= 30; // MR is low for safety discovery
            } else {
                score -= 10;
            }

            // Series description / body-site (+20)
            String body = s.bodySite.toLowerCase();
            String title = s.title.toLowerCase();
            if (body.contains("chest") || body.contains("skull") || body.contains("pelvis") || body.contains("spine") || body.contains("head") || body.contains("neck")) {
                score += 20;
            } else if (title.contains("chest") || title.contains("skull") || title.contains("pelvis") || title.contains("spine") || title.contains("head") || title.contains("neck")) {
                score += 20;
            }

            // Recency score (+10)
            try {
                String dStr = s.date;
                if (dStr.length() >= 4) {
                    int studyYear = Integer.parseInt(dStr.substring(0, 4));
                    if ((currentYear - studyYear) <= 5) {
                        score += 10;
                    }
                }
            } catch (Exception ignored) {}

            // Trigger score (+20)
            if (mode.equals("MRI_SAFETY")) {
                score += 20;
            }

            // Keyword boosts (Rule 2 + Rule 11)
            int keywordBoost = 0;
            String textToAnalyze = (s.title + " " + s.bodySite + " " + s.summaryOfConclusion).toLowerCase();

            // Check major strong boosts
            boolean hasStrongWord = false;
            for (String kw : HIGH_BOOST_KEYWORDS) {
                if (textToAnalyze.contains(kw)) {
                    hasStrongWord = true;
                    break;
                }
            }
            if (hasStrongWord) {
                keywordBoost += 50;
            }

            // Check medium boosts
            boolean hasMedWord = false;
            for (String kw : MEDIUM_BOOST_KEYWORDS) {
                if (textToAnalyze.contains(kw)) {
                    hasMedWord = true;
                    break;
                }
            }
            if (hasMedWord) {
                keywordBoost += 25;
            }

            // Check weak boosts
            boolean hasWeakWord = false;
            for (String kw : WEAK_BOOST_KEYWORDS) {
                if (textToAnalyze.contains(kw)) {
                    hasWeakWord = true;
                    break;
                }
            }
            if (hasWeakWord) {
                keywordBoost += 10;
            }

            score += keywordBoost;

            scoredStudies.add(new ScoredStudy(s, score, keywordBoost));
        }

        // Sort by decreasing score
        scoredStudies.sort((a, b) -> Integer.compare(b.score, a.score));

        // Enforce prompt budget of studies reviewed (Rule 10)
        List<ScoredStudy> candidateStudies = new ArrayList<>();
        int count = 0;
        for (ScoredStudy ss : scoredStudies) {
            // Apply duplicate older study deduction from prompt rules if multiple same region studies exist
            boolean isDuplicateRegion = false;
            for (ScoredStudy accepted : candidateStudies) {
                if (accepted.study.bodySite.equalsIgnoreCase(ss.study.bodySite) && accepted.study.modality.equalsIgnoreCase(ss.study.modality)) {
                    isDuplicateRegion = true;
                    break;
                }
            }
            if (isDuplicateRegion) {
                ss.score -= 15; // duplicate older study from same body region penalty
            }

            if (count < policy.maxStudiesReviewed) {
                candidateStudies.add(ss);
                count++;
            }
        }

        // Sort studies again after duplicate penalties applied
        candidateStudies.sort((a, b) -> Integer.compare(b.score, a.score));

        // 4. Selective image selection & visual tiles generation (Rule 4, Rule 5 and Rule 6)
        // In our high-fidelity system, we select only high-priority diagnostic images.
        // Let's create a beautiful structured result outlining the contact sheet layout
        List<Map<String, Object>> selectedTiles = new ArrayList<>();
        char rowLabel = 'A';
        int colIndex = 1;

        for (int i = 0; i < candidateStudies.size(); i++) {
            ScoredStudy ss = candidateStudies.get(i);

            // Check if we exceed image budget before VLM
            if (selectedTiles.size() >= policy.maxImagesBeforeVlm) {
                break;
            }

            // High-density selection logic: only prioritize files with decent scores
            if (ss.score < 10) {
                continue; // ignore unrelated low relevance ones (Rule 5 cheap pre-screen)
            }

            Map<String, Object> tile = new HashMap<>();
            String tileId = "" + rowLabel + colIndex;
            tile.put("tileId", tileId);
            tile.put("studyId", ss.study.id);
            tile.put("title", ss.study.title);
            tile.put("date", ss.study.date);
            tile.put("facility", ss.study.facility);
            tile.put("bodySite", ss.study.bodySite);
            tile.put("modality", ss.study.modality);
            tile.put("score", ss.score);
            tile.put("reportText", ss.study.summaryOfConclusion);

            // Link to SVG placeholder or mock image representation inside response
            tile.put("imageMockUrl", ss.study.imageResource);

            selectedTiles.add(tile);

            // Increment matrix layout
            colIndex++;
            if (colIndex > 3) {
                colIndex = 1;
                rowLabel++;
            }
        }

        // Limit contact sheet dimensions by trigger policy (Rule 10)
        if (selectedTiles.size() > policy.maxContactSheetTiles) {
            selectedTiles = selectedTiles.subList(0, policy.maxContactSheetTiles);
        }

        // 5. Ollama VLM orchestration / AI response (Rule 6, Rule 7, Section 8 user prompt)
        // If Ollama is available, we can trigger a real call.
        // Alternatively, if Ollama is offline or doesn't have a vision-language model,
        // we can perform high-fidelity prompt generation, return it for demonstration, and
        // fall back on a deterministic clinical NLP engine (rule-based matches) with beautiful
        // mock analysis to support both modes natively!
        String finalModel = model;
        Map<String, Object> aiResult = null;
        boolean liveOllamaUsed = false;

        // Try Live Ollama chat if requested or possible
        if (!finalModel.contains("mock") && isOllamaOnline()) {
            try {
                aiResult = callLiveOllamaForImplantDiscovery(selectedTiles, finalModel, mode, modelOverridePrompt);
                liveOllamaUsed = true;
            } catch (Exception e) {
                LOGGER.warn("Live Ollama call failed (Will fall back to clinical parsing helper): {}", e.getMessage());
            }
        }

        if (aiResult == null) {
            // Apply rule-based clinical scanner representing our VLM first pass (Pass 1)
            aiResult = generateClinicalNlpHelfidelity(selectedTiles, patientId);
        }

        // 6. Structured EHR Device Reconciliation (Section 7, Rule 7 / Rule 9 / Step 7)
        Map<String, Object> reconciliationResult = reconcileDevices(aiResult, ehrDevices);

        // 7. Assemble Consolidated Response
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("patientId", patientId);
        response.put("patientName", patientName);
        response.put("mode", mode);
        response.put("budgetPolicy", policy);
        response.put("liveOllamaUsed", liveOllamaUsed);
        response.put("orchestratedModel", finalModel);
        response.put("candidateStudies", candidateStudies);
        response.put("contactSheetTiles", selectedTiles);
        response.put("aiAnalysis", aiResult);
        response.put("reconciliation", reconciliationResult);

        return ResponseEntity.ok(response);
    }

    // =========================================================================
    // HELPER FUNCTIONS & ALGORITHMS
    // =========================================================================

    private BudgetPolicy getBudgetPolicy(String mode) {
        return switch (mode) {
            case "MRI_SAFETY" -> new BudgetPolicy(10, 4, 40, 12, 2, 5, true);
            case "GP_SUMMARY" -> new BudgetPolicy(3, 2, 12, 6, 1, 2, false);
            case "ED_QUICK_SCAN" -> new BudgetPolicy(6, 3, 20, 9, 1, 3, true);
            case "PRE_OP" -> new BudgetPolicy(8, 3, 30, 9, 2, 4, true);
            default -> new BudgetPolicy(5, 3, 20, 9, 1, 3, false);
        };
    }

    private boolean isOllamaOnline() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(trimTrailingSlash(ollamaBaseUrl) + "/api/tags"))
                    .timeout(Duration.ofSeconds(2))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Reconciles AI found candidates against EHR device lists. (Step 7)
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> reconcileDevices(Map<String, Object> aiResult, List<String> ehrDevices) {
        Map<String, Object> rec = new LinkedHashMap<>();
        List<Map<String, Object>> items = new ArrayList<>();

        List<Map<String, Object>> candidates = (List<Map<String, Object>>) aiResult.get("device_candidates");
        if (candidates == null) candidates = new ArrayList<>();

        boolean hasPacemakerAI = false;
        boolean hasShuntAI = false;
        boolean hasHipAI = false;
        boolean hasClipAI = false;
        boolean hasKneeAI = false;

        for (Map<String, Object> cand : candidates) {
            String cat = String.valueOf(cand.get("device_category")).toLowerCase();
            if (cat.contains("cardiac") || cat.contains("pacemaker") || cat.contains("icd")) hasPacemakerAI = true;
            if (cat.contains("shunt") || cat.contains("vp shunt")) hasShuntAI = true;
            if (cat.contains("hip") || cat.contains("arthroplasty") || cat.contains("pelvis")) hasHipAI = true;
            if (cat.contains("clip") || cat.contains("aneurysm") || cat.contains("coil")) hasClipAI = true;
            if (cat.contains("knee") || cat.contains("plate") || cat.contains("screws")) hasKneeAI = true;
        }

        // Reconcile Patient 1 Pacemaker Pacemaker VS EHR
        if (hasPacemakerAI) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("device_category", "Cardiac Implantable Electronic Device (Pacemaker)");
            row.put("imaging_evidence", "Visible on Chest X-ray A1 generator and lead lines");

            boolean inEhr = false;
            for (String ehr : ehrDevices) {
                if (ehr.toLowerCase().contains("pacemaker") || ehr.toLowerCase().contains("cardiac")) {
                    inEhr = true;
                }
            }
            if (inEhr) {
                row.put("status", "Matched known device");
                row.put("clinical_action", "No further action needed: Implant matches active record.");
            } else {
                row.put("status", "Visible but missing from structured record");
                row.put("clinical_action", "⚠️ High Discrepancy! Pacemaker is visible on imaging but missing in EHR. MRI Safety review required.");
            }
            items.add(row);
        }

        // Reconcile Patient 2 VP Shunt
        if (hasShuntAI) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("device_category", "Ventriculoperitoneal (VP) Shunt");
            row.put("imaging_evidence", "Tubing entering right parietal skull (B1) and abdomen cavity (B2)");
            boolean inEhr = false;
            for (String ehr : ehrDevices) {
                if (ehr.toLowerCase().contains("shunt") || ehr.toLowerCase().contains("vp")) {
                    inEhr = true;
                }
            }
            if (inEhr) {
                row.put("status", "Matched known device");
                row.put("clinical_action", "✓ Shunt is documented. Re-verifying valve programming prior to high magnetic field exposures.");
            } else {
                row.put("status", "Visible but missing from structured record");
                row.put("clinical_action", "⚠️ VP Shunt detected visually but absent from record. Requires immediate safety evaluation.");
            }
            items.add(row);
        }

        // Reconcile Patient 3 Hip Arthroplasty (with conflicting laterality)
        if (hasHipAI) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("device_category", "Total Hip Prosthesis");
            row.put("imaging_evidence", "Right total hip arthroplasty visible on AP pelvis (C1). Left hip has no hardware.");

            boolean leftEhr = false;
            boolean rightEhr = false;
            for (String ehr : ehrDevices) {
                if (ehr.toLowerCase().contains("left hip") || ehr.toLowerCase().contains("left total hip")) leftEhr = true;
                if (ehr.toLowerCase().contains("right hip") || ehr.toLowerCase().contains("right total hip")) rightEhr = true;
            }

            if (leftEhr && !rightEhr) {
                row.put("status", "Conflicting information");
                row.put("clinical_action", "❌ Lateral Conflict! EHR records LEFT hip replacement, but imaging clearly shows RIGHT hip replacement. Orthopedic list update required.");
            } else if (rightEhr) {
                row.put("status", "Matched known device");
                row.put("clinical_action", "✓ Right hip replacement documented and verified.");
            } else {
                row.put("status", "Visible but missing from structured record");
                row.put("clinical_action", "⚠️ Hip prosthesis visible, undocumented in EHR.");
            }
            items.add(row);
        }

        // Reconcile Patient 4 Aneurysm Clips
        if (hasClipAI) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("device_category", "Intracranial Aneurysm Clip");
            row.put("imaging_evidence", "High-density metallic star clip artifact at skull center (D1/D2)");

            boolean inEhr = false;
            for (String ehr : ehrDevices) {
                if (ehr.toLowerCase().contains("clip") || ehr.toLowerCase().contains("aneurysm")) {
                    inEhr = true;
                }
            }
            if (inEhr) {
                row.put("status", "Matched known device");
                row.put("clinical_action", "Confirm manufacturer compatibility (some aneurysm clips are contraindicated for MRI).");
            } else {
                row.put("status", "Visible but missing from structured record");
                row.put("clinical_action", "🚫 Extremely Critical Risk! Cerebral metallic clip found but missing from EHR. MRI is strictly contraindicated until safety certificate retrieved.");
            }
            items.add(row);
        }

        // Reconcile Patient 5 Knee Screws
        if (hasKneeAI) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("device_category", "Tibial Compression Plate & Screws");
            row.put("imaging_evidence", "Plates with 6 steel cortical fixation screws on proximal tibia (E1)");

            boolean inEhr = false;
            for (String ehr : ehrDevices) {
                if (ehr.toLowerCase().contains("tibia") || ehr.toLowerCase().contains("plate") || ehr.toLowerCase().contains("screw")) {
                    inEhr = true;
                }
            }
            if (inEhr) {
                row.put("status", "Matched known device");
                row.put("clinical_action", "✓ Hardware matches postoperative record. Normal MRI local artifacts expected.");
            } else {
                row.put("status", "Visible but missing from structured record");
                row.put("clinical_action", "⚠️ Orthopedic plates found undocumented.");
            }
            items.add(row);
        }

        // Fallback for empty/no findings
        if (items.isEmpty()) {
            boolean recordsExistButNoHardware = !ehrDevices.isEmpty() && !ehrDevices.get(0).equals("None");
            if (recordsExistButNoHardware) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("device_category", "EHR Listed Devices");
                row.put("imaging_evidence", "No hardware observed in any reviewed modality");
                row.put("status", "Structured record exists but no visual confirmation found");
                row.put("clinical_action", "Verify if device was previously explanted or is on an unreviewed anatomic segment.");
                items.add(row);
            } else {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("device_category", "None");
                row.put("imaging_evidence", "Empty");
                row.put("status", "No visible device found in reviewed studies");
                row.put("clinical_action", "No imaging evidence of implants. Standard MRI clinical clearance may proceed.");
                items.add(row);
            }
        }

        rec.put("recon_items", items);
        rec.put("reconciliation_date", LocalDate.now().toString());
        return rec;
    }

    /**
     * Fallback high-fidelity NLP-engine. Evaluates keywords in text and builds the
     * exact visual JSON responses corresponding to the clinical presentation.
     */
    private Map<String, Object> generateClinicalNlpHelfidelity(List<Map<String, Object>> tiles, String patientId) {
        Map<String, Object> ai = new LinkedHashMap<>();
        ai.put("review_quality", "adequate");

        List<Map<String, Object>> candidates = new ArrayList<>();
        List<Map<String, Object>> negatives = new ArrayList<>();

        for (Map<String, Object> tile : tiles) {
            String report = String.valueOf(tile.get("reportText")).toLowerCase();
            String tileId = String.valueOf(tile.get("tileId"));
            String bSite = String.valueOf(tile.get("bodySite"));

            if (report.contains("pacemaker") || report.contains("electronic device") || report.contains("pacing system")) {
                Map<String, Object> cand = new LinkedHashMap<>();
                cand.put("tile_id", tileId);
                cand.put("device_category", "cardiac implantable electronic device");
                cand.put("specific_type_if_visible", "pacemaker or ICD, dual lead system");
                cand.put("anatomical_location", "left anterior chest wall, leads extending to RA and RV");
                cand.put("laterality", "left");
                cand.put("confidence", "high");
                cand.put("visual_evidence", "generator-like rectangular radiopaque object in left chest with lead lines");
                cand.put("needs_zoom_or_human_review", true);
                cand.put("reason_for_uncertainty", "imaging cannot confirm specific model serial or MRI safety constraints");
                candidates.add(cand);
            } else if (report.contains("vp shunt") || report.contains("shunt catheter") || report.contains("shunt system")) {
                Map<String, Object> cand = new LinkedHashMap<>();
                cand.put("tile_id", tileId);
                cand.put("device_category", "ventriculoperitoneal shunt");
                cand.put("specific_type_if_visible", "VP shunt tubing and reservoir reservoir");
                cand.put("anatomical_location", bSite.equalsIgnoreCase("Skull") ? "enters right parietal skull skull, travels down neck." : "distal tip localized in peritoneal cavity");
                cand.put("laterality", "right");
                cand.put("confidence", "high");
                cand.put("visual_evidence", "continuous linear tubular radio-dense lines with crantiotomy insertion point");
                cand.put("needs_zoom_or_human_review", false);
                cand.put("reason_for_uncertainty", "catheter intact, cannot determine current flow or valve pressure setting without manual interrogation");
                candidates.add(cand);
            } else if (report.contains("arthroplasty") || report.contains("prosthesis") || report.contains("hip replacement")) {
                Map<String, Object> cand = new LinkedHashMap<>();
                cand.put("tile_id", tileId);
                cand.put("device_category", "orthopedic total joint arthroplasty");
                cand.put("specific_type_if_visible", "hip joint prosthesis, press-fit femoral stem and cup");
                cand.put("anatomical_location", "right acetabular and proximal femur articulation");
                cand.put("laterality", "right");
                cand.put("confidence", "high");
                cand.put("visual_evidence", "highly high-density metal femoral model and pelvic socket collar");
                cand.put("needs_zoom_or_human_review", false);
                cand.put("reason_for_uncertainty", "solid osteointegration, no lucencies");
                candidates.add(cand);
            } else if (report.contains("clip") || report.contains("clipping") || report.contains("aneurysm")) {
                Map<String, Object> cand = new LinkedHashMap<>();
                cand.put("tile_id", tileId);
                cand.put("device_category", "intracranial surgical clip");
                cand.put("specific_type_if_visible", "anterior communicating artery aneurysm clip");
                cand.put("anatomical_location", "sellar / suprasellar median skull area");
                cand.put("laterality", "medial");
                cand.put("confidence", "high");
                cand.put("visual_evidence", "starburst/metallic beam-hardening artifact with diagnostic head CT profile");
                cand.put("needs_zoom_or_human_review", true);
                cand.put("reason_for_uncertainty", "artifact restricts secondary evaluation. Clip material is unknown.");
                candidates.add(cand);
            } else if (report.contains("plate") || report.contains("screws") || report.contains("fixation")) {
                Map<String, Object> cand = new LinkedHashMap<>();
                cand.put("tile_id", tileId);
                cand.put("device_category", "orthopedic internal fixation hardware");
                cand.put("specific_type_if_visible", "tibial locking compression plate & 6 screws");
                cand.put("anatomical_location", "right proximal tibia shaft");
                cand.put("laterality", "right");
                cand.put("confidence", "high");
                cand.put("visual_evidence", "metallic compression bone plate secured with 6 visible cortical screws");
                cand.put("needs_zoom_or_human_review", false);
                cand.put("reason_for_uncertainty", "stable orthopedic steel, no backing out of screws");
                candidates.add(cand);
            } else {
                Map<String, Object> neg = new LinkedHashMap<>();
                neg.put("tile_id", tileId);
                neg.put("statement", "No obvious hardware or active medical implants detected in this " + bSite + " study.");
                neg.put("confidence", "medium");
                negatives.add(neg);
            }
        }

        ai.put("device_candidates", candidates);
        ai.put("negative_findings", negatives);
        return ai;
    }

    /**
     * Conducts a real HTTP call to Ollama `/api/chat` with structured prompt rules and contact layout (base64 image optional)
     */
    private Map<String, Object> callLiveOllamaForImplantDiscovery(List<Map<String, Object>> tiles, String model, String mode, String customPrompt) throws Exception {
        // System and User Prompts as requested in Section 8 of spec
        String systemPrompt = "You are assisting with visual identification of medical devices, implants, hardware, catheters, and foreign material on medical images.\n" +
                "You are not determining MRI safety, making a diagnosis, or replacing clinical review.\n" +
                "Only report visible devices or implants. Do not guess device manufacturer or MRI compatibility. If uncertain, say uncertain.\n" +
                "Response constraints:\n" +
                "1. Return structured JSON only.\n" +
                "2. NO <think> tags, NO thinking process, NO chain of thought. If you are a reasoning model like DeepSeek-R1, skip thinking/reasoning and start your response directly with the '{' character. Any extra text or reasoning tags will break the parser.\n" +
                "3. Keep descriptions very brief (few words) to maximize generation speed.";

        StringBuilder userPromptBuilder = new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(tiles) == null ? new StringBuilder() : new StringBuilder();
        userPromptBuilder.append("Review the provided medical images description list:\n")
                .append(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(tiles)).append("\n\n")
                .append("Each tile has an ID. Identify visible medical devices, implants, hardware, catheters, shunts, clips, coils, stents, prostheses, or foreign metallic material.\n")
                .append("For each finding, return a JSON object with this exact schema:\n")
                .append("{\n")
                .append("  \"review_quality\": \"adequate\",\n")
                .append("  \"device_candidates\": [\n")
                .append("    {\n")
                .append("      \"tile_id\": \"tile ID string\",\n")
                .append("      \"device_category\": \"catheter / cardiac implantable electronic device / VP shunt / etc.\",\n")
                .append("      \"specific_type_if_visible\": \"more specific type, manufacturer, or description if visible or mentioned\",\n")
                .append("      \"anatomical_location\": \"anatomical site details\",\n")
                .append("      \"laterality\": \"left / right / medial / bilateral\",\n")
                .append("      \"confidence\": \"high / medium / low\",\n")
                .append("      \"visual_evidence\": \"radiopaque wires, battery packs, orthopedic plates, etc.\",\n")
                .append("      \"needs_zoom_or_human_review\": true/false,\n")
                .append("      \"reason_for_uncertainty\": \"why you are uncertain\"\n")
                .append("    }\n")
                .append("  ],\n")
                .append("  \"negative_findings\": [\n")
                .append("    {\n")
                .append("      \"tile_id\": \"tile ID\",\n")
                .append("      \"statement\": \"reassurance sentence\",\n")
                .append("      \"confidence\": \"high/medium/low\"\n")
                .append("    }\n")
                .append("  ]\n")
                .append("}\n\n")
                .append("Do not report normal anatomy. Do not infer a device if it is not visible. Do not determine MRI safety.\n")
                .append("Return ONLY strict JSON representation without markdown wrapper. DO NOT think, DO NOT output any <think> tags or reasoning steps.");

        if (!customPrompt.isEmpty()) {
            userPromptBuilder.append("\n\nUser custom guidance: ").append(customPrompt);
        }

        ObjectNode chatRequest = objectMapper.createObjectNode();
        chatRequest.put("model", model);
        chatRequest.put("stream", false);
        chatRequest.put("format", "json");
        ObjectNode options = chatRequest.putObject("options");
        options.put("temperature", 0.0);
        options.put("num_predict", 1024);

        ArrayNode messages = chatRequest.putArray("messages");
        ObjectNode systemMsg = messages.addObject();
        systemMsg.put("role", "system");
        systemMsg.put("content", systemPrompt);
        ObjectNode userMsg = messages.addObject();
        userMsg.put("role", "user");
        userMsg.put("content", userPromptBuilder.toString());

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(trimTrailingSlash(ollamaBaseUrl) + "/api/chat"))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(chatRequest)))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException("Ollama returned " + response.statusCode());
        }

        JsonNode resNode = objectMapper.readTree(response.body());
        String textOutput = resNode.path("message").path("content").asText();

        JsonNode parsed = parseLenientJson(textOutput);
        if (parsed != null) {
            return objectMapper.convertValue(parsed, Map.class);
        }
        throw new RuntimeException("Failed to parse Ollama JSON response: " + textOutput);
    }

    private JsonNode parseLenientJson(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        String trimmed = content.trim();
        try {
            return objectMapper.readTree(trimmed);
        } catch (Exception ignored) {}
        int objStart = trimmed.indexOf('{');
        int arrStart = trimmed.indexOf('[');
        int start = (objStart < 0) ? arrStart : (arrStart < 0 ? objStart : Math.min(objStart, arrStart));
        char open = (start >= 0) ? trimmed.charAt(start) : '{';
        char close = (open == '[') ? ']' : '}';
        int end = trimmed.lastIndexOf(close);
        if (start >= 0 && end > start) {
            try {
                return objectMapper.readTree(trimmed.substring(start, end + 1));
            } catch (Exception ignored) {}
        }
        return null;
    }

    private ResponseEntity<Map<String, String>> errorResponse(HttpStatus statusCode, String msg) {
        Map<String, String> error = new HashMap<>();
        error.put("error", msg);
        return ResponseEntity.status(statusCode).body(error);
    }

    private static String trimTrailingSlash(String url) {
        return (url != null && url.endsWith("/")) ? url.substring(0, url.length() - 1) : url;
    }

    private List<StudyMetadata> generateGenericFallbackPriors() {
        List<StudyMetadata> list = new ArrayList<>();
        list.add(new StudyMetadata("prior-001", "2024-05-10", "CR", "Chest", "CHEST RADIOGRAPH 2 VIEWS", "Hospital Alpha",
                "Clear lung fields. No orthopedic hardware or pacemaker components detected.", ""));
        list.add(new StudyMetadata("prior-002", "2025-01-20", "CT", "Pelvis", "CT PELVIS WITHOUT CONTRAST", "Hospital Beta",
                "No hardware, degenerative changes of the cartilage joints.", ""));
        return list;
    }

    // =========================================================================
    // INNER CLASSES
    // =========================================================================

    public static class MockPatientCase {
        public String patientId;
        public String name;
        public String gender;
        public int age;
        public String defaultMode;
        public List<String> ehrDevices = new ArrayList<>();
        public List<StudyMetadata> studies = new ArrayList<>();

        public MockPatientCase(String patientId, String name, String gender, int age, String defaultMode) {
            this.patientId = patientId;
            this.name = name;
            this.gender = gender;
            this.age = age;
            this.defaultMode = defaultMode;
        }

        public void addEhrDevice(String name, String desc) {
            this.ehrDevices.add(name + ": " + desc);
        }

        public void addPriorStudy(String id, String date, String modality, String bodySite, String title, String facility, String conclusion, String imageResource) {
            this.studies.add(new StudyMetadata(id, date, modality, bodySite, title, facility, conclusion, imageResource));
        }
    }

    public static class StudyMetadata {
        public String id;
        public String date;
        public String modality;
        public String bodySite;
        public String title;
        public String facility;
        public String summaryOfConclusion;
        public String imageResource; // relative URL representation

        public StudyMetadata() {}

        public StudyMetadata(String id, String date, String modality, String bodySite, String title, String facility, String summaryOfConclusion, String imageResource) {
            this.id = id;
            this.date = date;
            this.modality = modality;
            this.bodySite = bodySite;
            this.title = title;
            this.facility = facility;
            this.summaryOfConclusion = summaryOfConclusion;
            this.imageResource = imageResource;
        }
    }

    public static class ScoredStudy {
        public StudyMetadata study;
        public int score;
        public int keywordBoost;

        public ScoredStudy(StudyMetadata study, int score, int keywordBoost) {
            this.study = study;
            this.score = score;
            this.keywordBoost = keywordBoost;
        }
    }

    public static class BudgetPolicy {
        public int maxStudiesReviewed;
        public int maxSeriesPerStudy;
        public int maxImagesBeforeVlm;
        public int maxContactSheetTiles;
        public int maxVlmCallsInitial;
        public int maxVlmCallsTotal;
        public boolean enableCtAxialEscalation;

        public BudgetPolicy() {}

        public BudgetPolicy(int maxStudies, int maxSeries, int maxImages, int maxTiles, int initialVlm, int totalVlm, boolean ctEscalation) {
            this.maxStudiesReviewed = maxStudies;
            this.maxSeriesPerStudy = maxSeries;
            this.maxImagesBeforeVlm = maxImages;
            this.maxContactSheetTiles = maxTiles;
            this.maxVlmCallsInitial = initialVlm;
            this.maxVlmCallsTotal = totalVlm;
            this.enableCtAxialEscalation = ctEscalation;
        }
    }
}

