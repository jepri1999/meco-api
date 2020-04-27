package uk.thepragmaticdev.sad;

import static io.restassured.RestAssured.given;
import static io.restassured.http.ContentType.JSON;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import java.util.Arrays;
import java.util.stream.IntStream;
import org.flywaydb.test.FlywayTestExecutionListener;
import org.flywaydb.test.annotation.FlywayTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import uk.thepragmaticdev.IntegrationData;
import uk.thepragmaticdev.exception.code.AccountCode;
import uk.thepragmaticdev.exception.code.ApiKeyCode;
import uk.thepragmaticdev.kms.AccessPolicy;
import uk.thepragmaticdev.kms.ApiKey;

@TestExecutionListeners({ DependencyInjectionTestExecutionListener.class, FlywayTestExecutionListener.class })
@SpringBootTest(webEnvironment = WebEnvironment.DEFINED_PORT)
public class ApiKeyEndpointIT extends IntegrationData {
  // @formatter:off

  /**
   * Called before each integration test to reset database to default state.
   */
  @BeforeEach
  @FlywayTest
  public void initEach() throws Exception {
  }

  // @endpoint:findAll

  @Test
  public void shouldNotReturnAllKeysWithInvalidToken() {
    given()
      .header(HttpHeaders.AUTHORIZATION, INVALID_TOKEN)
    .when()
      .get(API_KEY_ENDPOINT)
    .then()
        .body("status", is("UNAUTHORIZED"))
        .body("message", is(AccountCode.INVALID_EXPIRED_TOKEN.getMessage()))
        .statusCode(401);
  }

  // @endpoint:create

  @Test
  public void shouldNotCreateKeyWhenNameIsTooShort() {
    String shortName = "ab";
    ApiKey key = key();
    key.setName(shortName);

    given()
      .header(HttpHeaders.AUTHORIZATION, signin())
      .contentType(JSON)
      .body(key)
    .when()
      .post(API_KEY_ENDPOINT)
    .then()
        .body("status", is("BAD_REQUEST"))
        .body("message", is("Validation errors"))
        .body("subErrors", hasSize(1))
        .root("subErrors[0]")
          .body("object", is("apiKey"))
          .body("field", is("name"))
          .body("rejectedValue", is(shortName))
          .body("message", is("size must be between 3 and 20"))
        .statusCode(400);
  }

  @Test
  public void shouldNotCreateKeyWhenNameIsTooLong() {
    String longName = "abcdefghijklmnopqrstu";
    ApiKey key = key();
    key.setName(longName);

    given()
      .header(HttpHeaders.AUTHORIZATION, signin())
      .contentType(JSON)
      .body(key)
    .when()
      .post(API_KEY_ENDPOINT)
    .then()
        .body("status", is("BAD_REQUEST"))
        .body("message", is("Validation errors"))
        .body("subErrors", hasSize(1))
        .root("subErrors[0]")
          .body("object", is("apiKey"))
          .body("field", is("name"))
          .body("rejectedValue", is(longName))
          .body("message", is("size must be between 3 and 20"))
        .statusCode(400);
  }

  @Test
  public void shouldNotCreateKeyWithNoScope() {
    ApiKey key = key();
    key.setScope(null);

    given()
      .header(HttpHeaders.AUTHORIZATION, signin())
      .contentType(JSON)
      .body(key)
    .when()
      .post(API_KEY_ENDPOINT)
    .then()
        .body("status", is("BAD_REQUEST"))
        .body("message", is("Validation errors"))
        .body("subErrors", hasSize(1))
        .root("subErrors[0]")
          .body("object", is("apiKey"))
          .body("field", is("scope"))
          .body("rejectedValue", is(nullValue()))
          .body("message", is("must not be null"))
        .statusCode(400);
  }

  @Test
  public void shouldNotCreateKeyWithInvalidIpv4Cidr() {
    ApiKey key = key();
    AccessPolicy dirtyAccessPolicy = dirtyAccessPolicy();
    dirtyAccessPolicy.setRange("invalidRange");
    key.setAccessPolicies(Arrays.asList(dirtyAccessPolicy));

    given()
      .header(HttpHeaders.AUTHORIZATION, signin())
      .contentType(JSON)
      .body(key)
    .when()
      .post(API_KEY_ENDPOINT)
    .then()
        .body("status", is("BAD_REQUEST"))
        .body("message", is("Validation errors"))
        .body("subErrors", hasSize(1))
        .root("subErrors[0]")
          .body("object", is("apiKey"))
          .body("field", is("accessPolicies"))
          .body("rejectedValue", hasSize(1))
          .body("rejectedValue[0].name", is(dirtyAccessPolicy.getName()))
          .body("rejectedValue[0].range", is(dirtyAccessPolicy.getRange()))
          .body("message", is("Must match n.n.n.n/m where n=1-3 decimal digits, m = 1-3 decimal digits in range 1-32."))
        .statusCode(400);
  } 

  @Test
  public void shouldNotCreateKeyWhenAtMaxKeyLimit() {
    assertKeyCount(2);
    IntStream.range(0, 8).forEach(i -> createValidKey());
    assertKeyCount(10);
    given()
      .header(HttpHeaders.AUTHORIZATION, signin())
      .contentType(JSON)
      .body(key())
    .when()
      .post(API_KEY_ENDPOINT)
    .then()
        .body("status", is("FORBIDDEN"))
        .body("message", is(ApiKeyCode.API_KEY_LIMIT.getMessage()))
        .statusCode(403);
  }

  // @endpoint:update

  @Test
  public void shouldNotUpdateKeyWithInvalidToken() {
    given()
      .contentType(JSON)
      .header(HttpHeaders.AUTHORIZATION, INVALID_TOKEN)
      .body(dirtyKey())
    .when()
      .put(API_KEY_ENDPOINT + "1")
      .then()
        .body("status", is("UNAUTHORIZED"))
        .body("message", is(AccountCode.INVALID_EXPIRED_TOKEN.getMessage()))
        .statusCode(401);
  }

  @Test
  public void shouldNotUpdateUnknownKey() {
    given()
      .contentType(JSON)
      .header(HttpHeaders.AUTHORIZATION, signin())
      .body(dirtyKey())
      .log().all() // TODO remove
    .when()
      .put(API_KEY_ENDPOINT + "9999")
      .then()
        .body("status", is("NOT_FOUND"))
        .body("message", is(ApiKeyCode.NOT_FOUND.getMessage()))
        .statusCode(404);
  }

  // @endpoint:delete

  @Test
  public void shouldNotDeleteKeyWithInvalidToken() {
    given()
      .header(HttpHeaders.AUTHORIZATION, INVALID_TOKEN)
    .when()
      .delete(API_KEY_ENDPOINT + "1")
    .then()
        .body("status", is("UNAUTHORIZED"))
        .body("message", is(AccountCode.INVALID_EXPIRED_TOKEN.getMessage()))
        .statusCode(401);
  }

  @Test
  public void shouldNotDeleteUnknownKey() {
    given()
      .header(HttpHeaders.AUTHORIZATION, signin())
    .when()
      .delete(API_KEY_ENDPOINT + "9999")
    .then()
        .body("status", is("NOT_FOUND"))
        .body("message", is(ApiKeyCode.NOT_FOUND.getMessage()))
        .statusCode(404);
  }

  // @endpoint:key-logs

  @Test
  public void shouldNotReturnLatestKeyLogsWithInvalidToken() {
    given()
      .header(HttpHeaders.AUTHORIZATION, INVALID_TOKEN)
    .when()
      .get(API_KEY_ENDPOINT + "1/logs")
    .then()
        .body("status", is("UNAUTHORIZED"))
        .body("message", is(AccountCode.INVALID_EXPIRED_TOKEN.getMessage()))
        .statusCode(401);
  }

  @Test
  public void shouldNotReturnLatestKeyLogsForUnknownKey() {
    given()
      .header(HttpHeaders.AUTHORIZATION, signin())
    .when()
      .get(API_KEY_ENDPOINT + "9999/logs")
    .then()
        .body("status", is("NOT_FOUND"))
        .body("message", is(ApiKeyCode.NOT_FOUND.getMessage()))
        .statusCode(404);
  }

  // @endpoint:key-logs-download

  @Test
  public void shouldNotDownloadKeyLogsWithInvalidToken() {
    given()
      .header(HttpHeaders.AUTHORIZATION, INVALID_TOKEN)
    .when()
      .get(API_KEY_ENDPOINT + "1/logs/download")
    .then()
        .body("status", is("UNAUTHORIZED"))
        .body("message", is(AccountCode.INVALID_EXPIRED_TOKEN.getMessage()))
        .statusCode(401);
  }

  @Test
  public void shouldNotDownloadKeyLogsForUnknownKey() {
    given()
      .header(HttpHeaders.AUTHORIZATION, signin())
    .when()
      .get(API_KEY_ENDPOINT + "9999/logs/download")
    .then()
        .body("status", is("NOT_FOUND"))
        .body("message", is(ApiKeyCode.NOT_FOUND.getMessage()))
        .statusCode(404);
  }

  // @endpoint:count

  @Test
  public void shouldNotReturnKeyCountWithInvalidToken() {
    given()
      .header(HttpHeaders.AUTHORIZATION, INVALID_TOKEN)
    .when()
      .get(API_KEY_ENDPOINT + "count")
      .then()
        .body("status", is("UNAUTHORIZED"))
        .body("message", is(AccountCode.INVALID_EXPIRED_TOKEN.getMessage()))
        .statusCode(401);
  }

  private void createValidKey() {
    given()
      .header(HttpHeaders.AUTHORIZATION, signin())
      .contentType(JSON)
      .body(key())
    .when()
      .post(API_KEY_ENDPOINT)
    .then()
        .statusCode(201);
  }

  private void assertKeyCount(int expectedCount) {
    given()
      .header(HttpHeaders.AUTHORIZATION, signin())
    .when()
      .get(API_KEY_ENDPOINT + "count")
      .then()
        .body(is(Integer.toString(expectedCount)))
        .statusCode(200);
  }

  // @formatter:on
}