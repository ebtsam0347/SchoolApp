package com.forward.hrms.employee.entity;

import com.forward.hrms.commons.core.jpa.AuditedEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.Setter;

/**
 * Company letterhead, default signatory and policy defaults shared by every
 * HR letter (appointment, service, experience). Exactly one row, seeded by
 * {@code V0622}; read through {@code LetterSettingsService.current()}.
 */
@Entity
@Getter
@Setter
@Table(name = "letter_settings")
public class LetterSettings extends AuditedEntity {

    @Column(name = "company_legal_name", nullable = false)
    private String companyLegalName;

    @Column(name = "company_address", nullable = false)
    private String companyAddress;

    @Column(name = "company_phone")
    private String companyPhone;

    @Column(name = "company_email")
    private String companyEmail;

    /** Object key in the employee-docs bucket; null prints the letterhead without a logo. */
    @Column(name = "logo_object_key")
    private String logoObjectKey;

    /** Leading part of generated reference numbers, e.g. {@code FS/HR}. */
    @Column(name = "ref_prefix", nullable = false)
    private String refPrefix;

    @Column(name = "signatory_name", nullable = false)
    private String signatoryName;

    @Column(name = "signatory_designation", nullable = false)
    private String signatoryDesignation;

    @Column(name = "default_probation_months", nullable = false)
    private int defaultProbationMonths;

    /** Branch printed on the appointment letter unless the request overrides it. */
    @Column(name = "default_branch")
    private String defaultBranch;
}
