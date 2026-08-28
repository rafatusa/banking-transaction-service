package com.example.bankingtransactionservice.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.bankingtransactionservice.entity.Account;
import com.example.bankingtransactionservice.entity.Role;
import com.example.bankingtransactionservice.entity.TransactionRecord;
import com.example.bankingtransactionservice.service.AccountService;
import com.example.bankingtransactionservice.service.BankingException;
import com.example.bankingtransactionservice.service.TransferService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Slice tests for {@link TransactionController}.
 *
 * <p>The interesting logic here is visibility scoping and page-size clamping: a customer must never
 * be able to read another customer's history, and an unbounded {@code size} parameter must not turn
 * a history request into a full table scan.
 */
@WebMvcTest(TransactionController.class)
class TransactionControllerTest extends WebMvcTestSupport {

    @Autowired private MockMvc mockMvc;

    @MockBean private TransferService transferService;

    @MockBean private AccountService accountService;

    private static TransactionRecord record() {
        return new TransactionRecord(
                "ref-1",
                "ACC-1",
                "ACC-2",
                new BigDecimal("10.00"),
                "USD",
                TransactionRecord.Status.COMPLETED,
                "groceries",
                "alice");
    }

    private static Page<TransactionRecord> onePage() {
        return new PageImpl<>(List.of(record()), PageRequest.of(0, 20), 1);
    }

    private static Account account(String number, String owner) {
        return new Account(number, owner, new BigDecimal("10.00"), "USD");
    }

    @Test
    @DisplayName("unauthenticated history requests are rejected with 401")
    void unauthenticatedIsRejected() throws Exception {
        mockMvc.perform(get("/api/transactions")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "alice", roles = "CUSTOMER")
    @DisplayName("a scoped request runs the ownership check before returning history")
    void scopedRequestAuthorizesAccount() throws Exception {
        when(accountService.findByAccountNumber("ACC-1")).thenReturn(account("ACC-1", "alice"));
        when(transferService.historyForAccount(eq("ACC-1"), any(Pageable.class)))
                .thenReturn(onePage());

        mockMvc
                .perform(get("/api/transactions").param("accountNumber", "ACC-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].reference").value("ref-1"));

        verify(accountService).authorizeAccess(any(Account.class), eq("alice"), eq(Role.CUSTOMER));
    }

    @Test
    @WithMockUser(username = "mallory", roles = "CUSTOMER")
    @DisplayName("a customer cannot read another customer's account history")
    void scopedRequestForForeignAccountIsForbidden() throws Exception {
        when(accountService.findByAccountNumber("ACC-1")).thenReturn(account("ACC-1", "alice"));
        doThrow(BankingException.forbidden("You may only access your own accounts"))
                .when(accountService)
                .authorizeAccess(any(Account.class), anyString(), any(Role.class));

        mockMvc
                .perform(get("/api/transactions").param("accountNumber", "ACC-1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("FORBIDDEN"));

        verify(transferService, never()).historyForAccount(anyString(), any(Pageable.class));
    }

    @Test
    @WithMockUser(username = "alice", roles = "CUSTOMER")
    @DisplayName("an unscoped customer request spans only the accounts they own")
    void unscopedCustomerRequestUsesOwnedAccounts() throws Exception {
        when(accountService.findByOwner("alice"))
                .thenReturn(List.of(account("ACC-1", "alice"), account("ACC-3", "alice")));
        when(transferService.historyForAccounts(anyList(), any(Pageable.class))).thenReturn(onePage());

        mockMvc.perform(get("/api/transactions")).andExpect(status().isOk());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        verify(transferService).historyForAccounts(captor.capture(), any(Pageable.class));
        assertThat(captor.getValue()).containsExactly("ACC-1", "ACC-3");

        verify(accountService, never()).findAll();
    }

    @Test
    @WithMockUser(username = "admin1", roles = "ADMIN")
    @DisplayName("an unscoped staff request spans every account")
    void unscopedStaffRequestUsesAllAccounts() throws Exception {
        when(accountService.findAll())
                .thenReturn(List.of(account("ACC-1", "alice"), account("ACC-2", "bob")));
        when(transferService.historyForAccounts(anyList(), any(Pageable.class))).thenReturn(onePage());

        mockMvc.perform(get("/api/transactions")).andExpect(status().isOk());

        verify(accountService).findAll();
        verify(accountService, never()).findByOwner(anyString());
    }

    @Test
    @WithMockUser(username = "admin1", roles = "ADMIN")
    @DisplayName("an oversized page size is clamped to the maximum")
    void oversizedPageIsClamped() throws Exception {
        when(accountService.findAll()).thenReturn(List.of(account("ACC-1", "alice")));
        when(transferService.historyForAccounts(anyList(), any(Pageable.class))).thenReturn(onePage());

        mockMvc
                .perform(get("/api/transactions").param("size", "100000"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(transferService).historyForAccounts(anyList(), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(200);
    }

    @Test
    @WithMockUser(username = "admin1", roles = "ADMIN")
    @DisplayName("negative paging parameters are normalised rather than rejected")
    void negativePagingIsNormalised() throws Exception {
        when(accountService.findAll()).thenReturn(List.of(account("ACC-1", "alice")));
        when(transferService.historyForAccounts(anyList(), any(Pageable.class))).thenReturn(onePage());

        mockMvc
                .perform(get("/api/transactions").param("page", "-5").param("size", "-1"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(transferService).historyForAccounts(anyList(), captor.capture());
        assertThat(captor.getValue().getPageNumber()).isZero();
        assertThat(captor.getValue().getPageSize()).isEqualTo(1);
    }

    @Test
    @WithMockUser(username = "alice", roles = "CUSTOMER")
    @DisplayName("a blank accountNumber parameter is treated as an unscoped request")
    void blankAccountNumberIsUnscoped() throws Exception {
        when(accountService.findByOwner("alice")).thenReturn(List.of(account("ACC-1", "alice")));
        when(transferService.historyForAccounts(anyList(), any(Pageable.class))).thenReturn(onePage());

        mockMvc
                .perform(get("/api/transactions").param("accountNumber", "   "))
                .andExpect(status().isOk());

        verify(accountService).findByOwner("alice");
        verify(transferService, never()).historyForAccount(anyString(), any(Pageable.class));
    }

    @Test
    @WithMockUser(username = "alice", roles = "CUSTOMER")
    @DisplayName("a scoped request for a missing account surfaces as 404")
    void missingAccountIsNotFound() throws Exception {
        when(accountService.findByAccountNumber("ACC-9"))
                .thenThrow(BankingException.notFound("Account not found"));

        mockMvc
                .perform(get("/api/transactions").param("accountNumber", "ACC-9"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("NOT_FOUND"));
    }
}
