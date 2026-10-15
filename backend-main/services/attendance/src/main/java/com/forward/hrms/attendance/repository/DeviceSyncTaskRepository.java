package com.forward.hrms.attendance.repository;

import com.forward.hrms.attendance.entity.DeviceSyncTask;
import com.forward.hrms.attendance.enums.DeviceSyncTaskStatus;
import com.forward.hrms.attendance.enums.DeviceSyncTaskType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DeviceSyncTaskRepository extends JpaRepository<DeviceSyncTask, Long> {

    /**
     * Selects up to {@code limit} ready tasks for the device and locks
     * the rows for the duration of the transaction. {@code FOR UPDATE
     * SKIP LOCKED} is the postgres primitive that lets multiple
     * pollers (or a single one with an in-flight retry) coexist
     * without blocking on each other. The caller flips status to
     * IN_PROGRESS and {@code claimed_at} via JPA dirty-tracking once
     * it has the rows, so the audit listener fires.
     */
    @Query(value = """
            select * from attendance_device_sync_tasks
             where device_id = :deviceId
               and status = 'PENDING'
               and scheduled_for <= now()
             order by scheduled_for asc
             limit :limit
             for update skip locked
            """, nativeQuery = true)
    List<DeviceSyncTask> lockClaimable(@Param("deviceId") Long deviceId, @Param("limit") int limit);

    Page<DeviceSyncTask> findByDeviceId(Long deviceId, Pageable pageable);

    Page<DeviceSyncTask> findByDeviceIdAndStatusIn(
            Long deviceId, Collection<DeviceSyncTaskStatus> statuses, Pageable pageable);

    Optional<DeviceSyncTask> findFirstByTaskTypeAndStatusInOrderByIdDesc(
            DeviceSyncTaskType taskType, Collection<DeviceSyncTaskStatus> statuses);
}
