package com.example.bankingtransactionservice.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.bankingtransactionservice.entity.Role;
import com.example.bankingtransactionservice.entity.TransactionRecord;
import com.example.bankingtransactionservice.service.BankingException;
import com.example.bankingtransactionservice.service.TransferService;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Slice tests for {@link TransferController}.
 *
 * <p>The controller's real job is translating the authenticated principal, the caller's role and
 * the originating IP into a {@link TransferService.TransferCommand}. Those three values drive the
 * authorization and audit decisions inside the service, so the tests assert on the captured
 * command, not merely on the status code.
 */
@WebMvcTest(TransferController.class)
class TransferControllerTest extends WebMvcTestSupport {

    private static final String TRANSFER_BODY =
            "{\"sourceAccount\":\"ACC-1\",\"targetAccount\":\"ACC-2\","
                    + "\"amount\":25.00,\"description\":\"rent\"}";

    @Autowired private MockMvc mockMvc;

    @MockBean private TransferService transferService;

    private static TransactionRecord completed() {
        return new TransactionRecord(
                "ref-1",
                "ACC-1",
                "ACC-2",
                new BigDecimal("25.00"),
                "USD",
                TransactionRecord.Status.COMPLETED,
                "rent",
                "alice");
    }

    @Test
    @DisplayName("unauthenticated transfer attempts are rejected with 401")
    void unauthenticatedIsRejected() throws Exception {
        mockMvc
                .perform(
                        post("/api/transfers")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(TRANSFER_BODY))
                .andExpect(status().isUnauthorized());

        verify(transferService, never()).transfer(any());
    }

    @Test
    @WithMockUser(username = "alice", roles = "CUSTOMER")
    @DisplayName("a completed transfer returns 201 with the transaction record")
    void transferSucceeds() throws Exception {
        when(transferService.transfer(any())).thenReturn(completed());

        mockMvc
                .perform(
                        post("/api/transfers")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(TRANSFER_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reference").value("ref-1"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.amount").value(25.00));
    }

    @Test
    @WithMockUser(username = "alice", roles = "CUSTOMER")
    @DisplayName("the command carries the caller's identity, role and forwarded client IP")
    void commandCarriesCallerContext() throws Exception {
        when(transferService.transfer(any())).thenReturn(completed());

        mockMvc
                .perform(
                        post("/api/transfers")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(TRANSFER_BODY)
                                .header("X-Forwarded-For", "203.0.113.9, 10.0.0.1"))
                .andExpect(status().isCreated());

        ArgumentCaptor<TransferService.TransferCommand> captor =
                ArgumentCaptor.forClass(TransferService.TransferCommand.class);
        verify(transferService).transfer(captor.capture());

        TransferService.TransferCommand command = captor.getValue();
        assertThat(command.initiatedBy()).isEqualTo("alice");
        assertThat(command.initiatorRole()).isEqualTo(Role.CUSTOMER);
        assertThat(command.sourceIp()).isEqualTo("203.0.113.9");
        assertThat(command.sourceAccount()).isEqualTo("ACC-1");
        assertThat(command.targetAccount()).isEqualTo("ACC-2");
        assertThat(command.amount()).isEqualByComparingTo("25.00");
    }

    @Test
    @WithMockUser(username = "teller1", roles = "TELLER")
    @DisplayName("a teller's role reaches the service unchanged")
    void tellerRoleIsPropagated() throws Exception {
        when(transferService.transfer(any())).thenReturn(completed());

        mockMvc
                .perform(
                        post("/api/transfers")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(TRANSFER_BODY))
                .andExpect(status().isCreated());

        ArgumentCaptor<TransferService.TransferCommand> captor =
                ArgumentCaptor.forClass(TransferService.TransferCommand.class);
        verify(transferService).transfer(captor.capture());
        assertThat(captor.getValue().initiatorRole()).isEqualTo(Role.TELLER);
    }

    @Test
    @WithMockUser(username = "alice", roles = "CUSTOMER")
    @DisplayName("without a proxy header the socket address is used as the client IP")
    void remoteAddrUsedWhenNoForwardedHeader() throws Exception {
        when(transferService.transfer(any())).thenReturn(completed());

        mockMvc
                .perform(
                        post("/api/transfers")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(TRANSFER_BODY))
                .andExpect(status().isCreated());

        ArgumentCaptor<TransferService.TransferCommand> captor =
                ArgumentCaptor.forClass(TransferService.TransferCommand.class);
        verify(transferService).transfer(captor.capture());
        assertThat(captor.getValue().sourceIp()).isNotBlank();
    }

    @Test
    @WithMockUser(username = "alice", roles = "CUSTOMER")
    @DisplayName("a zero amount is rejected before the service is reached")
    void zeroAmountRejected() throws Exception {
        String body =
                "{\"sourceAccount\":\"ACC-1\",\"targetAccount\":\"ACC-2\","
                        + "\"amount\":0.00,\"description\":\"nope\"}";

        mockMvc
                .perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("VALIDATION_FAILED"));

        verify(transferService, never()).transfer(any());
    }

    @Test
    @WithMockUser(username = "alice", roles = "CUSTOMER")
    @DisplayName("a blank source account is rejected before the service is reached")
    void blankSourceRejected() throws Exception {
        String body =
                "{\"sourceAccount\":\"\",\"targetAccount\":\"ACC-2\","
                        + "\"amount\":25.00,\"description\":\"x\"}";

        mockMvc
                .perform(post("/api/transfers").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());

        verify(transferService, never()).transfer(any());
    }

    @Test
    @WithMockUser(username = "mallory", roles = "CUSTOMER")
    @DisplayName("transferring from someone else's account surfaces as 403")
    void foreignSourceAccountForbidden() throws Exception {
        when(transferService.transfer(any()))
                .thenThrow(BankingException.forbidden("You may only transfer from your own accounts"));

        mockMvc
                .perform(
                        post("/api/transfers")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(TRANSFER_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("FORBIDDEN"));
    }

    @Test
    @WithMockUser(username = "alice", roles = "CUSTOMER")
    @DisplayName("insufficient funds surfaces as a 400 business-rule problem")
    void insufficientFundsIsBadRequest() throws Exception {
        when(transferService.transfer(any()))
                .thenThrow(BankingException.businessRule("Insufficient funds"));

        mockMvc
                .perform(
                        post("/api/transfers")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(TRANSFER_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("BUSINESS_RULE"));
    }

    @Test
    @WithMockUser(username = "alice", roles = "CUSTOMER")
    @DisplayName("a missing account surfaces as 404")
    void missingAccountIsNotFound() throws Exception {
        when(transferService.transfer(any()))
                .thenThrow(BankingException.notFound("Account not found"));

        mockMvc
                .perform(
                        post("/api/transfers")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(TRANSFER_BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("NOT_FOUND"));
    }

    @Test
    @WithMockUser(username = "alice", roles = "CUSTOMER")
    @DisplayName("an unexpected service failure is reported opaquely as 500")
    void unexpectedFailureIsOpaque() throws Exception {
        when(transferService.transfer(any())).thenThrow(new IllegalStateException("ledger offline"));

        mockMvc
                .perform(
                        post("/api/transfers")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(TRANSFER_BODY))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.title").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred"));
    }
}
