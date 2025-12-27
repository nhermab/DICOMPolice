package be.uzleuven.ihe.dicom.creator.scu.streaming;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;

import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * NDJSON writer for huge PACS crawls.
 * Writes one JSON object per line with a lightweight schema.
 * This keeps memory usage near-constant.
 */
public class NdjsonManifestStreamWriter implements be.uzleuven.ihe.dicom.creator.scu.streaming.ManifestStreamWriter {

    private final File outputFile;
    private final boolean overwrite;

    private BufferedWriter w;
    private boolean studyOpen = false;
    private boolean seriesOpen = false;

    public NdjsonManifestStreamWriter(File outputFile, boolean overwrite) {
        this.outputFile = outputFile;
        this.overwrite = overwrite;
    }

    public File getOutputFile() {
        return outputFile;
    }

    @Override
    public void openStudy(Attributes studyAttrs) throws IOException {
        ensureOpen();
        if (studyOpen) {
            throw new IOException("Study already open");
        }
        studyOpen = true;

        writeLine(obj(
            "type", "study",
            "studyInstanceUID", safe(studyAttrs.getString(Tag.StudyInstanceUID)),
            "patientID", safe(studyAttrs.getString(Tag.PatientID)),
            "patientName", safe(studyAttrs.getString(Tag.PatientName)),
            "studyDate", safe(studyAttrs.getString(Tag.StudyDate)),
            "studyTime", safe(studyAttrs.getString(Tag.StudyTime)),
            "accessionNumber", safe(studyAttrs.getString(Tag.AccessionNumber)),
            "studyDescription", safe(studyAttrs.getString(Tag.StudyDescription))
        ));
    }

    @Override
    public void openSeries(Attributes seriesAttrs) throws IOException {
        requireStudyOpen();
        if (seriesOpen) {
            // auto-close previous series
            closeSeries();
        }
        seriesOpen = true;

        writeLine(obj(
            "type", "series",
            "studyInstanceUID", safe(seriesAttrs.getString(Tag.StudyInstanceUID)),
            "seriesInstanceUID", safe(seriesAttrs.getString(Tag.SeriesInstanceUID)),
            "modality", safe(seriesAttrs.getString(Tag.Modality)),
            "seriesNumber", safe(seriesAttrs.getString(Tag.SeriesNumber)),
            "seriesDescription", safe(seriesAttrs.getString(Tag.SeriesDescription)),
            "seriesDate", safe(seriesAttrs.getString(Tag.SeriesDate)),
            "seriesTime", safe(seriesAttrs.getString(Tag.SeriesTime))
        ));
    }

    @Override
    public void writeInstance(Attributes instanceAttrs) throws IOException {
        requireStudyOpen();
        if (!seriesOpen) {
            throw new IOException("writeInstance called without openSeries");
        }

        writeLine(obj(
            "type", "instance",
            "studyInstanceUID", safe(instanceAttrs.getString(Tag.StudyInstanceUID)),
            "seriesInstanceUID", safe(instanceAttrs.getString(Tag.SeriesInstanceUID)),
            "sopClassUID", safe(instanceAttrs.getString(Tag.SOPClassUID)),
            "sopInstanceUID", safe(instanceAttrs.getString(Tag.SOPInstanceUID)),
            "instanceNumber", safe(instanceAttrs.getString(Tag.InstanceNumber)),
            "numberOfFrames", safe(instanceAttrs.getString(Tag.NumberOfFrames)),
            "rows", safe(instanceAttrs.getString(Tag.Rows)),
            "columns", safe(instanceAttrs.getString(Tag.Columns))
        ));
    }

    @Override
    public void closeSeries() throws IOException {
        if (!seriesOpen) return;
        writeLine(obj("type", "seriesEnd"));
        seriesOpen = false;
    }

    @Override
    public void closeStudy() throws IOException {
        if (!studyOpen) return;
        if (seriesOpen) {
            closeSeries();
        }
        writeLine(obj("type", "studyEnd"));
        studyOpen = false;
    }

    @Override
    public void close() throws IOException {
        try {
            closeStudy();
        } finally {
            if (w != null) {
                w.flush();
                w.close();
                w = null;
            }
        }
    }

    private void ensureOpen() throws IOException {
        if (w != null) return;

        if (outputFile.exists() && !overwrite) {
            throw new IOException("Output exists (use --overwrite): " + outputFile.getAbsolutePath());
        }
        File parent = outputFile.getParentFile();
        if (parent != null && !parent.exists()) {
            if (!parent.mkdirs()) {
                throw new IOException("Failed to create directory: " + parent.getAbsolutePath());
            }
        }

        w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputFile, false), StandardCharsets.UTF_8));
    }

    private void requireStudyOpen() throws IOException {
        if (!studyOpen) {
            throw new IOException("No open study");
        }
    }

    private void writeLine(String s) throws IOException {
        w.write(s);
        w.newLine();
        w.flush(); // deliberate: keep progress durable during huge crawls
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static String jsonEscape(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case '"': sb.append("\\\""); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

    private static String obj(String... kv) {
        // kv = key1, value1, key2, value2...
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        for (int i = 0; i < kv.length; i += 2) {
            if (i > 0) sb.append(',');
            sb.append('"').append(jsonEscape(kv[i])).append('"').append(':');
            sb.append('"').append(jsonEscape(kv[i + 1] == null ? "" : kv[i + 1])).append('"');
        }
        sb.append('}');
        return sb.toString();
    }
}
