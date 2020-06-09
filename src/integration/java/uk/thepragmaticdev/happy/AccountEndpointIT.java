package uk.thepragmaticdev.happy;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.exparity.hamcrest.date.DateMatchers.after;
import static org.exparity.hamcrest.date.DateMatchers.before;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;

import io.restassured.mapper.TypeRef;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Scanner;
import org.flywaydb.test.FlywayTestExecutionListener;
import org.flywaydb.test.annotation.FlywayTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import uk.thepragmaticdev.IntegrationConfig;
import uk.thepragmaticdev.IntegrationData;
import uk.thepragmaticdev.auth.dto.response.AuthDeviceResponse;
import uk.thepragmaticdev.log.security.SecurityLog;

@Import(IntegrationConfig.class)
@TestExecutionListeners({ DependencyInjectionTestExecutionListener.class, FlywayTestExecutionListener.class })
@SpringBootTest(webEnvironment = WebEnvironment.DEFINED_PORT)
class AccountEndpointIT extends IntegrationData {
  // @formatter:off

  /**
   * Called before each integration test to reset database to default state.
   */
  @BeforeEach
  @FlywayTest
  public void initEach() {
  }

  // @endpoint:me

  @Test
  void shouldReturnAuthenticatedAccount() {
    given()
      .headers(headers())
      .header(HttpHeaders.AUTHORIZATION, signin())
    .when()
      .get(ACCOUNTS_ENDPOINT + "me")
    .then()
        .body("id", is(nullValue()))
        .body("stripeCustomerId", is(nullValue()))
        .body("stripeSubscriptionId", is(nullValue()))
        .body("stripeSubscriptionItemId", is(nullValue()))
        .body("username", is("admin@email.com"))
        .body("password", is(nullValue()))
        .body("passwordResetToken", is(nullValue()))
        .body("passwordResetTokenExpire", is(nullValue()))
        .body("fullName", is("Stephen Cathcart"))
        .body("emailSubscriptionEnabled", is(true))
        .body("billingAlertEnabled", is(false))
        .body("createdDate", is("2020-02-25T10:30:44.232Z"))
        .body("roles", is(nullValue()))
        .body("apiKeys", is(nullValue()))
        .statusCode(200);
  }

  // @endpoint:update

  @Test
  void shouldUpdateOnlyMutableAccountFields() {
    var request = accountUpdateRequest();

    given()
      .headers(headers())
      .contentType(JSON)
      .header(HttpHeaders.AUTHORIZATION, signin())
      .body(request)
    .when()
      .put(ACCOUNTS_ENDPOINT + "me")
    .then()
        .body("stripeCustomerId", is(nullValue()))
        .body("stripeSubscriptionId", is(nullValue()))
        .body("stripeSubscriptionItemId", is(nullValue()))
        .body("username", is("admin@email.com"))
        .body("password", is(nullValue()))
        .body("passwordResetToken", is(nullValue()))
        .body("passwordResetTokenExpire", is(nullValue()))
        .body("fullName", is(request.getFullName()))
        .body("emailSubscriptionEnabled", is(request.getEmailSubscriptionEnabled()))
        .body("billingAlertEnabled", is(request.getBillingAlertEnabled()))
        .body("createdDate", is("2020-02-25T10:30:44.232Z"))
        .statusCode(200);
  }

  // @endpoint:billing-logs

  @Test
  void shouldReturnLatestBillingLogs() {
    given()
      .headers(headers())
      .header(HttpHeaders.AUTHORIZATION, signin())
    .when()
      .get(ACCOUNTS_ENDPOINT + "me/billing/logs")
    .then()
        .body("numberOfElements", is(3))
        .body("content", hasSize(3))
        .root("content[0]")
          .body("action", is("billing.paid"))
          .body("amount", is("-£50.00"))
          .body("createdDate", is("2020-02-26T15:40:19.111Z"))
        .root("content[1]")
          .body("action", is("billing.invoice"))
          .body("amount", is("£0.00"))
          .body("createdDate", is("2020-02-25T15:50:19.111Z"))
        .root("content[2]")
          .body("action", is("subscription.created"))
          .body("amount", is("£0.00"))
          .body("createdDate", is("2020-02-24T15:55:19.111Z"))
        .statusCode(200);
  }

  // @endpoint:billing-logs-download

  @Test
  void shouldDownloadBillingLogs() throws IOException {
    given()
      .headers(headers())
      .header(HttpHeaders.AUTHORIZATION, signin())
    .when()
      .get(ACCOUNTS_ENDPOINT + "me/billing/logs/download")
    .then()
        .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, is(HttpHeaders.CONTENT_DISPOSITION))
        .header(HttpHeaders.CONTENT_DISPOSITION, startsWith("attachment; filename="))
        .body(is(csv("data/billing.log.csv")))
        .statusCode(200);
  }

  // @endpoint:security-logs

  @Test
  void shouldReturnLatestSecurityLogs() {
    given()
      .headers(headers())
      .header(HttpHeaders.AUTHORIZATION, signin())
    .when()
      .get(ACCOUNTS_ENDPOINT + "me/security/logs")
    .then()
        .body("numberOfElements", is(4))
        .body("content", hasSize(4))
        .root("content[0]")
          .body("action", is("account.signin"))
          .body("createdDate", is(withinLast(5, ChronoUnit.SECONDS)))
          .spec(validRequestMetadataSpec(0))
        .root("content[1]")
          .body("action", is("account.signin"))
          .body("createdDate", is("2020-02-26T15:40:19.111Z"))
          .spec(validRequestMetadataSpec(0))
        .root("content[2]")
          .body("action", is("account.two_factor_successful_login"))
          .body("createdDate", is("2020-02-25T15:40:19.111Z"))
          .spec(validRequestMetadataSpec(1))
        .root("content[3]")
          .body("action", is("account.created"))
          .body("createdDate", is("2020-02-24T15:40:19.111Z"))
          .spec(validRequestMetadataSpec(2))
        .statusCode(200);
  }

  // @endpoint:security-logs-download

  @Test
  void shouldDownloadSecurityLogs() throws IOException {
    var csv = given()
          .headers(headers())
          .header(HttpHeaders.AUTHORIZATION, signin())
        .when()
          .get(ACCOUNTS_ENDPOINT + "me/security/logs/download")
        .then()
          .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, is(HttpHeaders.CONTENT_DISPOSITION))
          .header(HttpHeaders.CONTENT_DISPOSITION, startsWith("attachment; filename="))
          .statusCode(200)
        .extract().body().asString();
    assertCsvMatch(csv, expectedSecurityLogs());
  }

  // @endpoint:find-all-active-devices

  @Test
  void shouldReturnAllActiveDevices() {
    var devices = given()
        .headers(headers())
        .header(HttpHeaders.AUTHORIZATION, signin())
        .when()
          .get(ACCOUNTS_ENDPOINT + "me/security/devices")
        .then()
          .body("$", hasSize(3))
            .statusCode(200)
            .extract().as(new TypeRef<List<AuthDeviceResponse>>() {});
    devices.forEach(device -> {
      assertThat(new Date(device.getCreatedDate().toInstant().toEpochMilli()), before(new Date()));
      assertThat(new Date(device.getExpirationTime().toInstant().toEpochMilli()), after(new Date()));
      assertThat(device.getRequestMetadata().getGeoMetadata(), is(notNullValue()));
      assertThat(device.getRequestMetadata().getDeviceMetadata(), is(notNullValue()));
    });
  }

  // @endpoint:delete-all-active-devices

  @Test
  void shouldDeleteAllActiveDevices() {
    given()
      .headers(headers())
      .header(HttpHeaders.AUTHORIZATION, signin())
      .when()
        .delete(ACCOUNTS_ENDPOINT + "me/security/devices")
      .then()
          .statusCode(204);
  }

  // @formatter:on

  // @helpers:security-log-assertions

  private List<SecurityLog> expectedSecurityLogs() {
    return List.of(//
        expectedSecurityLog("account.signin", "1900-01-01T00:00:00Z"), //
        expectedSecurityLog("account.signin", "2020-02-26T15:40:19.111Z"), //
        expectedSecurityLog("account.two_factor_successful_login", "2020-02-25T15:40:19.111Z"), //
        expectedSecurityLog("account.created", "2020-02-24T15:40:19.111Z")//
    );
  }

  private SecurityLog expectedSecurityLog(String action, String offsetDateTime) {
    return new SecurityLog(null, null, action, requestMetadata(), OffsetDateTime.parse(offsetDateTime));
  }

  private void assertCsvMatch(String csv, List<SecurityLog> expectedSecurityLogs) {
    try (var scanner = new Scanner(csv)) {
      assertHeaders(scanner.nextLine());
      for (var log : expectedSecurityLogs) {
        var fields = scanner.nextLine().split(",");
        assertSecurityLog(fields, log);
      }
      assertThat(scanner.hasNextLine(), is(false));
    }
  }

  private void assertHeaders(String headers) {
    assertThat(headers,
        is("ACTION,CITYNAME,COUNTRYISOCODE,CREATEDDATE,IP,"
            + "OPERATINGSYSTEMFAMILY,OPERATINGSYSTEMMAJOR,OPERATINGSYSTEMMINOR,"
            + "SUBDIVISIONISOCODE,USERAGENTFAMILY,USERAGENTMAJOR,USERAGENTMINOR"));
  }

  private void assertSecurityLog(String[] fields, SecurityLog log) {
    assertThat(fields[0], is(log.getAction()));
    assertThat(fields[1], is(log.getRequestMetadata().getGeoMetadata().getCityName()));
    assertThat(fields[2], is(log.getRequestMetadata().getGeoMetadata().getCountryIsoCode()));
    // Date should either match expected value or be a date within last five seconds
    // of now.
    assertThat(fields[3], anyOf(is(log.getCreatedDate().toString()), is(withinLast(5, ChronoUnit.SECONDS))));
    assertThat(fields[4], is(log.getRequestMetadata().getIp()));
    assertThat(fields[5], is(log.getRequestMetadata().getDeviceMetadata().getOperatingSystemFamily()));
    assertThat(fields[6], is(log.getRequestMetadata().getDeviceMetadata().getOperatingSystemMajor()));
    assertThat(fields[7], is(log.getRequestMetadata().getDeviceMetadata().getOperatingSystemMinor()));
    assertThat(fields[8], is(log.getRequestMetadata().getGeoMetadata().getSubdivisionIsoCode()));
    assertThat(fields[9], is(log.getRequestMetadata().getDeviceMetadata().getUserAgentFamily()));
    assertThat(fields[10], is(log.getRequestMetadata().getDeviceMetadata().getUserAgentMajor()));
    assertThat(fields[11], is(log.getRequestMetadata().getDeviceMetadata().getUserAgentMinor()));
  }
}