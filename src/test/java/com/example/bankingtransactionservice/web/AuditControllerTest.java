package com.example.bankingtransactionservice.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.bankingtransactionservice.entity.AuditEvent;
import com.example.bankingtransactionservice.service.AuditService;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Slice tests for {@link AuditController}.
 *
 * <p>The audit trail is the most access-sensitive endpoint in the service: it exposes who did what,
 * from which address. Every non-admin role must be refused, so each is asserted explicitly rather
 * than relying on one representative case.
 */
@WebMvcTest(AuditController.class)
class AuditControllerTest extends WebMvcTestSupport {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private AuditService auditService;

    private static Page<AuditEvent> onePage() {
        AuditEvent event =
                new AuditEvent(
                        "alice", "TRANSFER", "Account", "ACC-1", "SUCCESS", "moved 10.00", "203.0.113.5");
        return new PageImpl<>(List.of(event), PageRequest.of(0, 20), 1);
    }

    @Test
    @DisplayName("unauthenticated callers cannot read the audit trail")
    void unauthenticatedIsRejected() throws Exception {
        mockMvc.perform(get("/api/audit")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "alice", roles = "CUSTOMER")
    @DisplayName("a customer cannot read the audit trail")
    void customerIsForbidden() throws Exception {
        mockMvc.perform(get("/api/audit")).andExpect(status().isForbidden());

        verify(auditService, never()).findAll(any(Pageable.class));
    }

    @Test
    @WithMockUser(username = "teller1", roles = "TELLER")
    @DisplayName("a teller cannot read the audit trail — administrators only")
    void tellerIsForbidden() throws Exception {
        mockMvc.perform(get("/api/audit")).andExpect(status().isForbidden());

        verify(auditService, never()).findAll(any(Pageable.class));
    }

    @Test
    @WithMockUser(username = "admin1", roles = "ADMIN")
    @DisplayName("an admin reads the full audit trail")
    void adminReadsAllEvents() throws Exception {
        when(auditService.findAll(any(Pageable.class))).thenReturn(onePage());

        mockMvc
                .perform(get("/api/audit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].actor").value("alice"))
                .andExpect(jsonPath("$.content[0].action").value("TRANSFER"))
                .andExpect(jsonPath("$.content[0].sourceIp").value("203.0.113.5"));

        verify(auditService, never()).findByActor(anyString(), any(Pageable.class));
    }

    @Test
    @WithMockUser(username = "admin1", roles = "ADMIN")
    @DisplayName("the actor filter selects the by-actor query")
    void actorFilterIsApplied() throws Exception {
        when(auditService.findByActor(eq("alice"), any(Pageable.class))).thenReturn(onePage());

        mockMvc
                .perform(get("/api/audit").param("actor", "alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].actor").value("alice"));

        verify(auditService).findByActor(eq("alice"), any(Pageable.class));
        verify(auditService, never()).findAll(any(Pageable.class));
    }

    @Test
    @WithMockUser(username = "admin1", roles = "ADMIN")
    @DisplayName("a blank actor filter falls back to the unfiltered query")
    void blankActorFilterIsIgnored() throws Exception {
        when(auditService.findAll(any(Pageable.class))).thenReturn(onePage());

        mockMvc.perform(get("/api/audit").param("actor", "  ")).andExpect(status().isOk());

        verify(auditService).findAll(any(Pageable.class));
        verify(auditService, never()).findByActor(anyString(), any(Pageable.class));
    }

    @Test
    @WithMockUser(username = "admin1", roles = "ADMIN")
    @DisplayName("an oversized page size is clamped to the maximum")
    void oversizedPageIsClamped() throws Exception {
        when(auditService.findAll(any(Pageable.class))).thenReturn(onePage());

        mockMvc.perform(get("/api/audit").param("size", "100000")).andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(auditService).findAll(captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(200);
    }

    @Test
    @WithMockUser(username = "admin1", roles = "ADMIN")
    @DisplayName("negative paging parameters are normalised rather than rejected")
    void negativePagingIsNormalised() throws Exception {
        when(auditService.findAll(any(Pageable.class))).thenReturn(onePage());

        mockMvc
                .perform(get("/api/audit").param("page", "-3").param("size", "0"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(auditService).findAll(captor.capture());
        assertThat(captor.getValue().getPageNumber()).isZero();
        assertThat(captor.getValue().getPageSize()).isEqualTo(1);
    }
}
