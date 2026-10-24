package com.c4soft.resthero.gateway.security;

import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import com.c4_soft.springaddons.security.oidc.starter.properties.SpringAddonsOidcClientProperties;
import com.c4_soft.springaddons.security.oidc.starter.properties.SpringAddonsOidcProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import com.c4soft.resthero.problem.ProblemResponses;
import com.c4soft.resthero.problem.ProblemType;

/**
 * Refuses a logout whose return address the post-logout patterns don't allow: {@code 400}
 * {@link ProblemType#INVALID_REQUEST}, before {@code LogoutFilter}, so that nothing is logged out
 * (neither the gateway's session nor, through RP-Initiated Logout, the authorization server's).
 *
 * <p>
 * spring-addons' logout success handler refuses such an address too, but only after the logout
 * handlers have ended the session, and with an exception (a {@code 500}). This filter picks the
 * address it would follow, the same way ({@code X-POST-LOGOUT-SUCCESS-URI} when present, even
 * blank, otherwise the {@code post_logout_success_uri} parameter; without either, the configured
 * default, checked at startup), and checks it with spring-addons' own method and patterns.
 * </p>
 *
 * <p>
 * The address, which the client chose, isn't logged.
 * </p>
 *
 * @author Jerome Wacongne ch4mp&#64;c4-soft.com
 */
@Slf4j
public class PostLogoutUriFilter extends OncePerRequestFilter {

  private static final RequestMatcher LOGOUT =
      PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/logout");

  private final ProblemResponses problems;

  private final List<Pattern> allowedUriPatterns;

  public PostLogoutUriFilter(ProblemResponses problems,
      SpringAddonsOidcProperties addonsProperties) {
    this.problems = problems;
    this.allowedUriPatterns = addonsProperties.getClient().getPostLogoutAllowedUriPatterns();
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !LOGOUT.matches(request);
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
      FilterChain filterChain) throws ServletException, IOException {
    final var postLogoutUri = requestedPostLogoutUri(request);
    if (postLogoutUri != null && !SpringAddonsOidcClientProperties
        .isAllowedRedirectionUri(postLogoutUri, allowedUriPatterns)) {
      log.debug("Logout refused on {}: return address not allowed", request.getRequestURI());
      problems.write(request, response, ProblemType.INVALID_REQUEST);
      return;
    }
    filterChain.doFilter(request, response);
  }

  /**
   * The header when present (even blank: spring-addons then refuses it), otherwise the parameter,
   * otherwise nothing to check.
   */
  private static @Nullable String requestedPostLogoutUri(HttpServletRequest request) {
    final var header =
        request.getHeader(SpringAddonsOidcClientProperties.POST_LOGOUT_SUCCESS_URI_HEADER);
    if (header != null) {
      return header;
    }
    return request.getParameter(SpringAddonsOidcClientProperties.POST_LOGOUT_SUCCESS_URI_PARAM);
  }
}
