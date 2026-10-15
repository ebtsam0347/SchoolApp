package com.forward.hrms.employee.service;

import com.forward.hrms.commons.core.context.CurrentUser;
import com.forward.hrms.commons.core.context.CurrentUserHolder;
import com.forward.hrms.commons.storage.ObjectStorage;
import com.forward.hrms.employee.config.DocumentProperties;
import com.forward.hrms.employee.entity.LetterSettings;
import com.forward.hrms.employee.repository.LetterSettingsRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Letterhead, signatory and footer parameters shared by every HR letter
 * template (appointment, service, experience), so the three print as one
 * family. Values come from the single {@code letter_settings} row.
 *
 * <p>Parameter contract (all {@code String} unless noted), consumed by
 * {@code jasper/appointment-letter.jrxml} and {@code jasper/employee-letter.jrxml}:
 * {@code companyName, companyAddress, companyContact, companyLogo (InputStream, nullable),
 * signatoryName, signatoryDesignation, generatedAt, generatedBy}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LetterSettingsService {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm");

    private final LetterSettingsRepository settings;
    private final ObjectStorage storage;
    private final DocumentProperties docProps;

    /** The seeded settings row. Missing means V0622 didn't run — a deployment fault, not a user error. */
    public LetterSettings current() {
        return settings.findFirstByOrderByIdAsc().orElseThrow(() ->
                new IllegalStateException("letter_settings has no row; migration V0622 must seed it"));
    }

    /**
     * Letterhead + footer parameters. {@code signatoryName}/{@code signatoryDesignation}
     * override the settings defaults when non-blank.
     */
    public Map<String, Object> letterheadParams(LetterSettings s, String signatoryName, String signatoryDesignation) {
        CurrentUser actor = CurrentUserHolder.get().orElse(null);
        Map<String, Object> p = new HashMap<>();
        p.put("companyName", s.getCompanyLegalName());
        p.put("companyAddress", s.getCompanyAddress());
        p.put("companyContact", contactLine(s));
        p.put("companyLogo", logo(s));
        p.put("signatoryName", blank(signatoryName) ? s.getSignatoryName() : signatoryName.trim());
        p.put("signatoryDesignation", blank(signatoryDesignation) ? s.getSignatoryDesignation() : signatoryDesignation.trim());
        p.put("generatedAt", STAMP.format(LocalDateTime.now()));
        p.put("generatedBy", actor == null ? CurrentUser.SYSTEM_USERNAME : actor.username());
        return p;
    }

    private static String contactLine(LetterSettings s) {
        StringBuilder sb = new StringBuilder();
        if (!blank(s.getCompanyPhone())) sb.append("Ph.#: ").append(s.getCompanyPhone().trim());
        if (!blank(s.getCompanyEmail())) {
            if (!sb.isEmpty()) sb.append("   ·   ");
            sb.append("Email: ").append(s.getCompanyEmail().trim());
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    /** Logo bytes from the employee-docs bucket; a missing or unreadable logo prints the letterhead without it. */
    private InputStream logo(LetterSettings s) {
        return read(docProps.bucket(), s.getLogoObjectKey(), "letterhead logo");
    }

    /** Employee photo from the avatar bucket for letters that print one; null when absent or unreadable. */
    public InputStream employeePhoto(String profilePictureKey) {
        return read(docProps.avatarBucket(), profilePictureKey, "employee photo");
    }

    private InputStream read(String bucket, String key, String what) {
        if (blank(key)) return null;
        try (InputStream in = storage.getObject(bucket, key)) {
            return new ByteArrayInputStream(in.readAllBytes());
        } catch (Exception ex) {
            log.warn("{} unavailable ({}/{}): {}", what, bucket, key, ex.getMessage());
            return null;
        }
    }

    static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
