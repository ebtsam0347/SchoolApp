package com.forward.hrms.employee.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "OnboardingLookups",
        description = "Bundle of every catalog the onboarding wizard reads on its first paint. " +
                "Pulls 8 employee-side endpoints in one round-trip. The wizard fetches " +
                "`/admin/roles` from auth-service separately when the caller has `auth.users.write`.")
public record OnboardingLookups(
        List<DepartmentDto> departments,
        List<SectionDto> sections,
        List<DesignationDto> designations,
        List<GradeDto> grades,
        List<ProvinceDto> provinces,
        List<CityDto> cities,
        List<QualificationDto> qualifications,
        List<SkillDto> skills
) {}
