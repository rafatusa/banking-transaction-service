package com.example.bankingtransactionservice.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.bankingtransactionservice.entity.Account;
import com.example.bankingtransactionservice.entity.Role;
import com.example.bankingtransactionservice.service.AccountService;
import com.example.bankingtransactionservice.service.AuditService;
import com.example.bankingtransactionservice.service.BankingException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/** Slice tests for {@link AccountController}: RBAC, visibility rules and validation. */
@WebMvcTest(AccountController.class)
class AccountControllerTest extends WebMvcTestSupport {

    private static final String CREATE_BODY_TEMPLATE =
            "{\"ownerUsername\":\"%s\",\"openingBalance\":%s,\"currency\":\"%s\"}";

    @Autowired private MockMvc mockMvc;

    @MockBean private AccountService accountService;

    @MockBean private AuditService auditService;

    private static Account account(String number, String owner, String balance) {
        return new Account(number, owner, new BigDecimal(balance), "USD");
    }

    private static String createBody(String owner, String balance, String currency) {
        return String.format(CREATE_BODY_TEMPLATE, owner, balance, currency);
    }

    @Test
    @DisplayName("unauthenticated callers get 401, not a login redirect")
    void unauthenticatedIsRejected() throws Exception {
        mockMvc.perform(get("/api/accounts")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "teller1", roles = "TELLER")
    @DisplayName("a teller can open an account and the action is audited")
    void tellerCreatesAccount() throws Exception {
        when(accountService.create(anyString(), any(), anyString()))
                .thenReturn(account("ACC-1", "alice", "100.00"));

        mockMvc
                .perform(
                        post("/api/accounts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(createBody("alice", "100.00", "USD")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountNumber").value("ACC-1"))
                .andExpect(jsonPath("$.ownerUsername").value("alice"));

        verify(auditService)
                .record(
                        eq("teller1"),
                        eq("CREATE_ACCOUNT"),
                        eq("Account"),
                        eq("ACC-1"),
                        eq("SUCCESS"),
                        anyString(),
                        anyString());
    }

    @Test
    @WithMockUser(username = "carol", roles = "CUSTOMER")
    @DisplayName("a customer may not open an account")
    void customerCannotCreateAccount() throws Exception {
        mockMvc
                .perform(
                        post("/api/accounts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(createBody("carol", "100.00", "USD")))
                .andExpect(status().isForbidden());

        verify(accountService, never()).create(anyString(), any(), anyString());
    }

    @Test
    @WithMockUser(username = "teller1", roles = "TELLER")
    @DisplayName("a negative opening balance is rejected as a validation problem")
    void negativeOpeningBalanceRejected() throws Exception {
        mockMvc
                .perform(
                        post("/api/accounts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(createBody("alice", "-1.00", "USD")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("VALIDATION_FAILED"));

        verify(accountService, never()).create(anyString(), any(), anyString());
    }

    @Test
    @WithMockUser(username = "teller1", roles = "TELLER")
    @DisplayName("a non-ISO currency code is rejected")
    void badCurrencyRejected() throws Exception {
        mockMvc
                .perform(
                        post("/api/accounts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(createBody("alice", "100.00", "dollars")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("VALIDATION_FAILED"));
    }

    @Test
    @WithMockUser(username = "admin1", roles = "ADMIN")
    @DisplayName("staff listing returns every account")
    void staffListsAllAccounts() throws Exception {
        when(accountService.findAll())
                .thenReturn(
                        List.of(account("ACC-1", "alice", "10.00"), account("ACC-2", "bob", "20.00")));

        mockMvc
                .perform(get("/api/accounts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        verify(accountService).findAll();
        verify(accountService, never()).findByOwner(anyString());
    }

    @Test
    @WithMockUser(username = "alice", roles = "CUSTOMER")
    @DisplayName("customer listing is scoped to the caller's own accounts")
    void customerListsOnlyOwnAccounts() throws Exception {
        when(accountService.findByOwner("alice"))
                .thenReturn(List.of(account("ACC-1", "alice", "10.00")));

        mockMvc
                .perform(get("/api/accounts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].ownerUsername").value("alice"));

        verify(accountService).findByOwner("alice");
        verify(accountService, never()).findAll();
    }

    @Test
    @WithMockUser(username = "alice", roles = "CUSTOMER")
    @DisplayName("fetching one account runs the ownership check")
    void getAccountAuthorizesAccess() throws Exception {
        when(accountService.findByAccountNumber("ACC-1"))
                .thenReturn(account("ACC-1", "alice", "10.00"));

        mockMvc
                .perform(get("/api/accounts/ACC-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountNumber").value("ACC-1"));

        verify(accountService).authorizeAccess(any(Account.class), eq("alice"), eq(Role.CUSTOMER));
    }

    @Test
    @WithMockUser(username = "mallory", roles = "CUSTOMER")
    @DisplayName("a forbidden ownership check surfaces as 403 problem detail")
    void getForeignAccountIsForbidden() throws Exception {
        when(accountService.findByAccountNumber("ACC-1"))
                .thenReturn(account("ACC-1", "alice", "10.00"));
        doThrow(BankingException.forbidden("Not your account"))
                .when(accountService)
                .authorizeAccess(any(Account.class), anyString(), any(Role.class));

        mockMvc
                .perform(get("/api/accounts/ACC-1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("FORBIDDEN"));
    }

    @Test
    @WithMockUser(username = "alice", roles = "CUSTOMER")
    @DisplayName("a missing account surfaces as 404 problem detail")
    void missingAccountIsNotFound() throws Exception {
        when(accountService.findByAccountNumber("ACC-9"))
                .thenThrow(BankingException.notFound("No such account"));

        mockMvc
                .perform(get("/api/accounts/ACC-9"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("NOT_FOUND"));
    }

    @Test
    @WithMockUser(username = "teller1", roles = "TELLER")
    @DisplayName("a teller can deactivate an account")
    void tellerUpdatesAccount() throws Exception {
        Account deactivated = account("ACC-1", "alice", "10.00");
        deactivated.setActive(false);
        when(accountService.setActive("ACC-1", false)).thenReturn(deactivated);

        mockMvc
                .perform(
                        patch("/api/accounts/ACC-1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        verify(auditService)
                .record(
                        eq("teller1"),
                        eq("UPDATE_ACCOUNT"),
                        eq("Account"),
                        eq("ACC-1"),
                        eq("SUCCESS"),
                        anyString(),
                        anyString());
    }

    @Test
    @WithMockUser(username = "alice", roles = "CUSTOMER")
    @DisplayName("a customer may not update an account")
    void customerCannotUpdateAccount() throws Exception {
        mockMvc
                .perform(
                        patch("/api/accounts/ACC-1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"active\":false}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin1", roles = "ADMIN")
    @DisplayName("an admin can close an account")
    void adminClosesAccount() throws Exception {
        mockMvc.perform(delete("/api/accounts/ACC-1")).andExpect(status().isNoContent());

        verify(accountService).close("ACC-1");
        verify(auditService)
                .record(
                        eq("admin1"),
                        eq("CLOSE_ACCOUNT"),
                        eq("Account"),
                        eq("ACC-1"),
                        eq("SUCCESS"),
                        anyString(),
                        anyString());
    }

    @Test
    @WithMockUser(username = "teller1", roles = "TELLER")
    @DisplayName("a teller may not close an account — admins only")
    void tellerCannotCloseAccount() throws Exception {
        mockMvc.perform(delete("/api/accounts/ACC-1")).andExpect(status().isForbidden());

        verify(accountService, never()).close(anyString());
    }

    @Test
    @WithMockUser(username = "admin1", roles = "ADMIN")
    @DisplayName("closing an account that still holds funds surfaces as 409")
    void closingFundedAccountConflicts() throws Exception {
        doThrow(BankingException.conflict("Account still holds a balance"))
                .when(accountService)
                .close("ACC-1");

        mockMvc
                .perform(delete("/api/accounts/ACC-1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("CONFLICT"));
    }

    @Test
    @WithMockUser(username = "alice", roles = "CUSTOMER")
    @DisplayName("X-Forwarded-For is the audited client address behind nginx")
    void forwardedHeaderIsUsedAsClientIp() throws Exception {
        when(accountService.findByAccountNumber("ACC-1"))
                .thenReturn(account("ACC-1", "alice", "10.00"));

        mockMvc
                .perform(get("/api/accounts/ACC-1").header("X-Forwarded-For", "203.0.113.7, 10.0.0.1"))
                .andExpect(status().isOk());
    }
}
