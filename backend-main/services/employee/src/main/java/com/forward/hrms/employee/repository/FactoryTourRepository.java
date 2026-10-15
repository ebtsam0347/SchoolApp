package com.forward.hrms.employee.repository;

import com.forward.hrms.employee.entity.FactoryTour;
import com.forward.hrms.employee.enums.TourStatus;
import com.forward.hrms.employee.enums.TourType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface FactoryTourRepository extends JpaRepository<FactoryTour, Long> {

    @Query("""
            select t from FactoryTour t
            where (:status is null or t.status = :status)
              and (:travelType is null or t.travelType = :travelType)
              and (:cardNo is null or t.cardNo = :cardNo)
              and (:departmentId is null or t.departmentId = :departmentId)
            order by t.tourStart desc
            """)
    Page<FactoryTour> search(TourStatus status, TourType travelType, String cardNo, Long departmentId, Pageable pageable);
}
