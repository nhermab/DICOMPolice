/*
 * Based on PixelMed's dciodvfy.cc (CompositeIOD concept).
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
import be.uzleuven.ihe.dicom.validator.model.ValidationResult;

/**
 * Base interface for all DICOM IOD validators.
 * Similar to CompositeIOD in dciodvfy.cc
 */
public interface IODValidator {

    /**
     * Get the name of this IOD.
     */
    String getIODName();

    /**
     * Check if this validator can handle the given DICOM object.
     */
    boolean canValidate(Attributes dataset);

    /**
     * Perform validation of the DICOM object.
     *
     * @param dataset The DICOM dataset to validate
     * @param verbose Include detailed validation messages
     * @return ValidationResult containing all errors, warnings, and info messages
     */
    ValidationResult validate(Attributes dataset, boolean verbose);

    /**
     * Perform validation with an optional profile name. Default implementation delegates
     * to the older validate(dataset, verbose) method for backwards compatibility. Implementors
     * may override this to provide profile-specific checks.
     *
     * @param dataset The DICOM dataset to validate
     * @param verbose Include detailed validation messages
     * @param profile Optional profile name (may be null)
     * @return ValidationResult containing all errors, warnings, and info messages
     */
    default ValidationResult validate(Attributes dataset, boolean verbose, String profile) {
        return validate(dataset, verbose);
    }

    /**
     * Check if this IOD is retired.
     */
    default boolean isRetired() {
        return false;
    }
}
