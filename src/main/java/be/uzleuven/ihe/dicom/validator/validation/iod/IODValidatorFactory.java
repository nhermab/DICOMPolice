/*
 * Based on PixelMed's dciodvfy.cc (selectCompositeIOD logic).
 *
 * PixelMed Copyright (c) 1993-2024, David A. Clunie DBA PixelMed Publishing.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 *    conditions and the following disclaimers.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 *    conditions and the following disclaimers in the documentation and/or other materials
 *    provided with the distribution.
 *
 * 3. Neither the name of PixelMed Publishing nor the names of its contributors may
 *    be used to endorse or promote products derived from this software.
 *
 * This software is provided by the copyright holders and contributors "as is" and any
 * express or implied warranties, including, but not limited to, the implied warranties
 * of merchantability and fitness for a particular purpose are disclaimed. In no event
 * shall the copyright owner or contributors be liable for any direct, indirect, incidental,
 * special, exemplary, or consequential damages (including, but not limited to, procurement
 * of substitute goods or services; loss of use, data or profits; or business interruption)
 * however caused and on any theory of liability, whether in contract, strict liability, or
 * tort (including negligence or otherwise) arising in any way out of the use of this software,
 * even if advised of the possibility of such damage.
 *
 * This software has neither been tested nor approved for clinical use or for incorporation in
 * a medical device. It is the redistributor's or user's responsibility to comply with any
 * applicable local, state, national or international regulations.
 *
 * Note: This project as a whole is distributed under GPLv2 (to comply with DCM4CHE).
 * The PixelMed-derived portions in this file remain under the PixelMed license above;
 * when redistributing, you must satisfy both the GPLv2 terms and the PixelMed notice.
 */

package be.uzleuven.ihe.dicom.validator.validation.iod;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;

import java.util.ArrayList;
import java.util.List;

/**
 * Factory for selecting appropriate IOD validator based on SOPClassUID and profile.
 * Similar to selectCompositeIOD in dciodvfy.cc
 */
public class IODValidatorFactory {

    private static final List<IODValidator> validators = new ArrayList<>();

    static {
        // Register all available validators
        // Order matters for validators with same SOP Class UID
        validators.add(new MADOManifestValidator());
        validators.add(new KeyObjectSelectionValidator());
        // Add more validators here as they are implemented
    }

    /**
     * Select appropriate validator for the given DICOM dataset with profile awareness.
     * When a profile is specified, prefer validators that match that profile.
     *
     * @param dataset DICOM dataset to validate
     * @param profile Validation profile (e.g., "IHEMADO", "IHEXDSIManifest")
     * @return IODValidator if found, null otherwise
     */
    public static IODValidator selectValidator(Attributes dataset, String profile) {
        if (dataset == null) {
            return null;
        }

        // If profile is specified, prioritize profile-specific validators
        if (profile != null && !profile.isEmpty()) {
            // Check for MADO profile first - it's more specific than generic KOS
            if ("IHEMADO".equalsIgnoreCase(profile)) {
                IODValidator madoValidator = new MADOManifestValidator();
                if (madoValidator.canValidate(dataset)) {
                    return madoValidator;
                }
            }

            // Check for XDS-I Manifest profile
            if ("IHEXDSIManifest".equalsIgnoreCase(profile)) {
                IODValidator kosValidator = new KeyObjectSelectionValidator();
                if (kosValidator.canValidate(dataset)) {
                    return kosValidator;
                }
            }
        }

        // Fall back to standard validator selection
        for (IODValidator validator : validators) {
            if (validator.canValidate(dataset)) {
                return validator;
            }
        }

        return null;
    }

    /**
     * Select appropriate validator for the given DICOM dataset.
     * Uses default profile-agnostic selection.
     *
     * @param dataset DICOM dataset to validate
     * @return IODValidator if found, null otherwise
     */
    public static IODValidator selectValidator(Attributes dataset) {
        return selectValidator(dataset, null);
    }

    /**
     * Get the SOP Class UID from dataset.
     */
    public static String getSOPClassUID(Attributes dataset) {
        return dataset != null ? dataset.getString(Tag.SOPClassUID) : null;
    }

    /**
     * Get list of all registered validators.
     */
    public static List<IODValidator> getRegisteredValidators() {
        return new ArrayList<>(validators);
    }
}
