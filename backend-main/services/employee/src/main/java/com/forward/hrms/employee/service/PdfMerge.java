package com.forward.hrms.employee.service;

import com.lowagie.text.Document;
import com.lowagie.text.pdf.PdfCopy;
import com.lowagie.text.pdf.PdfReader;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/** Concatenates rendered PDFs (one letter each) into one printable file. OpenPDF ships with JasperReports. */
final class PdfMerge {

    private PdfMerge() {}

    static byte[] merge(List<byte[]> pdfs) {
        if (pdfs.size() == 1) return pdfs.get(0);
        ByteArrayOutputStream out = new ByteArrayOutputStream(pdfs.size() * 8 * 1024);
        Document doc = new Document();
        try {
            PdfCopy copy = new PdfCopy(doc, out);
            doc.open();
            for (byte[] pdf : pdfs) {
                PdfReader reader = new PdfReader(pdf);
                for (int p = 1; p <= reader.getNumberOfPages(); p++) copy.addPage(copy.getImportedPage(reader, p));
                copy.freeReader(reader);
                reader.close();
            }
        } catch (IOException | RuntimeException ex) {
            throw new IllegalStateException("PDF merge failed", ex);
        } finally {
            if (doc.isOpen()) doc.close();
        }
        return out.toByteArray();
    }
}
