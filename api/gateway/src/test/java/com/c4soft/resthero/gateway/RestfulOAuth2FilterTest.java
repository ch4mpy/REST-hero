package com.c4soft.resthero.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.client.ClientAuthorizationException;
import org.springframework.security.oauth2.client.ClientAuthorizationRequiredException;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.util.ThrowableAnalyzer;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import com.c4soft.resthero.gateway.SpringSecurityPatchConfiguration.RestfulOAuth2Filter;
import com.c4soft.resthero.problem.ProblemResponses;
import tools.jackson.databind.json.JsonMapper;

/**
 * A request whose authorized client can't be refreshed any more (refresh token expired or revoked)
 * gets a {@code 401} {@code unauthenticated} problem, without the authorization request's URI.
 */
class RestfulOAuth2FilterTest {

  @SuppressWarnings("unchecked")
  @Test
  void givenTheClientNeedsAuthorizationAgain_whenFilter_thenUnauthenticatedProblemWithoutLocation()
      throws Exception {
    final var resolver = mock(OAuth2AuthorizationRequestResolver.class);
    when(resolver.resolve(any(HttpServletRequest.class), eq("gateway")))
        .thenReturn(OAuth2AuthorizationRequest.authorizationCode()
            .authorizationUri("https://keycloak/auth").clientId("gateway")
            .authorizationRequestUri("https://keycloak/auth?client_id=gateway").build());
    final var filter = new RestfulOAuth2Filter(resolver, mock(AuthorizationRequestRepository.class),
        mock(RedirectStrategy.class), mock(AuthenticationFailureHandler.class),
        new ThrowableAnalyzer(), () -> new ProblemResponses(JsonMapper.builder().build()));
    final var request = new MockHttpServletRequest("GET", "/gateway/bff/accounts");
    final var response = new MockHttpServletResponse();
    final FilterChain chain = (req, res) -> {
      throw new ClientAuthorizationRequiredException("gateway");
    };

    filter.doFilter(request, response, chain);

    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(response.getHeader(HttpHeaders.LOCATION)).isNull();
    assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    assertThat(JsonPath.<String>read(response.getContentAsString(), "$.type"))
        .isEqualTo("urn:rest-hero:problem:unauthenticated");
    assertThat(response.getContentAsString()).doesNotContain("keycloak");
  }

  /** A refresh the authorization server refused: the plain parent exception. */
  @SuppressWarnings("unchecked")
  @Test
  void givenARefusedRefresh_whenFilter_thenUnauthenticatedProblem() throws Exception {
    final var filter = new RestfulOAuth2Filter(mock(OAuth2AuthorizationRequestResolver.class),
        mock(AuthorizationRequestRepository.class), mock(RedirectStrategy.class),
        mock(AuthenticationFailureHandler.class), new ThrowableAnalyzer(),
        () -> new ProblemResponses(JsonMapper.builder().build()));
    final var response = new MockHttpServletResponse();
    final FilterChain chain = (req, res) -> {
      throw new ClientAuthorizationException(new OAuth2Error("invalid_grant"), "gateway");
    };

    filter.doFilter(new MockHttpServletRequest("GET", "/gateway/bff/accounts"),
        response, chain);

    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(JsonPath.<String>read(response.getContentAsString(), "$.type"))
        .isEqualTo("urn:rest-hero:problem:unauthenticated");
  }
}
