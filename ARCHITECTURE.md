# DICOMPolice Architecture

This document describes the architecture, design patterns, and component organization of the DICOMPolice validator.

---

## Table of Contents

- [Overview](#overview)
- [Design Principles](#design-principles)
- [Component Architecture](#component-architecture)
- [Package Structure](#package-structure)
- [Validation Pipeline](#validation-pipeline)
- [Extension Points](#extension-points)
- [Data Flow](#data-flow)
- [Key Design Patterns](#key-design-patterns)

---

## Overview

DICOMPolice is a modular DICOM validator designed around the concept of **composable validators** that can be combined to validate different profiles and specifications. The architecture separates concerns:

- **IOD Validation**: Information Object Definition compliance
- **Profile Validation**: IHE-specific requirements (XDS-I.b, MADO)
- **Encoding Validation**: Low-level DICOM encoding rules
- **Structural Validation**: SR content tree and template validation
- **Semantic Validation**: Cross-attribute consistency and business rules

---

## Design Principles

### 1. **Single Responsibility**
Each validator class has one specific validation concern (e.g., timezone validation, TID 1600 structure, encoding rules).

### 2. **Open/Closed Principle**
New validators can be added without modifying existing code. The `IODValidatorFactory` uses a strategy pattern to select validators.

### 3. **Composition Over Inheritance**
Validators compose specialized validation utilities rather than deep inheritance hierarchies.

### 4. **Fail-Fast with Accumulation**
Validators accumulate all errors/warnings/info messages into a `ValidationResult` object rather than throwing exceptions on first error. This allows users to see all issues at once.

### 5. **Profile-Driven Validation**
The same DICOM file can be validated against different profiles (e.g., base KOS, XDS-I.b, MADO) with profile-specific requirements.

---

## Component Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    CLI Entry Point                          │
│              (CLIDICOMVerify.java)                          │
│  - Argument parsing                                         │
│  - File I/O                                                 │
│  - Result formatting                                        │
└─────────────────────┬───────────────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────────────┐
│              Part10FileValidator                            │
│  - DICOM Part 10 file format validation                     │
│  - Meta Information validation                              │
│  - Transfer Syntax checking                                 │
└─────────────────────┬───────────────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────────────┐
│              IODValidatorFactory                            │
│  - Inspects SOPClassUID                                     │
│  - Selects appropriate validator                            │
│  - Returns specific IODValidator instance                   │
└─────────────────────┬───────────────────────────────────────┘
                      │
          ┌───────────┴───────────┐
          ▼                       ▼
┌────────────────────┐  ┌──────────────────────┐
│ KOS Validator      │  │ MADO Validator       │
│ (Base)             │  │ (Extends KOS)        │
└─────────┬──────────┘  └──────────┬───────────┘
          │                        │
          │  Both use:             │
          └────────┬───────────────┘
                   │
                   ▼
┌─────────────────────────────────────────────────────────────┐
│               Specialized Validators                        │
│  ┌────────────────────────────────────────────────────┐    │
│  │ KeyObjectModuleValidator                           │    │
│  │  - Patient IE, Study IE, Series IE, Equipment IE   │    │
│  │  - SR Document Content Module                      │    │
│  └────────────────────────────────────────────────────┘    │
│  ┌────────────────────────────────────────────────────┐    │
│  │ AdvancedEncodingValidator                          │    │
│  │  - Character set validation                        │    │
│  │  - UI padding (null byte checks)                   │    │
│  │  - Text padding (space byte checks)                │    │
│  └────────────────────────────────────────────────────┘    │
│  ┌────────────────────────────────────────────────────┐    │
│  │ TimezoneValidator                                  │    │
│  │  - Timezone offset format                          │    │
│  │  - Content time consistency                        │    │
│  └────────────────────────────────────────────────────┘    │
│  ┌────────────────────────────────────────────────────┐    │
│  │ TID1600Validator (MADO-specific)                   │    │
│  │  - Image Library container structure               │    │
│  │  - Study/Series/Instance descriptors               │    │
│  │  - Code sequence validation                        │    │
│  └────────────────────────────────────────────────────┘    │
│  ┌────────────────────────────────────────────────────┐    │
│  │ EvidenceOrphanValidator                            │    │
│  │  - Detects unreferenced evidence                   │    │
│  └────────────────────────────────────────────────────┘    │
│  ┌────────────────────────────────────────────────────┐    │
│  │ DigitalSignatureValidator                          │    │
│  │  - Signature presence and structure                │    │
│  └────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────┘
```

---

## Package Structure

```
be.uzleuven.ihe.dicom/
├── constants/
│   ├── DicomConstants.java       # DICOM UIDs, tags, common values
│   └── XDSConstants.java         # XDS/IHE specific constants
│
├── creator/
│   ├── IHEKOSSampleCreator.java  # Generates XDS-I.b KOS samples
│   ├── IHEMADOSampleCreator.java # Generates MADO manifest samples
│   └── GenerateAndValidateMado.java  # Combined creation/validation
│
├── validator/
│   ├── CLIDICOMVerify.java       # CLI entry point
│   │
│   ├── model/
│   │   └── ValidationResult.java # Accumulates errors/warnings/info
│   │
│   ├── utils/                    # Profile-specific utilities
│   │   ├── KeyObjectContentUtils.java
│   │   ├── MADOContentUtils.java
│   │   ├── MADOProfileUtils.java
│   │   ├── SRContentTreeUtils.java
│   │   ├── SRReferenceUtils.java
│   │   └── XDSIManifestProfileUtils.java
│   │
│   └── validation/               # Validator implementations
│       ├── Part10FileValidator.java
│       ├── AdvancedEncodingValidator.java
│       ├── AdvancedStructureValidator.java
│       ├── DigitalSignatureValidator.java
│       ├── EvidenceOrphanValidator.java
│       ├── TimezoneValidator.java
│       ├── MADOAppendixBValidator.java
│       ├── MADORetrievalValidator.java
│       ├── MADOTemplateValidator.java
│       ├── MADOTimezoneValidator.java
│       │
│       ├── iod/                  # IOD-level validators
│       │   ├── AbstractIODValidator.java
│       │   ├── IODValidator.java
│       │   ├── IODValidatorFactory.java
│       │   ├── KeyObjectModuleValidator.java
│       │   ├── KeyObjectProfileValidator.java
│       │   ├── KeyObjectSelectionValidator.java
│       │   └── MADOManifestValidator.java
│       │
│       └── tid1600/              # TID 1600 specific validators
│           ├── TID1600Codes.java
│           ├── TID1600ImageLibraryValidator.java
│           ├── TID1600RootValidator.java
│           ├── TID1600Rules.java
│           ├── TID1600StudyValidator.java
│           └── TID1600Validator.java
```

---

## Validation Pipeline

### 1. **File-Level Validation**

```
Input File → Part10FileValidator
  ↓
  • Validate DICOM prefix (128-byte preamble + "DICM")
  • Validate File Meta Information
  • Check Transfer Syntax
  • Verify SOPClassUID consistency
```

### 2. **IOD Selection**

```
Dataset → IODValidatorFactory
  ↓
  • Read SOPClassUID
  • Select appropriate validator:
    - KeyObjectSelectionValidator (base)
    - MADOManifestValidator (extended)
```

### 3. **IE Module Validation**

```
Validator → Module Validators
  ↓
  • Patient IE (Type 1/2/3 attributes)
  • Study IE
  • Series IE
  • Equipment IE
  • SR Document Content Module
```

### 4. **Advanced Validation**

```
Dataset → Specialized Validators
  ↓
  • Encoding (character sets, padding)
  • Timezone consistency
  • Digital signatures
  • Content tree structure
```

### 5. **Profile-Specific Validation**

```
Dataset + Profile → Profile Validators
  ↓
  • XDS-I.b: XDSIManifestProfileUtils
  • MADO: MADOProfileUtils → TID1600Validator
```

### 6. **Result Accumulation**

```
All Validators → ValidationResult
  ↓
  • Errors (critical failures)
  • Warnings (should fix)
  • Info (recommendations)
  ↓
Exit Code: 0 (success) or 1 (failure)
```

---

## Extension Points

### Adding a New Validator

1. **Create Validator Class**:
   ```java
   public class MyCustomValidator {
       public static void validate(Attributes dataset, 
                                   ValidationResult result, 
                                   String modulePath) {
           // Validation logic
           if (error) {
               result.addError("Error message", modulePath);
           }
       }
   }
   ```

2. **Integrate into Pipeline**:
   ```java
   // In appropriate IODValidator subclass
   MyCustomValidator.validate(dataset, result, "ModuleName");
   ```

### Adding a New Profile

1. **Create Profile Constants**:
   ```java
   public static final String PROFILE_MY_PROFILE = "MyProfile";
   ```

2. **Implement Profile Logic**:
   ```java
   if (PROFILE_MY_PROFILE.equalsIgnoreCase(profile)) {
       // Profile-specific validation
   }
   ```

3. **Register in Factory**:
   Update `IODValidatorFactory` or `KeyObjectProfileValidator` to recognize the new profile.

### Adding a New IOD

1. **Create IODValidator Subclass**:
   ```java
   public class MyIODValidator extends AbstractIODValidator {
       @Override
       public boolean canValidate(Attributes dataset) {
           return MY_SOP_CLASS_UID.equals(
               dataset.getString(Tag.SOPClassUID));
       }
       
       @Override
       public ValidationResult validate(Attributes dataset, 
                                       boolean verbose) {
           // IOD-specific validation
       }
   }
   ```

2. **Register in Factory**:
   ```java
   // In IODValidatorFactory
   if (myValidator.canValidate(dataset)) {
       return myValidator;
   }
   ```

---

## Data Flow

### Validation Flow

```
┌──────────┐
│ DICOM    │
│ File     │
└────┬─────┘
     │
     ▼
┌──────────────────────┐
│ DicomInputStream     │
│ (dcm4che3)           │
└────┬─────────────────┘
     │
     ▼
┌──────────────────────┐
│ Attributes           │
│ (in-memory dataset)  │
└────┬─────────────────┘
     │
     ▼
┌──────────────────────┐
│ Validators           │
│ (iterate attributes) │
└────┬─────────────────┘
     │
     ▼
┌──────────────────────┐
│ ValidationResult     │
│ (accumulated)        │
└────┬─────────────────┘
     │
     ▼
┌──────────────────────┐
│ Console Output       │
│ (formatted)          │
└──────────────────────┘
```

### Creation Flow

```
┌──────────────────────┐
│ Creator Main         │
└────┬─────────────────┘
     │
     ▼
┌──────────────────────┐
│ Attributes Builder   │
│ (construct dataset)  │
└────┬─────────────────┘
     │
     ▼
┌──────────────────────┐
│ DicomOutputStream    │
│ (dcm4che3)           │
└────┬─────────────────┘
     │
     ▼
┌──────────────────────┐
│ DICOM File           │
└──────────────────────┘
```

---

## Key Design Patterns

### 1. **Strategy Pattern**
- `IODValidatorFactory` selects validation strategy based on SOPClassUID
- Different validators implement `IODValidator` interface

### 2. **Template Method Pattern**
- `AbstractIODValidator` defines common validation structure
- Subclasses override specific validation steps

### 3. **Composite Pattern**
- `ValidationResult` aggregates multiple validation results
- Validators call other validators and merge results

### 4. **Builder Pattern**
- Sample creators build complex DICOM datasets incrementally
- `Attributes` and `Sequence` use builder-style APIs

### 5. **Facade Pattern**
- `MADOProfileUtils` provides simplified interface to complex TID 1600 validation
- Hides complexity of multiple specialized validators

### 6. **Utility/Helper Pattern**
- Static utility classes (e.g., `SRContentTreeUtils`) provide reusable functions
- No state, pure functions for common operations

---

## Key Classes

### ValidationResult

Accumulates validation findings:

```java
public class ValidationResult {
    private List<String> errors;
    private List<String> warnings;
    private List<String> info;
    
    public void addError(String message, String location);
    public void addWarning(String message, String location);
    public void addInfo(String message);
    public void merge(ValidationResult other);
    public boolean isValid(); // true if no errors
}
```

### AbstractIODValidator

Base class for IOD validators:

```java
public abstract class AbstractIODValidator {
    protected abstract boolean canValidate(Attributes dataset);
    protected abstract ValidationResult validate(
        Attributes dataset, boolean verbose);
    
    // Helper methods for common checks
    protected void checkStringValue(...);
    protected void checkSequencePresence(...);
    protected void checkType1Attribute(...);
    protected void checkType2Attribute(...);
}
```

### TID1600Validator

Validates MADO TID 1600 Image Library structure:

```java
public class TID1600Validator {
    public static void validateTID1600Structure(
        Attributes dataset, 
        ValidationResult result,
        String modulePath, 
        boolean verbose) {
        
        // Find Image Library container
        // Validate study descriptors
        // Validate series descriptors
        // Cross-validate with evidence
    }
}
```

---

## Threading and Performance

### Current Design
- **Single-threaded**: Validation runs sequentially
- **Memory-efficient**: Uses dcm4che3's lazy loading for large sequences
- **No caching**: Each validation is independent

---

## Error Handling

### Validation Errors
- **Errors**: Critical failures that make the file non-compliant
- **Warnings**: Issues that should be fixed but don't prevent use
- **Info**: Recommendations and informational messages

### System Errors
- File I/O exceptions: Caught and reported with meaningful messages
- Parsing errors: dcm4che3 exceptions caught and wrapped
- Invalid arguments: Detected early with usage help

### Error Formatting
```
Error: PatientID (0010,0020) is missing/empty. [Patient]
Warning: Timezone offset format invalid: should be ±HHMM [Timezone]
Info: Transfer Syntax: Explicit VR Little Endian (recommended) [FileMetaInfo]
```

---

## Testing Strategy

### Integration Tests
- Full validation pipeline
- Sample creator output validation
- Profile-specific scenarios

### Conformance Tests
- Known-good DICOM files (from vendors)
- IHE Connectathon samples
- Intentionally malformed files

---

## Future Architecture Considerations

### Planned Enhancements
3. **REST API**: HTTP service for validation to plug into gazelle
5. **Report Generation**: HTML/PDF validation reports

### Scalability
- Currently suitable for single-file, on-demand validation
- For high-volume scenarios, consider:
  - Connection pooling (if adding network features)
  - Streaming validation (for very large files)
  - Distributed validation (for large batches)

---

## References

- DICOM Standard PS3.3 (Information Object Definitions)
- DICOM Standard PS3.10 (Media Storage and File Format)
- IHE Radiology Technical Framework - XDS-I.b
- IHE MADO Profile (Public Comment)
- dcm4che3 Documentation: https://dcm4che.org/

