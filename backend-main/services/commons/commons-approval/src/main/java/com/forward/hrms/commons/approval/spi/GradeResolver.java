package com.forward.hrms.commons.approval.spi;

/** Resolves the {@code GRADE} stage kind — a role-only pool keyed by grade instead of permission. */
@FunctionalInterface
public interface GradeResolver {

    /**
     * @param userId the acting user's id
     * @return the grade code of that user's linked employee record, or {@code null} if none
     */
    String resolveGradeCode(long userId);
}
