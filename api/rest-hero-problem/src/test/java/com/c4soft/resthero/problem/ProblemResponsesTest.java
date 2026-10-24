package com.c4soft.resthero.problem;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import com.jayway.jsonpath.JsonPath;
import tools.jackson.databind.json.JsonMapper;

class ProblemResponsesTest {

  final ProblemResponses problems = new ProblemResponses(JsonMapper.builder().build());

  @Test
  void givenAType_whenWrite_thenProblemJsonWithItsTypeStatusAndNoPropertiesMember()
      throws Exception {
    final var request = new MockHttpServletRequest("GET", "/gateway/bff/events/uri");
    final var response = new MockHttpServletResponse();

    problems.write(request, response, ProblemType.UNAUTHENTICATED);

    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    final var body = response.getContentAsString();
    assertThat(JsonPath.<String>read(body, "$.type")).isEqualTo("urn:rest-hero:problem:unauthenticated");
    assertThat(JsonPath.<Integer>read(body, "$.status")).isEqualTo(401);
    assertThat(JsonPath.<String>read(body, "$.instance")).isEqualTo("/gateway/bff/events/uri");
    // Spring's ProblemDetail mixin, as for the problems Spring MVC renders: members at the root, no
    // "properties" map; the parameters always there, empty for a parameterless type (the schema
    // requires them)
    assertThat(body).doesNotContain("\"properties\"");
    assertThat(JsonPath.<java.util.Map<String, Object>>read(body, "$.parameters")).isEmpty();
  }

  @Test
  void givenAStatus_whenForStatus_thenItsType() {
    assertThat(ProblemResponses.forStatus(HttpStatus.UNAUTHORIZED).getType())
        .isEqualTo(ProblemType.UNAUTHENTICATED.uri());
    assertThat(ProblemResponses.forStatus(HttpStatus.FORBIDDEN).getType())
        .isEqualTo(ProblemType.ACCESS_DENIED.uri());
    assertThat(ProblemResponses.forStatus(HttpStatus.NOT_FOUND).getType())
        .isEqualTo(ProblemType.NOT_FOUND.uri());
    assertThat(ProblemResponses.forStatus(HttpStatus.SERVICE_UNAVAILABLE).getType())
        .isEqualTo(ProblemType.SERVICE_UNAVAILABLE.uri());
    assertThat(ProblemResponses.forStatus(HttpStatus.BAD_GATEWAY).getType())
        .isEqualTo(ProblemType.SERVER_ERROR.uri());
    assertThat(ProblemResponses.forStatus(HttpStatus.BAD_GATEWAY).getStatus()).isEqualTo(500);
  }

  @Test
  void givenAnotherClientError_whenForStatus_thenInvalidRequestKeepingTheStatus() {
    final var problem = ProblemResponses.forStatus(HttpStatus.METHOD_NOT_ALLOWED);

    assertThat(problem.getType()).isEqualTo(ProblemType.INVALID_REQUEST.uri());
    assertThat(problem.getStatus()).isEqualTo(405);
  }
}
