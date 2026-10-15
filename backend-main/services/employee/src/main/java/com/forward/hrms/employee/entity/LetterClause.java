package com.forward.hrms.employee.entity;

import com.forward.hrms.commons.core.jpa.AuditedEntity;
import com.forward.hrms.employee.enums.LetterClauseSection;
import com.forward.hrms.employee.enums.LetterType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.Setter;

/**
 * One paragraph of an HR letter template. {@link #body} may carry
 * {@code {placeholder}} tokens filled at render time; wording is owned by
 * HR/legal and edited in the table, not in code.
 */
@Entity
@Getter
@Setter
@Table(name = "letter_clauses")
public class LetterClause extends AuditedEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "letter_type", nullable = false, length = 32)
    private LetterType letterType;

    @Enumerated(EnumType.STRING)
    @Column(name = "section", nullable = false, length = 16)
    private LetterClauseSection section;

    @Column(name = "seq", nullable = false)
    private int seq;

    @Column(name = "title")
    private String title;

    @Column(name = "body", nullable = false, columnDefinition = "text")
    private String body;

    @Column(name = "active", nullable = false)
    private boolean active = true;
}
