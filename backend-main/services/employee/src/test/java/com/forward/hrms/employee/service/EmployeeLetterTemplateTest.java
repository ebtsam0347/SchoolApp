package com.forward.hrms.employee.service;

import com.forward.hrms.commons.reports.JasperRenderer;
import com.forward.hrms.commons.storage.ObjectStorage;
import com.forward.hrms.employee.config.DocumentProperties;
import com.forward.hrms.employee.entity.LetterSettings;
import com.forward.hrms.employee.repository.LetterSettingsRepository;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;

import net.sf.jasperreports.engine.JREmptyDataSource;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Service / experience letter template ({@code jasper/employee-letter.jrxml})
 * fills with the shared letterhead parameters — the contract it now shares
 * with the appointment letter.
 */
class EmployeeLetterTemplateTest {

    @Test
    void serviceLetterPrintsLetterheadBodyAndSignatory() throws Exception {
        LetterSettings s = new LetterSettings();
        s.setCompanyLegalName("Forward Sports (Pvt) Ltd.");
        s.setCompanyAddress("Sialkot, Punjab, Pakistan");
        s.setCompanyPhone("052-3571800-5");
        s.setSignatoryName("Manager Human Resources");
        s.setSignatoryDesignation("Human Resources Department");
        LetterSettingsService letters = new LetterSettingsService(mock(LetterSettingsRepository.class),
                mock(ObjectStorage.class), new DocumentProperties("employee-docs", "employee-avatars",
                Duration.ofMinutes(10), Duration.ofMinutes(10), Duration.ofDays(7), 1L, 1L));

        Map<String, Object> params = letters.letterheadParams(s, null, null);
        params.put("letterTitle", "Service Letter");
        params.put("letterDate", "25 September 2026");
        params.put("employeeCode", "FS-2026-0042");
        params.put("body", "To Whom It May Concern,\n\nThis is to certify that Muhammad Ali has been employed.");

        byte[] pdf = new JasperRenderer().renderPdf("/jasper/employee-letter.jrxml", params, new JREmptyDataSource(1));
        Files.createDirectories(Path.of("target", "report-previews"));
        Files.write(Path.of("target", "report-previews", "service-letter.pdf"), pdf);

        PdfReader reader = new PdfReader(pdf);
        String text = new PdfTextExtractor(reader).getTextFromPage(1).replaceAll("\s+", " ");
        assertThat(reader.getNumberOfPages()).isEqualTo(1);
        assertThat(text.trim()).startsWith("Forward Sports (Pvt) Ltd.");
        assertThat(text).contains("Ph.#: 052-3571800-5", "SERVICE LETTER", "25 September 2026",
                "Muhammad Ali has been employed", "For Forward Sports (Pvt) Ltd.", "Manager Human Resources");
    }
}
