package com.c4soft.resthero.problem;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.RequestDispatcher;
import tools.jackson.databind.json.JsonMapper;

class ApiErrorControllerTest {

  final ApiErrorController controller =
      new ApiErrorController(new ProblemResponses(JsonMapper.builder().build()));

  final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/error");

  final MockHttpServletResponse response = new MockHttpServletResponse();

  @Test
  void givenAnErrorDispatch_whenError_thenProblemOfItsStatusOnTheOriginalUri() throws Exception {
    request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, 401);
    request.setAttribute(RequestDispatcher.ERROR_REQUEST_URI, "/applications");

    controller.error(request, response);

    assertThat(response.getStatus()).isEqualTo(401);
    final var body = response.getContentAsString();
    assertThat(JsonPath.<String>read(body, "$.type")).isEqualTo("urn:rest-hero:problem:unauthenticated");
    assertThat(JsonPath.<String>read(body, "$.instance")).isEqualTo("/applications");
  }

  @Test
  void givenAFilterException_whenError_thenServerErrorWithoutItsMessage() throws Exception {
    request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, 500);
    request.setAttribute(RequestDispatcher.ERROR_EXCEPTION,
        new IllegalStateException("https://keycloak.internal:8443 refused"));

    controller.error(request, response);

    assertThat(response.getStatus()).isEqualTo(500);
    assertThat(JsonPath.<String>read(response.getContentAsString(), "$.type"))
        .isEqualTo("urn:rest-hero:problem:server-error");
    assertThat(response.getContentAsString()).doesNotContain("keycloak.internal");
  }

  @Test
  void givenANonStandardServerErrorStatus_whenError_thenServerErrorNotNotFound() throws Exception {
    request.setAttribute(RequestDispatcher.ERROR_STATUS_CODE, 599);

    controller.error(request, response);

    assertThat(response.getStatus()).isEqualTo(500);
    assertThat(JsonPath.<String>read(response.getContentAsString(), "$.type"))
        .isEqualTo("urn:rest-hero:problem:server-error");
  }

  @Test
  void givenNoErrorStatus_whenError_thenNotFound() throws Exception {
    controller.error(request, response);

    assertThat(response.getStatus()).isEqualTo(404);
    assertThat(JsonPath.<String>read(response.getContentAsString(), "$.type"))
        .isEqualTo("urn:rest-hero:problem:not-found");
  }
}
