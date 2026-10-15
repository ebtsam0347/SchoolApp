package com.forward.hrms.commons.kafka.outbox;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface OutboxRepository extends JpaRepository<OutboxEntry, Long> {

    @Query("""
           select e from OutboxEntry e
            where e.status = com.forward.hrms.commons.kafka.outbox.OutboxEntry$Status.PENDING
              and (e.nextAttemptAt is null or e.nextAttemptAt <= :now)
            order by e.id asc
           """)
    List<OutboxEntry> findReadyToSend(@Param("now") LocalDateTime now, Pageable pageable);
}
