package dev.ryan.pdftexttab;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.core.ByteArray;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.HttpHeader;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.ui.Selection; // NOTE: Selection here (not ui.editor)
import burp.api.montoya.ui.editor.extension.ExtensionProvidedHttpResponseEditor;

import javax.swing.*;
import java.awt.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

/**
 * A read-only response viewer that extracts and displays text from:
 *  - PDFs (application/pdf, application/x-pdf, etc.)
 *  - ZIPs containing PDFs (application/zip): concatenates all PDF texts
 *
 * Appears as a tab in Burp’s HTTP response editor.
 */
final class PdfTextViewerTab implements ExtensionProvidedHttpResponseEditor {

    private final JTextArea textArea;
    private HttpResponse currentResponse;

    PdfTextViewerTab(MontoyaApi api) {
        textArea = new JTextArea();
        textArea.setEditable(false);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
    }

    // Enable this editor only for PDF or ZIP (by response Content-Type).
    @Override
    public boolean isEnabledFor(HttpRequestResponse requestResponse) {
        HttpResponse resp = (requestResponse == null) ? null : requestResponse.response();
        if (resp == null) return false;

        String ct = contentTypeHeader(resp.headers());
        if (ct == null) return false;

        ct = ct.toLowerCase(Locale.ROOT);
        return ct.contains("application/pdf")
            || ct.contains("application/x-pdf")
            || ct.contains("application/zip");
    }

    @Override
    public String caption() {
        return "PDF Text";
    }

    @Override
    public Component uiComponent() {
        return new JScrollPane(textArea);
    }

    @Override
    public void setRequestResponse(HttpRequestResponse requestResponse) {
        textArea.setText("");
        currentResponse = (requestResponse == null) ? null : requestResponse.response();
        if (currentResponse == null) return;

        String ct = contentTypeHeader(currentResponse.headers());
        String display;
        try {
            if (ct != null && ct.toLowerCase(Locale.ROOT).contains("application/zip")) {
                display = extractFromZip(currentResponse.body());
            } else {
                display = extractFromPdf(currentResponse.body());
            }
        } catch (Throwable t) {
            display = "[PDF Text] Error: " + t.getClass().getSimpleName() + ": " + String.valueOf(t.getMessage());
        }
        textArea.setText(display);
        textArea.setCaretPosition(0);
    }

    @Override
    public HttpResponse getResponse() {
        // Read-only viewer: just return the original response
        return currentResponse;
    }

    @Override
    public Selection selectedData() {
        // We don't provide selections from this read-only view; return null.
        return null;
    }

    @Override
    public boolean isModified() {
        return false;
    }

    // ---------- helpers ----------

    private static String contentTypeHeader(List<HttpHeader> headers) {
        for (HttpHeader h : headers) {
            if ("Content-Type".equalsIgnoreCase(h.name())) {
                return h.value();
            }
        }
        return null;
    }

    private static String extractFromPdf(ByteArray body) throws IOException {
        byte[] pdfBytes = body.getBytes(); // Montoya ByteArray → byte[]
        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String out = stripper.getText(doc);
            return (out == null || out.isBlank())
                    ? "[PDF Text] (No extractable text — possibly image-only or encrypted PDF)"
                    : out.trim();
        }
    }

    private static String extractFromZip(ByteArray body) throws IOException {
        StringBuilder sb = new StringBuilder();
        int count = 0;

        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(body.getBytes()))) {
            ZipEntry ze;
            while ((ze = zis.getNextEntry()) != null) {
                if (ze.isDirectory()) continue;
                String name = ze.getName();
                if (!name.toLowerCase(Locale.ROOT).endsWith(".pdf")) continue;

                // Read entry fully
                int initial = (int) Math.min(Integer.MAX_VALUE, Math.max(8192L, ze.getSize()));
                ByteArrayOutputStream baos = new ByteArrayOutputStream(initial > 0 ? initial : 8192);
                byte[] buf = new byte[8192];
                int r;
                while ((r = zis.read(buf)) != -1) baos.write(buf, 0, r);
                byte[] pdfBytes = baos.toByteArray();

                // Extract text
                try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
                    PDFTextStripper stripper = new PDFTextStripper();
                    String txt = stripper.getText(doc);
                    if (count > 0) sb.append("\n\n-----\n\n");
                    sb.append("=== ").append(name).append(" ===\n");
                    if (txt != null && !txt.isBlank()) {
                        sb.append(txt.trim());
                    } else {
                        sb.append("[PDF Text] (No extractable text — possibly image-only or encrypted PDF)");
                    }
                    count++;
                }
            }
        }

        if (count == 0) {
            return "[PDF Text] No PDFs found inside ZIP.";
        }
        return sb.toString();
    }
}

