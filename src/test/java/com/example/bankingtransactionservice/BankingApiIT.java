package com.example.bankingtransactionservice;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import com.example.bankingtransactionservice.entity.Role;
import com.example.bankingtransactionservice.entity.UserAccount;
import com.example.bankingtransactionservice.repository.UserAccountRepository;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;

/** End-to-end API tests driven through REST Assured against a real PostgreSQL container. */
class BankingApiIT extends AbstractIntegrationTest {

    @LocalServerPort private int port;

    @Autowired private UserAccountRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        if (!userRepository.existsByUsername(TestCredentials.CUSTOMER_USER)) {
            userRepository.save(
                    new UserAccount(
                            TestCredentials.CUSTOMER_USER,
                            passwordEncoder.encode(TestCredentials.CUSTOMER_CREDENTIAL),
                            Role.CUSTOMER));
        }
    }

    private String tokenFor(String username, String credential) {
        return given()
                .contentType(ContentType.JSON)
                .body(Map.of("username", username, "password", credential))
                .when()
                .post("/api/auth/login")
                .then()
                .statusCode(200)
                .extract()
                .path("token");
    }

    private String adminToken() {
        return tokenFor(TestCredentials.ADMIN_USER, TestCredentials.ADMIN_CREDENTIAL);
    }

    private String customerToken() {
        return tokenFor(TestCredentials.CUSTOMER_USER, TestCredentials.CUSTOMER_CREDENTIAL);
    }

    private String openAccount(String token, String owner, String balance) {
        return given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(Map.of("ownerUsername", owner, "openingBalance", balance, "currency", "USD"))
                .when()
                .post("/api/accounts")
                .then()
                .statusCode(201)
                .extract()
                .path("accountNumber");
    }

    @Test
    @DisplayName("health endpoint reports the database as UP")
    void healthShowsDatabaseUp() {
        given().when().get("/actuator/health").then().statusCode(200).body("status", equalTo("UP"));
    }

    @Test
    @DisplayName("OpenAPI document and Swagger UI are served")
    void apiDocsAvailable() {
        given().when().get("/v3/api-docs").then().statusCode(200);
        given().when().get("/swagger-ui/index.html").then().statusCode(200);
    }

    @Test
    @DisplayName("landing page is served at the root")
    void landingPageServed() {
        given().when().get("/").then().statusCode(200);
    }

    @Test
    @DisplayName("valid credentials return a bearer token")
    void loginSucceeds() {
        given().contentType(ContentType.JSON)
                .body(
                        Map.of(
                                "username", TestCredentials.ADMIN_USER,
                                "password", TestCredentials.ADMIN_CREDENTIAL))
                .when()
                .post("/api/auth/login")
                .then()
                .statusCode(200)
                .body("token", notNullValue())
                .body("tokenType", equalTo("Bearer"))
                .body("role", equalTo("ADMIN"));
    }

    @Test
    @DisplayName("bad credentials are refused")
    void loginFailsForBadCredential() {
        given().contentType(ContentType.JSON)
                .body(
                        Map.of(
                                "username", TestCredentials.ADMIN_USER,
                                "password", "definitely-not-the-right-value"))
                .when()
                .post("/api/auth/login")
                .then()
                .statusCode(403);
    }

    @Test
    @DisplayName("protected endpoints reject an unauthenticated caller")
    void unauthenticatedIsRejected() {
        given().when().get("/api/accounts").then().statusCode(401);
    }

    @Test
    @DisplayName("protected endpoints reject a forged token")
    void forgedTokenIsRejected() {
        given().header("Authorization", "Bearer not.a.real.token")
                .when()
                .get("/api/accounts")
                .then()
                .statusCode(401);
    }

    @Test
    @DisplayName("an admin can open an account and read it back")
    void adminOpensAccount() {
        String token = adminToken();
        String accountNumber = openAccount(token, TestCredentials.CUSTOMER_USER, "500.00");

        given().header("Authorization", "Bearer " + token)
                .when()
                .get("/api/accounts/" + accountNumber)
                .then()
                .statusCode(200)
                .body("ownerUsername", equalTo(TestCredentials.CUSTOMER_USER))
                .body("active", equalTo(true));
    }

    @Test
    @DisplayName("a customer cannot open accounts")
    void customerCannotOpenAccounts() {
        given().header("Authorization", "Bearer " + customerToken())
                .contentType(ContentType.JSON)
                .body(
                        Map.of(
                                "ownerUsername", TestCredentials.CUSTOMER_USER,
                                "openingBalance", "10.00",
                                "currency", "USD"))
                .when()
                .post("/api/accounts")
                .then()
                .statusCode(403);
    }

    @Test
    @DisplayName("a transfer moves money and appears in the history")
    void transferMovesMoney() {
        String admin = adminToken();
        String source = openAccount(admin, TestCredentials.CUSTOMER_USER, "300.00");
        String target = openAccount(admin, TestCredentials.CUSTOMER_USER, "50.00");

        given().header("Authorization", "Bearer " + admin)
                .contentType(ContentType.JSON)
                .body(
                        Map.of(
                                "sourceAccount", source,
                                "targetAccount", target,
                                "amount", "125.00",
                                "description", "integration test transfer"))
                .when()
                .post("/api/transfers")
                .then()
                .statusCode(201)
                .body("status", equalTo("COMPLETED"))
                .body("reference", notNullValue());

        given().header("Authorization", "Bearer " + admin)
                .when()
                .get("/api/accounts/" + source)
                .then()
                .statusCode(200)
                .body("balance", equalTo(175.00f));

        given().header("Authorization", "Bearer " + admin)
                .when()
                .get("/api/accounts/" + target)
                .then()
                .statusCode(200)
                .body("balance", equalTo(175.00f));

        given().header("Authorization", "Bearer " + admin)
                .queryParam("accountNumber", source)
                .when()
                .get("/api/transactions")
                .then()
                .statusCode(200)
                .body("content[0].status", equalTo("COMPLETED"));
    }

    @Test
    @DisplayName("an overdrawing transfer is refused and leaves balances untouched")
    void overdraftIsRefused() {
        String admin = adminToken();
        String source = openAccount(admin, TestCredentials.CUSTOMER_USER, "10.00");
        String target = openAccount(admin, TestCredentials.CUSTOMER_USER, "0.00");

        given().header("Authorization", "Bearer " + admin)
                .contentType(ContentType.JSON)
                .body(
                        Map.of(
                                "sourceAccount", source,
                                "targetAccount", target,
                                "amount", "999999.00"))
                .when()
                .post("/api/transfers")
                .then()
                .statusCode(400);

        given().header("Authorization", "Bearer " + admin)
                .when()
                .get("/api/accounts/" + source)
                .then()
                .body("balance", equalTo(10.00f));
    }

    @Test
    @DisplayName("a customer cannot transfer from an account they do not own")
    void customerCannotTransferFromForeignAccount() {
        String admin = adminToken();
        String foreign = openAccount(admin, "someone-else", "100.00");
        String mine = openAccount(admin, TestCredentials.CUSTOMER_USER, "10.00");

        given().header("Authorization", "Bearer " + customerToken())
                .contentType(ContentType.JSON)
                .body(Map.of("sourceAccount", foreign, "targetAccount", mine, "amount", "5.00"))
                .when()
                .post("/api/transfers")
                .then()
                .statusCode(403);
    }

    @Test
    @DisplayName("validation rejects a non-positive transfer amount")
    void validationRejectsBadAmount() {
        String admin = adminToken();
        String source = openAccount(admin, TestCredentials.CUSTOMER_USER, "100.00");
        String target = openAccount(admin, TestCredentials.CUSTOMER_USER, "100.00");

        given().header("Authorization", "Bearer " + admin)
                .contentType(ContentType.JSON)
                .body(Map.of("sourceAccount", source, "targetAccount", target, "amount", "0.00"))
                .when()
                .post("/api/transfers")
                .then()
                .statusCode(400);
    }

    @Test
    @DisplayName("only an admin may read the audit trail")
    void auditTrailIsAdminOnly() {
        given().header("Authorization", "Bearer " + adminToken())
                .when()
                .get("/api/audit")
                .then()
                .statusCode(200);

        given().header("Authorization", "Bearer " + customerToken())
                .when()
                .get("/api/audit")
                .then()
                .statusCode(403);
    }

    @Test
    @DisplayName("a customer sees only their own accounts")
    void customerSeesOwnAccountsOnly() {
        String admin = adminToken();
        openAccount(admin, "unrelated-owner", "42.00");
        openAccount(admin, TestCredentials.CUSTOMER_USER, "42.00");

        given().header("Authorization", "Bearer " + customerToken())
                .when()
                .get("/api/accounts")
                .then()
                .statusCode(200)
                .body(
                        "ownerUsername.unique()",
                        equalTo(java.util.List.of(TestCredentials.CUSTOMER_USER)));
    }

    @Test
    @DisplayName("an unknown account returns 404")
    void unknownAccountIsNotFound() {
        given().header("Authorization", "Bearer " + adminToken())
                .when()
                .get("/api/accounts/ACC999999999999")
                .then()
                .statusCode(404);
    }
}
