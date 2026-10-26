package com.c4soft.resthero.problem;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.ConversionNotSupportedException;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.mvc.annotation.ResponseStatusExceptionResolver;
import org.springframework.web.servlet.mvc.method.annotation.ExceptionHandlerExceptionResolver;
import org.springframework.web.servlet.mvc.support.DefaultHandlerExceptionResolver;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import com.jayway.jsonpath.JsonPath;
import tools.jackson.databind.json.JsonMapper;

class ProblemHandlerExceptionResolverTest {

  final ProblemResponses problems = new ProblemResponses(JsonMapper.builder().build());

  final ProblemHandlerExceptionResolver resolver = new ProblemHandlerExceptionResolver(problems,
      List.of(ex -> ex instanceof UnsupportedOperationException));

  final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/gateway/unknown");

  final MockHttpServletResponse response = new MockHttpServletResponse();

  private String type() throws Exception {
    return JsonPath.read(response.getContentAsString(), "$.type");
  }

  @Test
  void givenNoResourceFound_whenResolve_thenNotFound() throws Exception {
    final var mav = resolver.resolveException(request, response, null,
        new NoResourceFoundException(HttpMethod.GET, "/gateway/unknown", "unknown"));

    assertThat(mav).isNotNull();
    assertThat(response.getStatus()).isEqualTo(404);
    assertThat(type()).isEqualTo("urn:rest-hero:problem:not-found");
  }

  @Test
  void givenMethodNotSupported_whenResolve_thenInvalidRequestWithItsStatusAndHeaders()
      throws Exception {
    resolver.resolveException(request, response, null,
        new HttpRequestMethodNotSupportedException("POST", Set.of("GET")));

    assertThat(response.getStatus()).isEqualTo(405);
    assertThat(type()).isEqualTo("urn:rest-hero:problem:invalid-request");
    assertThat(response.getHeader(HttpHeaders.ALLOW)).isEqualTo("GET");
  }

  @Test
  void givenAnUnreadableBody_whenResolve_thenInvalidRequest() throws Exception {
    resolver.resolveException(request, response, null, new HttpMessageNotReadableException(
        "JSON parse error", new MockHttpInputMessage(new byte[0])));

    assertThat(response.getStatus()).isEqualTo(400);
    assertThat(type()).isEqualTo("urn:rest-hero:problem:invalid-request");
  }

  @Test
  void givenAnyOtherException_whenResolve_thenServerErrorWithoutItsMessage() throws Exception {
    resolver.resolveException(request, response, null,
        new IllegalStateException("jdbc:postgresql://internal-host/secret"));

    assertThat(response.getStatus()).isEqualTo(500);
    assertThat(type()).isEqualTo("urn:rest-hero:problem:server-error");
    assertThat(response.getContentAsString()).doesNotContain("internal-host");
  }

  @Test
  void givenSecurityExceptions_whenResolve_thenLeftToSpringSecurity() {
    assertThat(resolver.resolveException(request, response, null,
        new AccessDeniedException("denied"))).isNull();
    assertThat(resolver.resolveException(request, response, null,
        new InsufficientAuthenticationException("anonymous"))).isNull();
    assertThat(response.getContentLength()).isZero();
  }

  @Test
  void givenAMissingConverter_whenResolve_thenServerError() throws Exception {
    resolver.resolveException(request, response, null,
        new ConversionNotSupportedException("value", Integer.class, null));

    assertThat(response.getStatus()).isEqualTo(500);
    assertThat(type()).isEqualTo("urn:rest-hero:problem:server-error");
  }

  @Test
  void givenAnExcludedException_whenResolve_thenLeftToTheFilters() {
    assertThat(resolver.resolveException(request, response, null,
        new UnsupportedOperationException("handled by a filter"))).isNull();
    assertThat(response.getContentLength()).isZero();
  }

  @Test
  void givenACommittedResponse_whenResolve_thenLeftAlone() {
    response.setCommitted(true);

    assertThat(resolver.resolveException(request, response, null,
        new IllegalStateException("stream broken"))).isNull();
  }

  @Test
  void givenSpringResolvers_whenExtend_thenInsertedRightBeforeTheDefaultOne() {
    final List<HandlerExceptionResolver> resolvers = new ArrayList<>(
        List.of(new ExceptionHandlerExceptionResolver(), new ResponseStatusExceptionResolver(),
            new DefaultHandlerExceptionResolver()));

    new ProblemWebMvcConfiguration(JsonMapper.builder().build(),
        new DefaultListableBeanFactory().getBeanProvider(ProblemResolutionExclusion.class))
        .extendHandlerExceptionResolvers(resolvers);

    assertThat(resolvers).hasSize(4);
    assertThat(resolvers.get(2)).isInstanceOf(ProblemHandlerExceptionResolver.class);
    assertThat(resolvers.get(3)).isInstanceOf(DefaultHandlerExceptionResolver.class);
  }
}
