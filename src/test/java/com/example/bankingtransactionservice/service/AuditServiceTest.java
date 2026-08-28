package com.example.bankingtransactionservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.bankingtransactionservice.entity.AuditEvent;
import com.example.bankingtransactionservice.repository.AuditEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/** Unit tests for the audit trail. */
@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock private AuditEventRepository auditEventRepository;

    @InjectMocks private AuditService auditService;

    @Test
    @DisplayName("persists every field of an audit entry")
    void recordsEvent() {
        auditService.record(
                "alice", "TRANSFER", "Account", "ACC1", "SUCCESS", "moved 10.00", "203.0.113.1");

        ArgumentCaptor<AuditEvent> captor = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditEventRepository).save(captor.capture());

        AuditEvent saved = captor.getValue();
        assertThat(saved.getActor()).isEqualTo("alice");
        assertThat(saved.getAction()).isEqualTo("TRANSFER");
        assertThat(saved.getResourceType()).isEqualTo("Account");
        assertThat(saved.getResourceId()).isEqualTo("ACC1");
        assertThat(saved.getOutcome()).isEqualTo("SUCCESS");
        assertThat(saved.getDetail()).isEqualTo("moved 10.00");
        assertThat(saved.getSourceIp()).isEqualTo("203.0.113.1");
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("reads the whole trail newest first")
    void findsAll() {
        Pageable pageable = PageRequest.of(0, 20);
        when(auditEventRepository.findAllByOrderByCreatedAtDesc(pageable))
                .thenReturn(Page.empty(pageable));

        Page<AuditEvent> page = auditService.findAll(pageable);

        assertThat(page).isEmpty();
        verify(auditEventRepository).findAllByOrderByCreatedAtDesc(pageable);
    }

    @Test
    @DisplayName("filters the trail by actor")
    void findsByActor() {
        Pageable pageable = PageRequest.of(0, 20);
        when(auditEventRepository.findByActorOrderByCreatedAtDesc("alice", pageable))
                .thenReturn(Page.empty(pageable));

        auditService.findByActor("alice", pageable);

        verify(auditEventRepository).findByActorOrderByCreatedAtDesc("alice", pageable);
    }

    @Test
    @DisplayName("accepts a null resource id and detail")
    void toleratesNullOptionalFields() {
        auditService.record("system", "STARTUP", "Application", null, "SUCCESS", null, null);

        verify(auditEventRepository).save(any(AuditEvent.class));
    }
}
