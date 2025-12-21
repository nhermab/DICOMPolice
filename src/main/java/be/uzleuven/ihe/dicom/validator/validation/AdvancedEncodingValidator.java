package be.uzleuven.ihe.dicom.validator.validation;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.data.ElementDictionary;
import be.uzleuven.ihe.dicom.validator.model.ValidationResult;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Advanced encoding and character set validation for DICOM objects.
 * Implements strict checks for Specific Character Set, padding bytes, escape sequences,
 * and other byte-level encoding rules critical for IHE XDS-I Connectathon compliance.
 */
public class AdvancedEncodingValidator {

    private static final int ESCAPE_CHAR = 0x1B;
    private static final byte NULL_BYTE = 0x00;
    private static final byte SPACE_BYTE = 0x20;

    // ISO 2022 character sets that use escape sequences
    private static final Set<String> ISO_2022_CHARACTER_SETS = new HashSet<>(Arrays.asList(
        "ISO 2022 IR 13",  // Japanese (Katakana)
        "ISO 2022 IR 87",  // Japanese (Kanji)
        "ISO 2022 IR 159", // Japanese (Supplementary Kanji)
        "ISO 2022 IR 149", // Korean
        "ISO 2022 IR 58",  // Chinese (Simplified)
        "ISO 2022 IR 100", // Latin Alphabet No. 1
        "ISO 2022 IR 101", // Latin Alphabet No. 2
        "ISO 2022 IR 109", // Latin Alphabet No. 3
        "ISO 2022 IR 110", // Latin Alphabet No. 4
        "ISO 2022 IR 144", // Cyrillic
        "ISO 2022 IR 127", // Arabic
        "ISO 2022 IR 126", // Greek
        "ISO 2022 IR 138", // Hebrew
        "ISO 2022 IR 148", // Latin Alphabet No. 5 (Turkish)
        "ISO 2022 IR 166"  // Thai
    ));

    // UTF-8 character set identifier
    private static final String UTF8_CHARSET = "ISO_IR 192";

    // VRs that contain string data and must respect character set
    private static final Set<VR> TEXT_VRS = new HashSet<>(Arrays.asList(
        VR.PN, VR.LO, VR.SH, VR.ST, VR.UT, VR.LT, VR.AE, VR.AS, VR.CS, VR.DA, VR.DS, VR.DT, VR.IS, VR.TM
    ));

    /**
     * Validate Specific Character Set and encoding rules.
     */
    public static void validateCharacterSetEncoding(Attributes dataset, ValidationResult result, String path) {
        String[] specificCharSet = dataset.getStrings(Tag.SpecificCharacterSet);

        if (specificCharSet == null || specificCharSet.length == 0) {
            // If no SpecificCharacterSet, only default repertoire (ASCII) is allowed
            result.addInfo("SpecificCharacterSet not present, assuming default repertoire", path);

            // Check for high-bit characters when no SpecificCharacterSet is declared
            validateNoHighBitCharactersWithoutCharset(dataset, result, path);
            return;
        }

        // Check UTF-8 exclusivity rule
        validateUTF8Exclusivity(specificCharSet, result, path);

        // Check ISO 2022 escape sequences
        validateISO2022EscapeSequences(dataset, specificCharSet, result, path);
    }

    /**
     * Validate that no high-bit characters (non-ASCII) are present when SpecificCharacterSet is not declared.
     * Characters like 'É', 'ñ', etc. require explicit character set declaration.
     */
    private static void validateNoHighBitCharactersWithoutCharset(Attributes dataset, ValidationResult result, String path) {
        visitAllTextAttributes(dataset, new TextAttributeVisitor() {
            @Override
            public void visit(int tag, String attributeName, VR vr, byte[] bytes) {
                if (bytes == null || bytes.length == 0) {
                    return;
                }

                // Check for bytes with high bit set (> 0x7F)
                boolean hasHighBitChar = false;
                int firstHighBitPos = -1;
                byte firstHighBitValue = 0;

                for (int i = 0; i < bytes.length; i++) {
                    if ((bytes[i] & 0x80) != 0) { // Check if bit 7 is set (value > 127)
                        hasHighBitChar = true;
                        firstHighBitPos = i;
                        firstHighBitValue = bytes[i];
                        break;
                    }
                }

                if (hasHighBitChar) {
                    // Try to decode to show example character
                    String valueStr;
                    try {
                        valueStr = new String(bytes, StandardCharsets.ISO_8859_1).trim();
                        if (valueStr.length() > 50) {
                            valueStr = valueStr.substring(0, 50) + "...";
                        }
                    } catch (Exception e) {
                        valueStr = "<unable to decode>";
                    }

                    result.addError(String.format(
                        "High-bit character detected (byte 0x%02X at position %d) in %s %s, " +
                        "but SpecificCharacterSet (0008,0005) is not present. " +
                        "Extended characters (like 'É', 'ñ', etc.) require explicit character set declaration. " +
                        "Value sample: \"%s\"",
                        firstHighBitValue & 0xFF, firstHighBitPos, attributeName, tagString(tag), valueStr), path);
                }
            }
        });
    }

    /**
     * Validate UTF-8 exclusivity rule: if UTF-8 is declared, it should be the only value.
     */
    private static void validateUTF8Exclusivity(String[] specificCharSet, ValidationResult result, String path) {
        boolean hasUTF8 = false;
        boolean hasOthers = false;

        for (String charset : specificCharSet) {
            if (charset == null || charset.trim().isEmpty()) {
                continue;
            }
            if (UTF8_CHARSET.equals(charset.trim())) {
                hasUTF8 = true;
            } else {
                hasOthers = true;
            }
        }

        if (hasUTF8 && hasOthers) {
            result.addError("SpecificCharacterSet (0008,0005) contains ISO_IR 192 (UTF-8) " +
                          "combined with other character sets. UTF-8 must be the only value.", path);
        }
    }

    /**
     * Validate ISO 2022 escape sequences in text attributes.
     */
    private static void validateISO2022EscapeSequences(Attributes dataset, String[] specificCharSet,
                                                      ValidationResult result, String path) {
        // Build set of declared ISO 2022 character sets
        Set<String> declaredISO2022 = new HashSet<>();
        for (String charset : specificCharSet) {
            if (charset != null && charset.trim().startsWith("ISO 2022")) {
                declaredISO2022.add(charset.trim());
            }
        }

        // If no ISO 2022 character sets are declared, check that no escape sequences exist
        // If ISO 2022 is declared, we need to validate escape sequences
        visitAllTextAttributes(dataset, new TextAttributeVisitor() {
            @Override
            public void visit(int tag, String attributeName, VR vr, byte[] bytes) {
                if (bytes == null || bytes.length == 0) {
                    return;
                }

                boolean hasEscapeSequence = false;
                for (byte b : bytes) {
                    if (b == ESCAPE_CHAR) {
                        hasEscapeSequence = true;
                        break;
                    }
                }

                if (hasEscapeSequence) {
                    if (declaredISO2022.isEmpty()) {
                        result.addError("Attribute " + attributeName + " " + tagString(tag) +
                                      " contains escape sequences (0x1B) but no ISO 2022 character set " +
                                      "is declared in SpecificCharacterSet (0008,0005)", path);
                    } else {
                        // Found escape sequences and ISO 2022 is declared - this is expected
                        // A full implementation would parse the actual escape sequences
                        result.addInfo("Attribute " + attributeName + " " + tagString(tag) +
                                     " contains escape sequences (ISO 2022 encoding detected)", path);
                    }
                }
            }
        });
    }

    /**
     * Validate padding bytes for UI (Unique Identifier) values.
     * UIDs must be padded with NULL byte (0x00) if odd length.
     */
    public static void validateUIPadding(Attributes dataset, ValidationResult result, String path) {
        visitAllAttributes(dataset, new AttributeVisitor() {
            @Override
            public void visit(int tag, String attributeName, VR vr, byte[] bytes) {
                if (vr != VR.UI || bytes == null || bytes.length == 0) {
                    return;
                }

                // Check if length is odd (requires padding)
                if (bytes.length % 2 == 1) {
                    // Odd length UIDs must be padded with NULL in the actual encoding
                    // but dcm4che typically handles this. We check if the UID string has trailing space
                    String uidValue = dataset.getString(tag);
                    if (uidValue != null && uidValue.endsWith(" ")) {
                        result.addError("UID attribute " + attributeName + " " + tagString(tag) +
                                      " is incorrectly padded with SPACE (0x20). UIDs must be padded " +
                                      "with NULL (0x00) if odd length.", path);
                    }
                }

                // Check for space padding in the byte array (last byte)
                if (bytes.length > 0 && bytes[bytes.length - 1] == SPACE_BYTE) {
                    result.addError("UID attribute " + attributeName + " " + tagString(tag) +
                                  " has SPACE byte (0x20) padding. UIDs must be padded with NULL (0x00).", path);
                }
            }
        });
    }

    /**
     * Validate padding bytes for CS/SH/LO values.
     * These must be padded with SPACE byte (0x20), not NULL.
     */
    public static void validateTextPadding(Attributes dataset, ValidationResult result, String path) {
        visitAllAttributes(dataset, new AttributeVisitor() {
            @Override
            public void visit(int tag, String attributeName, VR vr, byte[] bytes) {
                if ((vr != VR.CS && vr != VR.SH && vr != VR.LO && vr != VR.ST && vr != VR.LT && vr != VR.UT)
                    || bytes == null || bytes.length == 0) {
                    return;
                }

                // Check if padded with NULL instead of SPACE
                if (bytes.length > 0 && bytes[bytes.length - 1] == NULL_BYTE) {
                    result.addError("Text attribute " + attributeName + " " + tagString(tag) +
                                  " (" + vr + ") is padded with NULL (0x00). " +
                                  "Text VRs must be padded with SPACE (0x20).", path);
                }
            }
        });
    }

    /**
     * Visit all attributes in the dataset and sequences.
     */
    private static void visitAllAttributes(Attributes dataset, AttributeVisitor visitor) {
        int[] tags = dataset.tags();
        for (int tag : tags) {
            VR vr = dataset.getVR(tag);
            if (vr == null) {
                continue;
            }

            String attributeName = ElementDictionary.getStandardElementDictionary().keywordOf(tag);
            if (attributeName == null) {
                attributeName = "Unknown";
            }

            if (vr == VR.SQ) {
                // Recursively visit sequence items
                org.dcm4che3.data.Sequence seq = dataset.getSequence(tag);
                if (seq != null) {
                    for (Attributes item : seq) {
                        visitAllAttributes(item, visitor);
                    }
                }
            } else {
                try {
                    byte[] bytes = dataset.getBytes(tag);
                    visitor.visit(tag, attributeName, vr, bytes);
                } catch (Exception e) {
                    // Ignore errors reading bytes
                }
            }
        }
    }

    /**
     * Visit all text attributes in the dataset.
     */
    private static void visitAllTextAttributes(Attributes dataset, TextAttributeVisitor visitor) {
        visitAllAttributes(dataset, new AttributeVisitor() {
            @Override
            public void visit(int tag, String attributeName, VR vr, byte[] bytes) {
                if (TEXT_VRS.contains(vr)) {
                    visitor.visit(tag, attributeName, vr, bytes);
                }
            }
        });
    }

    /**
     * Format tag as string (gggg,eeee).
     */
    private static String tagString(int tag) {
        int group = (tag >>> 16) & 0xFFFF;
        int element = tag & 0xFFFF;
        return String.format("(%04X,%04X)", group, element);
    }

    /**
     * Visitor interface for attributes.
     */
    private interface AttributeVisitor {
        void visit(int tag, String attributeName, VR vr, byte[] bytes);
    }

    /**
     * Visitor interface for text attributes.
     */
    private interface TextAttributeVisitor {
        void visit(int tag, String attributeName, VR vr, byte[] bytes);
    }
}

