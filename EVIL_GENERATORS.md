# EVIL generators (intentionally malformed DICOM)

This repo includes **EVIL** generators that intentionally produce **non-compliant** / **broken** KOS and MADO files.

They are **isolated** from the normal generators:
- main entry points: `be.uzleuven.ihe.dicom.creator.evil.EVILKOSCreator`, `be.uzleuven.ihe.dicom.creator.evil.EVILMADOCreator`
- helper utilities live under `be.uzleuven.ihe.dicom.creator.evil.*`

## Behavior

- **20% chance**: skip parts of the creation process (missing tags / missing sequences / missing SR tree pieces).
- **5% chance**: inject an explicit corruption (wrong values / incompatible tags).
- **30% chance**: add 1-5 forbidden DICOM tags that violate KOS/MADO specifications:
  - **CRITICAL violations**: Pixel Data, Waveform Data, Audio Data, Spectroscopy Data
  - **WARNING violations**: Image Pixel Module attributes (Rows, Columns, Bits Allocated, etc.)
  - **WARNING violations**: Image positioning (Image Position/Orientation Patient, Slice Location/Thickness)
  - **WARNING violations**: Acquisition parameters (KVP, Exposure Time, X-Ray Tube Current, etc.)
  - **WARNING violations**: Modality LUT (Rescale Intercept/Slope) and VOI LUT (Window Center/Width)

Optional reproducibility:
- Set a seed with any of the following system properties (checked in this order):
  - `-Devil.seed=123`
  - `-DEvil.seed=123`
  - `-DevilSeed=123`

## Build

```cmd
mvn -q clean package
```

Jar will be in `target\DICOMPolice-0.1.0-SNAPSHOT.jar`.

## Generate EVIL KOS

Generate 5 files:

```cmd
java -cp target\DICOMPolice-0.1.0-SNAPSHOT.jar be.uzleuven.ihe.dicom.creator.evil.EVILKOSCreator 5
```

Generate 5 files deterministically:

```cmd
java -cp target\DICOMPolice-0.1.0-SNAPSHOT.jar -Devil.seed=123 be.uzleuven.ihe.dicom.creator.evil.EVILKOSCreator 5
```

## Generate EVIL MADO

Generate 5 files:

```cmd
java -cp target\DICOMPolice-0.1.0-SNAPSHOT.jar be.uzleuven.ihe.dicom.creator.evil.EVILMADOCreator 5
```

Generate 5 files deterministically:

```cmd
java -cp target\DICOMPolice-0.1.0-SNAPSHOT.jar -Devil.seed=123 be.uzleuven.ihe.dicom.creator.evil.EVILMADOCreator 5
```

## Validation note

These EVIL files are expected to **fail** validation frequently by design.
You can validate a generated file using the existing CLI validator, e.g.:

```cmd
java -cp target\DICOMPolice-0.1.0-SNAPSHOT.jar be.uzleuven.ihe.dicom.validator.CLIDICOMVerify --profile IHEMADO -v EVIL_IHE_MADO_0_XXXXXX.dcm
```
