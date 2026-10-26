package com.c4soft.resthero.problem;

import java.io.IOException;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Answers a request that needs an authenticated user with a {@code 401}
 * {@link ProblemType#UNAUTHENTICATED}: no redirection, no login URL.
 *
 * @author Jerome Wacongne ch4mp&#64;c4-soft.com
 */
public class ProblemAuthenticationEntryPoint implements AuthenticationEntryPoint {

  private final ProblemResponses problems;

  private final @Nullable String wwwAuthenticate;

  /**
   * @param problems the problem writer
   * @param wwwAuthenticate the challenge of a resource server ({@code Bearer}), or {@code null} for
   *        the BFF, whose session cookie is no HTTP authentication scheme
   */
  public ProblemAuthenticationEntryPoint(ProblemResponses problems,
      @Nullable String wwwAuthenticate) {
    this.problems = problems;
    this.wwwAuthenticate = wwwAuthenticate;
  }

  @Override
  public void commence(HttpServletRequest request, HttpServletResponse response,
      AuthenticationException authException) throws IOException {
    if (wwwAuthenticate != null) {
      response.setHeader(HttpHeaders.WWW_AUTHENTICATE, wwwAuthenticate);
    }
    problems.write(request, response, ProblemType.UNAUTHENTICATED);
  }
}
