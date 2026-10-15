package com.forward.hrms.attendance.service;

import com.forward.hrms.attendance.dto.request.CreateHolidayRequest;
import com.forward.hrms.attendance.dto.request.UpdateHolidayRequest;
import com.forward.hrms.attendance.dto.response.HolidayDto;
import com.forward.hrms.attendance.entity.Holiday;
import com.forward.hrms.attendance.enums.AttendanceErrorCode;
import com.forward.hrms.attendance.repository.HolidayRepository;
import com.forward.hrms.attendance.dto.response.HolidayDtoMapper;
import com.forward.hrms.commons.core.error.ConflictError;
import com.forward.hrms.commons.core.error.NotFoundError;
import com.forward.hrms.commons.kafka.outbox.AuditEmitter;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class HolidayService {

    private final HolidayRepository holidays;
    private final AuditEmitter audit;

    @Transactional(readOnly = true)
    public List<HolidayDto> listForYear(int year) {
        return holidays.findAllByHolidayDateBetweenOrderByHolidayDateAsc(
                        LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31)).stream()
                .map(HolidayDtoMapper::of).toList();
    }

    @Transactional
    public HolidayDto create(CreateHolidayRequest req) {
        if (holidays.existsByHolidayDate(req.holidayDate())) {
            throw new ConflictError(AttendanceErrorCode.HOLIDAY_DATE_TAKEN, "holiday already exists");
        }
        Holiday h = new Holiday();
        h.setHolidayDate(req.holidayDate());
        h.setName(req.name());
        h.setType(req.type());
        h.setDescription(req.description());
        h = holidays.save(h);
        audit.emit("holiday", String.valueOf(h.getId()), "created", null, snapshot(h), false);
        return HolidayDtoMapper.of(h);
    }

    @Transactional
    public HolidayDto update(long id, UpdateHolidayRequest req) {
        Holiday h = holidays.findById(id)
                .orElseThrow(() -> new NotFoundError(AttendanceErrorCode.HOLIDAY_NOT_FOUND, "holiday not found"));
        Map<String, Object> before = snapshot(h);
        if (req.name() != null && !req.name().isBlank()) h.setName(req.name());
        if (req.type() != null) h.setType(req.type());
        if (req.description() != null) h.setDescription(req.description());
        audit.emit("holiday", String.valueOf(h.getId()), "updated", before, snapshot(h), false);
        return HolidayDtoMapper.of(h);
    }

    @Transactional
    public void delete(long id) {
        Holiday h = holidays.findById(id)
                .orElseThrow(() -> new NotFoundError(AttendanceErrorCode.HOLIDAY_NOT_FOUND, "holiday not found"));
        Map<String, Object> before = snapshot(h);
        holidays.delete(h);
        audit.emit("holiday", String.valueOf(id), "deleted", before, null, false);
    }

    private static Map<String, Object> snapshot(Holiday h) {
        Map<String, Object> m = new HashMap<>();
        m.put("date", h.getHolidayDate().toString());
        m.put("name", h.getName());
        m.put("type", h.getType() == null ? null : h.getType().name());
        return m;
    }
}
