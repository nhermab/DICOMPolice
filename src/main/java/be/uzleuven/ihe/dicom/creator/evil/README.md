### full of bugs


This package is intentionally filled with bugs for testing purposes. Do not use it in production environments.


## Known Issues

right now it will never even closely create accurate KOS / MADO files due to incorrect handling of sequences, missing attributes, and wrong VRs.

etc i still need to fix it and bring it up to date with the latest valid creators ...

## Recent Updates Needed

**2025-12-22**: The IHEKOSSampleCreator has been updated with XDS-I.b compliance fixes:
- Added Retrieve Location UID (0040,E011) to Evidence Series (CRITICAL)
- Added IssuerOfPatientIDQualifiersSequence (0010,0024) with OID
- Added IssuerOfAccessionNumberSequence (0008,0051) in main header and ReferencedRequestSequence

EVILKOSCreator needs to be synchronized with these changes to properly test validator behavior.
