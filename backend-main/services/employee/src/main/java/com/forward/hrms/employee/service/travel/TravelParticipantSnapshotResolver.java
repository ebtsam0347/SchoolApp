package com.forward.hrms.employee.service.travel;

import com.forward.hrms.employee.entity.Employee;
import com.forward.hrms.employee.entity.travel.TravelRequestParticipant;
import com.forward.hrms.employee.repository.DepartmentRepository;
import com.forward.hrms.employee.repository.DesignationRepository;
import com.forward.hrms.employee.repository.GradeRepository;
import com.forward.hrms.employee.repository.SectionRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

/**
 * Fills a {@link TravelRequestParticipant}'s historical employee /
 * organisation snapshot fields from the current {@link Employee} record
 * plus the catalog rows it points at. The snapshot is frozen at the time
 * the participant is added so later grade/designation/department/section
 * renames or moves never rewrite a historical Travel Request.
 */
@Component
@RequiredArgsConstructor
public class TravelParticipantSnapshotResolver {

    private final GradeRepository grades;
    private final DesignationRepository designations;
    private final DepartmentRepository departments;
    private final SectionRepository sections;

    void apply(TravelRequestParticipant p, Employee e) {
        p.setEmployeeId(e.getId());
        p.setEmployeeNoSnapshot(e.getEmployeeCode());
        p.setEmployeeNameSnapshot(e.getFullName());

        p.setGradeIdSnapshot(e.getGradeId());
        if (e.getGradeId() != null) {
            grades.findById(e.getGradeId()).ifPresent(g -> {
                p.setGradeCodeSnapshot(g.getCode());
                p.setGradeNameSnapshot(g.getName());
            });
        }

        p.setDesignationIdSnapshot(e.getDesignationId());
        if (e.getDesignationId() != null) {
            designations.findById(e.getDesignationId())
                    .ifPresent(d -> p.setDesignationNameSnapshot(d.getTitle()));
        }

        p.setDepartmentIdSnapshot(e.getDepartmentId());
        if (e.getDepartmentId() != null) {
            departments.findById(e.getDepartmentId())
                    .ifPresent(d -> p.setDepartmentNameSnapshot(d.getName()));
        }

        p.setSectionIdSnapshot(e.getSectionId());
        if (e.getSectionId() != null) {
            sections.findById(e.getSectionId())
                    .ifPresent(s -> p.setSectionNameSnapshot(s.getName()));
        }
    }
}
