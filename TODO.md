# DICOMPolice - TODO

This document tracks planned features, enhancements, and technical debt for the DICOMPolice validator.

---

## High Priority

### MADO Profile Finalization
- [ ] Monitor IHE Public Comment period for MADO profile updates
- [ ] Update code values when final specification is published
- [ ] Replace provisional "ddd" prefix codes with final codes
- [ ] Validate against final MADO Connectathon test suite
- [ ] Update documentation with final specification references

### Comprehensive Testing
- [ ] Create unit test suite for all validators
- [ ] Add integration tests for complete validation pipeline
- [ ] Test with diverse vendor-generated KOS/MADO files
- [ ] Create test fixtures with known-good and known-bad examples
- [ ] Add regression tests for reported bugs
- [ ] Performance benchmarking for large manifests

### Documentation Improvements
- [ ] Add Javadoc to all public APIs
- [ ] Create user guide with detailed validation examples
- [ ] Document all error codes and their meanings
- [ ] Add troubleshooting guide for common issues
- [ ] Create video tutorials for usage

---

## Medium Priority

### Validation Enhancements

#### Profile Support
- [ ] Add support for IHE XDS-I.c (if/when published)
- [ ] Support FHIR ImagingManifest validation (for IHE MHD integration)
- [ ] Add custom profile definition via configuration files
- [ ] Implement profile versioning support

#### Advanced Validation Features
- [ ] Deep SR Content Tree validation with relationship checks
- [ ] Enhanced UID uniqueness validation across multiple files
- [ ] Cross-reference validation for multi-document submissions
- [ ] Validate against DICOM Conformance Statements
- [ ] Add support for validating signed documents (verify signatures)
- [ ] Implement SR template library (more TIDs beyond 1600/2010)

#### Encoding and Interoperability
- [ ] Add support for validating character set conversions
- [ ] Validate private attributes (with configurable private dictionaries)
- [ ] Support for validating retired/legacy attributes
- [ ] Enhanced transfer syntax validation (compression formats)
- [ ] Validate VR (Value Representation) correctness per data dictionary

### Output and Reporting

- [ ] HTML validation report generation
- [ ] JSON/XML output format for automated processing
- [ ] Severity levels for warnings (minor/major)
- [ ] Configurable validation rules (enable/disable specific checks)
- [ ] Summary statistics (total errors/warnings/files)
- [ ] Comparison reports (before/after validation)
- [ ] Export validation results to CSV

### Sample Creators

- [ ] Add more diversity to generated samples (edge cases)
- [ ] Create intentionally malformed samples for testing validators
- [ ] Add command-line options for customizing generated samples
- [ ] Create sample generator for other IODs (CT, MR, etc.)
- [ ] Generate samples with various character sets
- [ ] Add digital signature to generated samples (optional)

---

## Lower Priority

### User Interface

- [ ] Web-based UI for drag-and-drop file validation
- [ ] GUI desktop application (JavaFX or Swing)
- [ ] Real-time validation feedback as file uploads
- [ ] Batch validation with progress bar
- [ ] Interactive error exploration (click error to see location)

### Developer Tools

- [ ] Maven plugin for CI/CD integration
- [ ] Gradle plugin support
- [ ] Pre-commit hooks for validating DICOM files in repos
- [ ] IDE plugin (IntelliJ IDEA, Eclipse)
- [ ] Docker container for validation service
- [ ] REST API endpoint for remote validation

### Performance Optimizations

- [ ] Parallel validation of multiple files
- [ ] Streaming validation for very large files (GB+)
- [ ] Caching of parsed DICOM structures
- [ ] Memory profiling and optimization
- [ ] Lazy loading of validation rules
- [ ] Incremental validation (validate only changed attributes)

### Integration

- [ ] Integration with PACS systems
- [ ] XDS.b Registry validation workflow integration
- [ ] Integrate with IHE Gazelle test tools
- [ ] Support for WADO-RS retrieval + validation
- [ ] Integration with dcm4chee PACS
- [ ] Plugin system for third-party validators

---

## Technical Debt

### Code Quality

- [ ] Refactor large validator classes into smaller components
- [ ] Extract magic strings to constants
- [ ] Improve error message consistency
- [ ] Add more comprehensive code comments
- [ ] Reduce code duplication in validators
- [ ] Standardize naming conventions across packages
- [ ] Extract repeated validation patterns to helper methods

### Build and Dependency Management

- [ ] Set up continuous integration (GitHub Actions, Jenkins)
- [ ] Add code coverage reporting (JaCoCo)
- [ ] Set up static analysis (SpotBugs, PMD, Checkstyle)
- [ ] Add dependency vulnerability scanning
- [ ] Create release automation scripts
- [ ] Add versioned API documentation generation
- [ ] Consider modularization (Java 9+ modules)

### Testing Infrastructure

- [ ] Set up test data repository (de-identified samples)
- [ ] Add mutation testing
- [ ] Create mock DICOM objects for unit testing
- [ ] Add property-based testing (jqwik)
- [ ] Load testing for batch validation
- [ ] Add test fixtures from IHE Connectathon results

---

## Research and Exploration

### Future Standards Support

- [ ] Research DICOM Supplement 222 (Unified Procedure Step)
- [ ] Investigate FHIR R5 ImagingSelection validation
- [ ] Explore AI-assisted validation rule generation
- [ ] Study emerging IHE profiles (IOCM, AIR, etc.)
- [ ] Investigate DICOM-SR to FHIR conversion validation

### Advanced Features

- [ ] Natural language explanations of validation errors
- [ ] Machine learning for detecting common error patterns
- [ ] Automatic repair suggestions for common issues
- [ ] Interactive wizard for creating manifests
- [ ] Visual diff tool for comparing DICOM files
- [ ] Timeline visualization for study/series relationships

### Compliance and Certification

- [ ] Pursue IHE vendor testing/certification
- [ ] Document FDA/CE compliance considerations
- [ ] Create DICOM Conformance Statement generator
- [ ] Add support for NEMA WG-06 conformance testing
- [ ] Explore integration with CTP (RSNA Clinical Trial Processor)

---

## Community and Outreach

- [ ] Present at IHE Connectathon
- [ ] Submit to DICOM Working Groups for feedback
- [ ] Create tutorial videos on YouTube
- [ ] Write blog posts about common DICOM issues
- [ ] Contribute improvements back to dcm4che
- [ ] Engage with PACS/VNA vendor community
- [ ] Create educational materials for DICOM beginners

---

## Completed

- [x] Basic KOS validation
- [x] MADO TID 1600 validation (Approach 2)
- [x] CLI interface with verbose output
- [x] Sample KOS creator
- [x] Sample MADO creator
- [x] Timezone validation
- [x] Character set encoding validation
- [x] Part 10 file format validation
- [x] Evidence orphan detection
- [x] Profile-based validation (IHEXDSIManifest, IHEMADO)
- [x] Initial documentation (README, THIRD_PARTY_NOTICES, LICENSE)

---

## Version Roadmap

### v0.2.0 (Near-term)
- HTML report generation
- JSON output format
- Batch file validation
- Bug fixes from initial feedback

### v0.3.0 (Mid-term)
- Web UI
- REST API
- Docker container
- CI/CD integration
- MADO profile finalization (once published)

### v1.0.0 (Long-term)
- Production-ready stability
- Comprehensive test coverage
- Full IHE XDS-I.b + MADO compliance
- Documentation complete
- Performance optimized
- Community adoption

---

## Contributing

Want to help? Check out our [Contributing Guide](CONTRIBUTING.md) (to be created) and:

1. Pick an item from this TODO list
2. Open an issue to discuss your approach
3. Submit a pull request with your implementation
4. Update this TODO to mark items complete

**Priority areas needing help:**
- Writing unit tests
- Creating sample DICOM files for testing
- Documentation and tutorials
- Testing with real-world vendor files
- Performance optimization

---

## Notes

- Items marked with [ ] are not started
- Items marked with [x] are completed
- Priority levels are suggestions and may change
- This list is living document - expect frequent updates
- Many items depend on MADO profile finalization

**Last Updated**: 2024-12-21

