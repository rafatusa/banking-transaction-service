package com.example.bankingtransactionservice.service;

import com.example.bankingtransactionservice.entity.AuditEvent;
import com.example.bankingtransactionservice.repository.AuditEventRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Records and exposes the append-only audit trail. */
@Service
public class AuditService {

    private final AuditEventRepository auditEventRepository;

    public AuditService(AuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }

    /**
     * Writes an audit entry in its own transaction.
     *
     * <p>{@link Propagation#REQUIRES_NEW} is deliberate: a rejected transfer rolls back its own
     * transaction, and the audit record of that rejection must survive the rollback. Sharing the
     * caller's transaction would erase exactly the events most worth auditing.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(
            String actor,
            String action,
            String resourceType,
            String resourceId,
            String outcome,
            String detail,
            String sourceIp) {
        auditEventRepository.save(
                new AuditEvent(actor, action, resourceType, resourceId, outcome, detail, sourceIp));
    }

    /** Returns the full audit trail, most recent first. */
    @Transactional(readOnly = true)
    public Page<AuditEvent> findAll(Pageable pageable) {
        return auditEventRepository.findAllByOrderByCreatedAtDesc(pageable);
    }

    /** Returns the audit trail for a single actor, most recent first. */
    @Transactional(readOnly = true)
    public Page<AuditEvent> findByActor(String actor, Pageable pageable) {
        return auditEventRepository.findByActorOrderByCreatedAtDesc(actor, pageable);
    }
}
