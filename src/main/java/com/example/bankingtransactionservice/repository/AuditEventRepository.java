package com.example.bankingtransactionservice.repository;

import com.example.bankingtransactionservice.entity.AuditEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Data access for {@link AuditEvent}. Append-only by design: no update or delete methods. */
public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

    Page<AuditEvent> findByActorOrderByCreatedAtDesc(String actor, Pageable pageable);

    Page<AuditEvent> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
